package dev.javohir.podcast.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.javohir.podcast.data.db.entities.DownloadStatus
import dev.javohir.podcast.data.db.entities.SourceType
import dev.javohir.podcast.data.download.DownloadWorker
import dev.javohir.podcast.data.youtube.YouTubeDownloadWorker

data class DownloadProgress(
    val fraction: Float? = null,
    val waitingForWifi: Boolean = false
)

@Composable
fun rememberDownloadProgress(
    episodeId: Long,
    status: DownloadStatus,
    sourceType: SourceType = SourceType.RSS
): DownloadProgress {
    if (status != DownloadStatus.RUNNING && status != DownloadStatus.QUEUED) return DownloadProgress()
    val context = LocalContext.current
    val workManager = remember { WorkManager.getInstance(context) }
    val workName = if (sourceType == SourceType.YOUTUBE) {
        YouTubeDownloadWorker.workName(episodeId)
    } else {
        DownloadWorker.workName(episodeId)
    }
    val workInfos by workManager
        .getWorkInfosForUniqueWorkFlow(workName)
        .collectAsState(initial = emptyList())
    val running = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
    val fraction = running?.progress?.let { data ->
        val bytes = data.getLong(DownloadWorker.PROGRESS_BYTES, 0L)
        val total = data.getLong(DownloadWorker.PROGRESS_TOTAL, -1L)
        if (total > 0L) (bytes.toFloat() / total).coerceIn(0f, 1f) else null
    }
    val waitingForWifi = status == DownloadStatus.QUEUED && workInfos.any { info ->
        (info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.BLOCKED) &&
            info.constraints.requiredNetworkType == NetworkType.UNMETERED
    }
    return DownloadProgress(fraction = fraction, waitingForWifi = waitingForWifi)
}

@Composable
fun rememberDownloadFraction(
    episodeId: Long,
    status: DownloadStatus,
    sourceType: SourceType = SourceType.RSS
): Float? = rememberDownloadProgress(episodeId, status, sourceType).fraction
