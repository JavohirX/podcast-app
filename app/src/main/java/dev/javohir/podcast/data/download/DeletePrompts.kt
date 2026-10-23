package dev.javohir.podcast.data.download

import dev.javohir.podcast.data.db.entities.DownloadStatus
import dev.javohir.podcast.data.db.entities.SourceType

enum class DeleteTarget(val menuLabel: String) {
    DOWNLOAD("Delete download"),
    EPISODE("Delete episode")
}

data class DeletePrompt(
    val title: String,
    val body: String,
    val confirmLabel: String
)

/** Local folder audio is the user's own file, so deleting that episode leaves the file in place. */
fun deletePrompt(
    target: DeleteTarget,
    sourceType: SourceType,
    status: DownloadStatus,
    downloadedFilePath: String?
): DeletePrompt {
    if (target == DeleteTarget.DOWNLOAD) {
        return DeletePrompt(
            title = "Delete download?",
            body = "Only the downloaded audio is removed. The episode stays, and listening progress stays.",
            confirmLabel = "Delete download"
        )
    }
    if (sourceType == SourceType.LOCAL) {
        return DeletePrompt(
            title = "Delete episode?",
            body = "The episode will be removed from the app. The audio file in your folder stays.",
            confirmLabel = "Delete episode"
        )
    }
    val hasFile = status == DownloadStatus.DONE || !downloadedFilePath.isNullOrBlank()
    val savingAudio = status == DownloadStatus.QUEUED ||
        status == DownloadStatus.RUNNING ||
        status == DownloadStatus.FAILED
    return when {
        hasFile -> DeletePrompt(
            title = "Delete episode?",
            body = "The episode will be removed, and its downloaded audio file will be deleted too.",
            confirmLabel = "Delete episode and file"
        )
        savingAudio -> DeletePrompt(
            title = "Delete episode?",
            body = "The episode will be removed. The download will stop, and any audio already saved will be deleted.",
            confirmLabel = "Delete episode and file"
        )
        else -> DeletePrompt(
            title = "Delete episode?",
            body = "The episode and its listening progress will be removed.",
            confirmLabel = "Delete episode"
        )
    }
}
