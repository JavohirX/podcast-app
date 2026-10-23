package dev.javohir.podcast.data.download

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class HttpAudioFetcherTest {

    @Test
    fun largeFileUsesParallelRangesAndKeepsEveryByte() = runBlocking {
        val data = ByteArray(8 * 1024 * 1024) { (it % 251).toByte() }
        val server = LocalAudioServer(data, honorRange = true)
        val dest = File.createTempFile("audio", ".part")
        dest.delete()
        try {
            HttpAudioFetcher(OkHttpClient()).download(
                url = "http://127.0.0.1:${server.port}/episode.mp3",
                dest = dest,
                resume = false
            ) { _, _ -> }
            assertTrue(dest.readBytes().contentEquals(data))
            assertTrue(server.maxInFlight.get() >= 2)
        } finally {
            dest.delete()
            File(dest.parentFile, dest.name + ".parallel").delete()
            server.stop()
        }
    }

    @Test
    fun serverThatIgnoresRangesStillSavesTheFile() = runBlocking {
        val data = ByteArray(64 * 1024) { (it % 251).toByte() }
        val server = LocalAudioServer(data, honorRange = false)
        val dest = File.createTempFile("audio", ".part")
        dest.delete()
        try {
            HttpAudioFetcher(OkHttpClient()).download(
                url = "http://127.0.0.1:${server.port}/episode.mp3",
                dest = dest,
                resume = true
            ) { _, _ -> }
            assertTrue(dest.readBytes().contentEquals(data))
        } finally {
            dest.delete()
            server.stop()
        }
    }

    @Test
    fun fourConnectionsFallBackAfterThreeRefusals() = runBlocking {
        val data = ByteArray(8 * 1024 * 1024) { 7 }
        val server = FaultServer(data, failWideRanges = true)
        val dest = File.createTempFile("audio", ".part")
        dest.delete()
        try {
            HttpAudioFetcher(OkHttpClient()).download(
                url = "http://127.0.0.1:${server.port}/episode.mp3",
                dest = dest,
                resume = false
            ) { _, _ -> }
            assertTrue(dest.readBytes().contentEquals(data))
            assertTrue(server.wideRanges.get() >= 8)
            assertEquals(1, server.fullGets.get())
        } finally {
            dest.delete()
            server.stop()
        }
    }

    @Test
    fun networkFailureDoesNotFallBackToOneConnection() = runBlocking {
        val data = ByteArray(8 * 1024 * 1024) { 7 }
        val server = FaultServer(data, failWideRanges = false)
        val dest = File.createTempFile("audio", ".part")
        dest.delete()
        try {
            HttpAudioFetcher(OkHttpClient()).download(
                url = "http://127.0.0.1:${server.port}/episode.mp3",
                dest = dest,
                resume = false
            ) { _, _ -> }
            throw AssertionError("download should have failed")
        } catch (e: DownloadStopped) {
            assertEquals(503, e.httpCode)
            assertEquals(0, server.fullGets.get())
        } finally {
            dest.delete()
            server.stop()
        }
    }

    @Test
    fun contentRangeTotalIsParsed() {
        assertEquals(249955445L, HttpAudioFetcher.totalFromContentRange("bytes 0-0/249955445"))
        assertEquals(null, HttpAudioFetcher.totalFromContentRange("bytes 0-0/*"))
    }
}

private class LocalAudioServer(
    private val data: ByteArray,
    private val honorRange: Boolean
) {
    private val socket = ServerSocket(0)
    val port: Int = socket.localPort
    val maxInFlight = AtomicInteger(0)
    private val inFlight = AtomicInteger(0)
    private val running = AtomicBoolean(true)
    private val pool = Executors.newFixedThreadPool(8)
    private val acceptThread = Thread {
        while (running.get()) {
            val client = try {
                socket.accept()
            } catch (_: Exception) {
                break
            }
            pool.execute { handle(client) }
        }
    }.also { it.start() }

    fun stop() {
        running.set(false)
        socket.close()
        pool.shutdownNow()
        acceptThread.join(1000)
    }

    private fun handle(client: Socket) {
        val now = inFlight.incrementAndGet()
        maxInFlight.updateAndGet { current -> maxOf(current, now) }
        client.use { sock ->
            val input = sock.getInputStream()
            val header = readHeader(input)
            val range = header.lineSequence()
                .firstOrNull { it.startsWith("Range:", ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()
            val output = sock.getOutputStream()
            if (honorRange && range != null && range.startsWith("bytes=")) {
                val spec = range.removePrefix("bytes=")
                val start = spec.substringBefore("-").toInt()
                val end = spec.substringAfter("-").toIntOrNull() ?: (data.size - 1)
                val slice = data.copyOfRange(start, end + 1)
                val body = buildString {
                    append("HTTP/1.1 206 Partial Content\r\n")
                    append("Content-Type: audio/mpeg\r\n")
                    append("Accept-Ranges: bytes\r\n")
                    append("Content-Range: bytes $start-$end/${data.size}\r\n")
                    append("Content-Length: ${slice.size}\r\n")
                    append("Connection: close\r\n\r\n")
                }.toByteArray()
                output.write(body)
                output.write(slice)
            } else {
                val head = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: audio/mpeg\r\n")
                    append("Content-Length: ${data.size}\r\n")
                    append("Connection: close\r\n\r\n")
                }.toByteArray()
                output.write(head)
                output.write(data)
            }
            output.flush()
        }
        inFlight.decrementAndGet()
    }

    private fun readHeader(input: java.io.InputStream): String {
        val bytes = ArrayList<Byte>()
        while (true) {
            val value = input.read()
            if (value == -1) break
            bytes.add(value.toByte())
            val n = bytes.size
            if (n >= 4 &&
                bytes[n - 4] == '\r'.code.toByte() &&
                bytes[n - 3] == '\n'.code.toByte() &&
                bytes[n - 2] == '\r'.code.toByte() &&
                bytes[n - 1] == '\n'.code.toByte()
            ) {
                break
            }
        }
        return bytes.toByteArray().toString(Charsets.US_ASCII)
    }
}

/**
 * [failWideRanges] answers a one-byte range and a normal download, and rejects every wider range.
 * Otherwise the first one-byte range succeeds and every later request is 503.
 */
private class FaultServer(
    private val data: ByteArray,
    private val failWideRanges: Boolean
) {
    private val socket = ServerSocket(0)
    val port: Int = socket.localPort
    val wideRanges = AtomicInteger(0)
    val fullGets = AtomicInteger(0)
    private val probes = AtomicInteger(0)
    private val running = AtomicBoolean(true)
    private val pool = Executors.newFixedThreadPool(8)
    private val acceptThread = Thread {
        while (running.get()) {
            val client = try {
                socket.accept()
            } catch (_: Exception) {
                break
            }
            pool.execute { handle(client) }
        }
    }.also { it.start() }

    fun stop() {
        running.set(false)
        socket.close()
        pool.shutdownNow()
        acceptThread.join(1000)
    }

    private fun handle(client: Socket) {
        client.use { sock ->
            val header = readHeader(sock.getInputStream())
            val range = header.lineSequence()
                .firstOrNull { it.startsWith("Range:", ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()
            val output = sock.getOutputStream()
            val wide = range != null && range != "bytes=0-0"
            if (wide) wideRanges.incrementAndGet()
            val status = when {
                failWideRanges && wide -> 403
                !failWideRanges && probes.incrementAndGet() > 1 -> 503
                range == null -> 200
                else -> 206
            }
            if (status == 200) {
                fullGets.incrementAndGet()
                val head = "HTTP/1.1 200 OK\r\nContent-Type: audio/mpeg\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n"
                output.write(head.toByteArray())
                output.write(data)
            } else if (status == 206) {
                val head = "HTTP/1.1 206 Partial Content\r\nContent-Type: audio/mpeg\r\nContent-Range: bytes 0-0/${data.size}\r\nContent-Length: 1\r\nConnection: close\r\n\r\n"
                output.write(head.toByteArray())
                output.write(byteArrayOf(data[0]))
            } else {
                val head = "HTTP/1.1 $status NO\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                output.write(head.toByteArray())
            }
            output.flush()
        }
    }

    private fun readHeader(input: java.io.InputStream): String {
        val bytes = ArrayList<Byte>()
        while (true) {
            val value = input.read()
            if (value == -1) break
            bytes.add(value.toByte())
            val n = bytes.size
            if (n >= 4 && bytes[n - 4] == 13.toByte() && bytes[n - 3] == 10.toByte() &&
                bytes[n - 2] == 13.toByte() && bytes[n - 1] == 10.toByte()
            ) break
        }
        return bytes.toByteArray().toString(Charsets.US_ASCII)
    }
}
