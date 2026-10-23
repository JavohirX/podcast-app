package dev.javohir.podcast.data.download

import dev.javohir.podcast.data.db.entities.DownloadStatus

enum class WorkLiveness { ALIVE, ABSENT }

enum class FileDeleteResult { Deleted, AlreadyGone, Failed }

enum class FailureKind { RETRY, FAIL }

/**
 * Why a four-connection attempt failed. Decided from a follow-up request that uses one connection,
 * not from the parallel error alone.
 * NETWORK: that single request could not be completed either, so one connection would not help.
 * PARALLEL: the single request was served, so the batch is the part that failed.
 * FATAL: the server refused the file itself. One connection would be refused too.
 */
enum class TransferFault { NETWORK, PARALLEL, FATAL }

data class ReconcileDecision(
    val status: DownloadStatus,
    val error: String? = null,
    val clearPath: Boolean = false
)

data class FailureDecision(val kind: FailureKind, val message: String)

const val DOWNLOAD_INTERRUPTED = "Download was interrupted"
const val DOWNLOAD_HTML_BODY = "Server did not return audio"
const val DOWNLOAD_EMPTY = "Download was empty"
const val DOWNLOAD_NO_URL = "No audio URL"
const val DOWNLOAD_DISK_FULL = "Not enough storage"

fun reconcileDownload(
    status: DownloadStatus,
    fileExists: Boolean,
    work: WorkLiveness
): ReconcileDecision? = when (status) {
    DownloadStatus.DONE ->
        if (fileExists) null else ReconcileDecision(DownloadStatus.NONE, clearPath = true)
    DownloadStatus.QUEUED, DownloadStatus.RUNNING ->
        if (work == WorkLiveness.ALIVE) null
        else ReconcileDecision(DownloadStatus.FAILED, error = DOWNLOAD_INTERRUPTED)
    else -> null
}

/** A finished download may be recorded only while the user still expects it. */
fun canCommitDownload(status: DownloadStatus?): Boolean =
    status == DownloadStatus.QUEUED || status == DownloadStatus.RUNNING

fun interpretFileDelete(existed: Boolean, deleteSucceeded: Boolean): FileDeleteResult = when {
    !existed -> FileDeleteResult.AlreadyGone
    deleteSucceeded -> FileDeleteResult.Deleted
    else -> FileDeleteResult.Failed
}

fun isSuccessfulDelete(result: FileDeleteResult): Boolean =
    result == FileDeleteResult.Deleted || result == FileDeleteResult.AlreadyGone

/**
 * Cancellation must not write a status. The cancel path already set NONE,
 * and a late FAILED or QUEUED write is what brings a cancelled download back.
 */
fun statusWriteForOutcome(
    cancelled: Boolean,
    httpCode: Int?,
    message: String?,
    attempt: Int,
    maxAttempts: Int = 3
): FailureDecision? {
    if (cancelled) return null
    return classifyDownloadFailure(httpCode, message, attempt, maxAttempts)
}

fun classifyDownloadFailure(
    httpCode: Int?,
    message: String?,
    attempt: Int,
    maxAttempts: Int = 3
): FailureDecision {
    if (isDiskFull(message)) return FailureDecision(FailureKind.FAIL, DOWNLOAD_DISK_FULL)
    if (message == DOWNLOAD_HTML_BODY || message == DOWNLOAD_EMPTY || message == DOWNLOAD_NO_URL) {
        return FailureDecision(FailureKind.FAIL, message)
    }
    val text = when (httpCode) {
        404 -> "Audio file was not found"
        403, 401 -> "Download was not allowed"
        410 -> "Audio file is gone"
        else -> message?.lineSequence()?.firstOrNull()?.trim()?.take(180)?.ifBlank { null }
            ?: "Download failed"
    }
    val retryable = when (httpCode) {
        null -> true
        408, 429 -> true
        in 500..599 -> true
        else -> false
    }
    if (retryable && attempt < maxAttempts) return FailureDecision(FailureKind.RETRY, text)
    return FailureDecision(FailureKind.FAIL, text)
}

private val PERMANENT_HTTP = setOf(401, 403, 404, 410)

fun classifyTransferFault(controlReachedServer: Boolean, controlHttpCode: Int?): TransferFault {
    if (!controlReachedServer) return TransferFault.NETWORK
    if (controlHttpCode in PERMANENT_HTTP) return TransferFault.FATAL
    if (controlHttpCode == 206 || controlHttpCode == 200) return TransferFault.PARALLEL
    return TransferFault.NETWORK
}

fun isDiskFull(message: String?): Boolean =
    message?.contains("ENOSPC", ignoreCase = true) == true ||
        message?.contains("No space", ignoreCase = true) == true

fun episodeWorkTag(episodeId: Long): String = "episode_$episodeId"

fun episodeIdFromTags(tags: Set<String>): Long? =
    tags.firstNotNullOfOrNull { tag ->
        if (tag.startsWith("episode_")) tag.removePrefix("episode_").toLongOrNull() else null
    }

fun extensionFromUrl(url: String): String {
    val path = url.substringBefore('?').substringBefore('#')
    val raw = path.substringAfterLast('.', missingDelimiterValue = "")
    val clean = raw.filter { it.isLetterOrDigit() }.lowercase().take(4)
    return if (clean.length in 2..4) clean else "mp3"
}

fun mimeForExtension(ext: String): String = when (ext.lowercase()) {
    "mp3" -> "audio/mpeg"
    "m4a", "m4b", "mp4", "aac" -> "audio/mp4"
    "ogg", "oga" -> "audio/ogg"
    "opus" -> "audio/opus"
    "flac" -> "audio/flac"
    "wav" -> "audio/wav"
    else -> "audio/mpeg"
}
