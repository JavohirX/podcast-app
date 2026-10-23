package dev.javohir.podcast.data.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

class DownloadStopped(val httpCode: Int?, message: String) : Exception(message)

private class RangeNotSupported(val httpCode: Int) : Exception("HTTP $httpCode")

@Singleton
class HttpAudioFetcher @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    /**
     * Writes [url] to [dest]. When [resume] is true and [dest] already has bytes, sends a Range request.
     * Files of at least 8MB are fetched in four parallel ranges. One connection to the usual podcast
     * CDNs leaves most of the link idle.
     * [onProgress] receives bytes written and the total, or -1 when the total is unknown.
     */
    suspend fun download(
        url: String,
        dest: File,
        resume: Boolean,
        headers: Map<String, String> = emptyMap(),
        onProgress: suspend (written: Long, total: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val marker = parallelMarker(dest)
        if (marker.exists()) {
            dest.delete()
            marker.delete()
        }
        val existing = if (resume && dest.exists()) dest.length() else 0L
        if (!resume && dest.exists()) dest.delete()
        if (existing == 0L) {
            val probe = probeRange(url, headers)
            if (probe != null && probe.total >= MIN_PARALLEL_BYTES) {
                downloadParallelOrFallback(probe, dest, marker, headers, onProgress)
                return@withContext
            }
        }
        downloadSequential(url, dest, existing, headers, onProgress)
    }

    /**
     * Four connections stay the default. A failure counts toward the single-connection fallback
     * only when a following one-connection range is served. If that check fails too, the network
     * or the file is at fault and a single full download is not attempted.
     */
    private suspend fun downloadParallelOrFallback(
        probe: Probe,
        dest: File,
        marker: File,
        headers: Map<String, String>,
        onProgress: suspend (written: Long, total: Long) -> Unit
    ) {
        var strikes = 0
        while (strikes < PARALLEL_ATTEMPTS) {
            coroutineContext.ensureActive()
            try {
                downloadInParts(probe, dest, marker, headers, onProgress)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.message == DOWNLOAD_HTML_BODY || isDiskFull(e.message)) throw e
                coroutineContext.ensureActive()
                val control = controlRange(probe.finalUrl, headers)
                when (classifyTransferFault(control.reachedServer, control.httpCode)) {
                    TransferFault.NETWORK -> throw preserveHttpCode(e)
                    TransferFault.FATAL -> throw DownloadStopped(control.httpCode, "HTTP ${control.httpCode}")
                    TransferFault.PARALLEL -> {
                        strikes++
                        if (strikes < PARALLEL_ATTEMPTS) delay(150L * strikes)
                    }
                }
            }
        }
        downloadSequential(probe.finalUrl, dest, 0L, headers, onProgress)
    }

    private fun probeRange(url: String, headers: Map<String, String>): Probe? {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
            header("Range", "bytes=0-0")
        }.build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 200) return null
            if (response.code != 206) throw DownloadStopped(response.code, "HTTP ${response.code}")
            val total = totalFromContentRange(response.header("Content-Range")) ?: return null
            rejectHtml(response)
            return Probe(finalUrl = response.request.url.toString(), total = total)
        }
    }

    private fun controlRange(url: String, headers: Map<String, String>): Control {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
            header("Range", "bytes=0-0")
        }.build()
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                Control(reachedServer = true, httpCode = response.code)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Control(reachedServer = false, httpCode = null)
        }
    }

    private suspend fun downloadInParts(
        probe: Probe,
        dest: File,
        marker: File,
        headers: Map<String, String>,
        onProgress: suspend (written: Long, total: Long) -> Unit
    ) {
        marker.createNewFile()
        val written = AtomicLong(0L)
        val lastReport = AtomicLong(0L)
        val gate = Mutex()
        var complete = false
        try {
            val partSize = probe.total / PARTS
            coroutineScope {
                (0 until PARTS).map { index ->
                    async {
                        val start = index * partSize
                        val end = if (index == PARTS - 1) probe.total - 1 else start + partSize - 1
                        downloadRange(probe.finalUrl, dest, start, end, headers) { count ->
                            report(written.addAndGet(count.toLong()), probe.total, force = false, lastReport, gate, onProgress)
                        }
                    }
                }.awaitAll()
            }
            if (dest.length() < probe.total) throw DownloadStopped(null, DOWNLOAD_EMPTY)
            report(probe.total, probe.total, force = true, lastReport, gate, onProgress)
            complete = true
        } finally {
            if (!complete) dest.delete()
            marker.delete()
        }
    }

    private suspend fun downloadRange(
        url: String,
        dest: File,
        start: Long,
        end: Long,
        headers: Map<String, String>,
        onChunk: suspend (Int) -> Unit
    ) {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
            header("Range", "bytes=$start-$end")
        }.build()
        val call = okHttpClient.newCall(request)
        try {
            call.execute().use { response ->
                if (response.code != 206) throw RangeNotSupported(response.code)
                rejectHtml(response)
                val expect = end - start + 1
                val body = response.body ?: throw DownloadStopped(response.code, DOWNLOAD_EMPTY)
                body.byteStream().use { input ->
                    writeAt(dest, start, input, expect, onChunk)
                }
            }
        } finally {
            call.cancel()
        }
    }

    private suspend fun writeAt(
        dest: File,
        start: Long,
        input: InputStream,
        expect: Long,
        onChunk: suspend (Int) -> Unit
    ) {
        val channel = FileChannel.open(dest.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        try {
            val buffer = ByteArray(256 * 1024)
            var offset = start
            var got = 0L
            while (got < expect) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read == -1) break
                val bytes = ByteBuffer.wrap(buffer, 0, read)
                var written = 0
                while (bytes.hasRemaining()) {
                    val n = channel.write(bytes, offset + written)
                    if (n < 0) break
                    written += n
                }
                offset += read
                got += read
                onChunk(read)
            }
            if (got != expect) throw DownloadStopped(null, DOWNLOAD_EMPTY)
        } finally {
            channel.close()
        }
    }

    private suspend fun downloadSequential(
        url: String,
        dest: File,
        existingStart: Long,
        headers: Map<String, String>,
        onProgress: suspend (written: Long, total: Long) -> Unit
    ) {
        var existing = existingStart
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
            if (existing > 0L) header("Range", "bytes=$existing-")
        }.build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 416 && existing > 0L) {
                dest.delete()
                throw DownloadStopped(416, "Could not resume download")
            }
            if (response.code == 200 && existing > 0L) {
                existing = 0L
                dest.delete()
            }
            if (!response.isSuccessful && response.code != 206) {
                throw DownloadStopped(response.code, "HTTP ${response.code}")
            }
            val body = response.body ?: throw DownloadStopped(response.code, DOWNLOAD_EMPTY)
            rejectHtml(response)
            val append = response.code == 206 && existing > 0L
            val bodyLength = body.contentLength()
            val total = when {
                append && bodyLength > 0L -> existing + bodyLength
                bodyLength > 0L -> bodyLength
                else -> -1L
            }
            var written = if (append) existing else 0L
            val lastReport = AtomicLong(0L)
            val gate = Mutex()
            val out = FileOutputStream(dest, append)
            try {
                val input = body.byteStream()
                try {
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        written += read
                        report(written, total, force = false, lastReport, gate, onProgress)
                    }
                } finally {
                    input.close()
                }
            } finally {
                out.close()
            }
            if (written <= 0L) {
                dest.delete()
                throw DownloadStopped(response.code, DOWNLOAD_EMPTY)
            }
            report(written, total, force = true, lastReport, gate, onProgress)
        }
    }

    private suspend fun report(
        written: Long,
        total: Long,
        force: Boolean,
        lastReport: AtomicLong,
        gate: Mutex,
        onProgress: suspend (written: Long, total: Long) -> Unit
    ) {
        val now = System.nanoTime()
        if (!force) {
            val prev = lastReport.get()
            if (now - prev < PROGRESS_INTERVAL_NANOS) return
            if (!lastReport.compareAndSet(prev, now)) return
        }
        gate.withLock {
            coroutineContext.ensureActive()
            onProgress(written, total)
        }
    }

    private fun rejectHtml(response: Response) {
        val contentType = response.body?.contentType()?.toString().orEmpty()
        if (contentType.startsWith("text/")) {
            throw DownloadStopped(response.code, DOWNLOAD_HTML_BODY)
        }
    }

    private fun preserveHttpCode(error: Exception): Exception {
        if (error is DownloadStopped) return error
        val ranged = error as? RangeNotSupported ?: return error
        return DownloadStopped(ranged.httpCode, "HTTP ${ranged.httpCode}")
    }

    private fun parallelMarker(dest: File): File = File(dest.parentFile, dest.name + ".parallel")

    private data class Probe(val finalUrl: String, val total: Long)

    private data class Control(val reachedServer: Boolean, val httpCode: Int?)

    companion object {
        private const val PARTS = 4
        private const val PARALLEL_ATTEMPTS = 3
        private const val MIN_PARALLEL_BYTES = 8L * 1024 * 1024
        private const val PROGRESS_INTERVAL_NANOS = 500_000_000L

        fun totalFromContentRange(header: String?): Long? {
            val total = header?.substringAfterLast("/", missingDelimiterValue = "") ?: return null
            return total.toLongOrNull()?.takeIf { it > 0L }
        }
    }
}
