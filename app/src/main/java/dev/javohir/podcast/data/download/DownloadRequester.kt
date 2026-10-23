package dev.javohir.podcast.data.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.javohir.podcast.data.db.dao.EpisodeDao
import dev.javohir.podcast.data.db.entities.DownloadStatus
import dev.javohir.podcast.data.settings.SettingsStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRequester @Inject constructor(
    @ApplicationContext private val context: Context,
    private val episodeDao: EpisodeDao,
    private val settingsStore: SettingsStore
) {
    suspend fun enqueue(episodeId: Long, replace: Boolean = false) {
        val wifiOnly = settingsStore.settings.first().wifiOnlyDownloads
        val network = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        episodeDao.setDownloadError(episodeId, DownloadStatus.QUEUED, null)
        val work = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_EPISODE_ID to episodeId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .addTag(episodeWorkTag(episodeId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                DownloadWorker.workName(episodeId),
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                work
            )
    }

    fun cancelWork(episodeId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(DownloadWorker.workName(episodeId))
    }

    companion object {
        const val TAG = "download"
    }
}
