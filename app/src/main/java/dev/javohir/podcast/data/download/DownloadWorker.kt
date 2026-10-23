package dev.javohir.podcast.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.javohir.podcast.R
import dev.javohir.podcast.data.db.dao.EpisodeDao
import dev.javohir.podcast.data.db.entities.DownloadStatus
import dev.javohir.podcast.ui.UserMessages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val episodeDao: EpisodeDao,
    private val fetcher: HttpAudioFetcher,
    private val publisher: AudioFilePublisher,
    private val fileStore: DownloadFileStore,
    private val userMessages: UserMessages
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val episodeId = inputData.getLong(KEY_EPISODE_ID, -1L)
        return createForegroundInfo("Downloading…", episodeId)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val episodeId = inputData.getLong(KEY_EPISODE_ID, -1L)
        if (episodeId < 0) return@withContext Result.failure()

        val episode = episodeDao.getById(episodeId) ?: return@withContext Result.failure()
        val url = episode.enclosureUrl
        if (url.isNullOrBlank()) {
            episodeDao.setDownloadErrorIfActive(episodeId, DownloadStatus.FAILED, DOWNLOAD_NO_URL)
            return@withContext Result.failure()
        }
        if (episodeDao.markRunningIfActive(episodeId) == 0) return@withContext Result.success()

        tryForeground(applicationContext.getString(R.string.downloading_episode, episode.title), episodeId)

        val cache = fileStore.cacheFile(episodeId)
        val ext = extensionFromUrl(url)
        return@withContext try {
            fetcher.download(
                url = url,
                dest = cache,
                resume = true,
                headers = mapOf("User-Agent" to USER_AGENT)
            ) { written, total ->
                setProgress(workDataOf(PROGRESS_BYTES to written, PROGRESS_TOTAL to total))
            }
            val published = publisher.publish(cache, "$episodeId.$ext", mimeForExtension(ext))
                ?: throw DownloadStopped(null, "Could not save the audio file")
            if (!canCommitDownload(episodeDao.getById(episodeId)?.downloadStatus)) {
                fileStore.delete(published)
                cache.delete()
                return@withContext Result.success()
            }
            if (episodeDao.setDownloadDoneIfActive(episodeId, published) == 0) {
                fileStore.delete(published)
            }
            cache.delete()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val stopped = e as? DownloadStopped
            val decision = statusWriteForOutcome(
                cancelled = false,
                httpCode = stopped?.httpCode,
                message = stopped?.message ?: e.message,
                attempt = runAttemptCount
            )
            if (decision == null) return@withContext Result.success()
            if (decision.kind == FailureKind.FAIL) cache.delete()
            val wrote = episodeDao.setDownloadErrorIfActive(
                episodeId,
                if (decision.kind == FailureKind.RETRY) DownloadStatus.QUEUED else DownloadStatus.FAILED,
                decision.message
            )
            if (wrote == 0) {
                cache.delete()
                return@withContext Result.success()
            }
            if (decision.kind == FailureKind.FAIL) userMessages.show(decision.message)
            if (decision.kind == FailureKind.RETRY) Result.retry() else Result.failure()
        }
    }

    private suspend fun tryForeground(title: String, episodeId: Long) {
        try {
            setForeground(createForegroundInfo(title, episodeId))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The transfer continues when a foreground notification is not allowed.
        }
    }

    private fun createForegroundInfo(content: String, episodeId: Long): ForegroundInfo {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.download_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
        val notificationId = ((episodeId % 100_000) + 1_000).toInt()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    companion object {
        const val KEY_EPISODE_ID = "episode_id"
        const val PROGRESS_BYTES = "bytes_downloaded"
        const val PROGRESS_TOTAL = "bytes_total"
        const val CHANNEL_ID = "downloads"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

        fun workName(episodeId: Long) = "download_$episodeId"
    }
}
