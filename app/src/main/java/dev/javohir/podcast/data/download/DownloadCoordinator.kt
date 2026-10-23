package dev.javohir.podcast.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.javohir.podcast.data.db.dao.EpisodeDao
import dev.javohir.podcast.data.db.entities.DownloadStatus
import dev.javohir.podcast.data.db.entities.Episode
import dev.javohir.podcast.data.db.entities.SourceType
import dev.javohir.podcast.data.settings.SettingsStore
import dev.javohir.podcast.data.youtube.YouTubeDownloadRequester
import dev.javohir.podcast.data.youtube.YouTubeDownloadWorker
import dev.javohir.podcast.playback.PlaybackSession
import dev.javohir.podcast.ui.UserMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val episodeDao: EpisodeDao,
    private val fileStore: DownloadFileStore,
    private val settingsStore: SettingsStore,
    private val rssRequester: DownloadRequester,
    private val youTubeRequester: YouTubeDownloadRequester,
    private val playbackSession: PlaybackSession,
    private val userMessages: UserMessages
) {
    private val locks = ConcurrentHashMap<Long, Mutex>()

    private fun lock(episodeId: Long) = locks.getOrPut(episodeId) { Mutex() }

    suspend fun enqueue(episodeId: Long) = lock(episodeId).withLock {
        val episode = episodeDao.getById(episodeId) ?: return
        if (episode.sourceType == SourceType.LOCAL) return
        if (episode.sourceType != SourceType.YOUTUBE && episode.enclosureUrl.isNullOrBlank()) {
            episodeDao.setDownloadError(episodeId, DownloadStatus.FAILED, DOWNLOAD_NO_URL)
            userMessages.show(DOWNLOAD_NO_URL)
            return
        }
        var status = episode.downloadStatus
        if (status == DownloadStatus.DONE) {
            if (fileStore.exists(episode.downloadedFilePath)) return
            episodeDao.clearDownload(episodeId)
            status = DownloadStatus.NONE
        }
        val alive = workAlive(episode)
        val stillExpected = status == DownloadStatus.QUEUED || status == DownloadStatus.RUNNING
        if (alive && stillExpected) {
            notifyIfWaitingForWifi()
            return
        }
        val replace = alive
        when (episode.sourceType) {
            SourceType.YOUTUBE -> youTubeRequester.enqueue(episodeId, replace)
            else -> rssRequester.enqueue(episodeId, replace)
        }
        notifyIfWaitingForWifi()
    }

    suspend fun cancel(episodeId: Long) = lock(episodeId).withLock {
        cancelAndDeleteFiles(episodeId, clearRecord = true)
    }

    suspend fun deleteFile(episodeId: Long) = lock(episodeId).withLock {
        val episode = episodeDao.getById(episodeId) ?: return
        if (episode.sourceType == SourceType.LOCAL) return
        cancelAndDeleteFiles(episodeId, clearRecord = true, episode = episode)
    }

    suspend fun deleteEpisode(episodeId: Long) = lock(episodeId).withLock {
        val episode = episodeDao.getById(episodeId) ?: return
        if (episode.sourceType != SourceType.LOCAL) {
            cancelAndDeleteFiles(episodeId, clearRecord = false, episode = episode)
        }
        episodeDao.delete(episodeId)
    }

    /** Drops a downloaded file record when the bytes are gone. Playback progress is kept. */
    suspend fun noteMissingFile(episodeId: Long) = lock(episodeId).withLock {
        val episode = episodeDao.getById(episodeId) ?: return
        if (episode.sourceType == SourceType.LOCAL) return
        if (episode.downloadStatus != DownloadStatus.DONE) return
        if (fileStore.exists(episode.downloadedFilePath)) return
        episodeDao.clearDownload(episodeId)
    }

    suspend fun deleteAllListened(): Int {
        val episodes = episodeDao.getListenedRemoteDownloads()
        var count = 0
        for (episode in episodes) {
            deleteFile(episode.id)
            count++
        }
        return count
    }

    suspend fun reconcile() {
        val managed = episodeDao.getManagedDownloads()
        for (episode in managed) {
            lock(episode.id).withLock {
                val fresh = episodeDao.getById(episode.id) ?: return@withLock
                val decision = reconcileDownload(
                    status = fresh.downloadStatus,
                    fileExists = fileStore.exists(fresh.downloadedFilePath),
                    work = if (workAlive(fresh)) WorkLiveness.ALIVE else WorkLiveness.ABSENT
                ) ?: return@withLock
                if (decision.clearPath || decision.status == DownloadStatus.NONE) {
                    fileStore.deleteCache(fresh.id)
                    episodeDao.clearDownload(fresh.id)
                } else {
                    fileStore.deleteCache(fresh.id)
                    episodeDao.setDownloadError(fresh.id, decision.status, decision.error)
                }
            }
        }
        cancelOrphanWork(DownloadRequester.TAG)
        cancelOrphanWork(YouTubeDownloadRequester.TAG)
    }

    private suspend fun cancelAndDeleteFiles(
        episodeId: Long,
        clearRecord: Boolean,
        episode: Episode? = null
    ) {
        val current = episode ?: episodeDao.getById(episodeId)
        rssRequester.cancelWork(episodeId)
        youTubeRequester.cancelWork(episodeId)
        playbackSession.stopIfCurrent(episodeId)
        current?.downloadedFilePath?.let { fileStore.delete(it) }
        fileStore.deleteCache(episodeId)
        if (clearRecord) episodeDao.clearDownload(episodeId)
    }

    private suspend fun workAlive(episode: Episode): Boolean = withContext(Dispatchers.IO) {
        val name = when (episode.sourceType) {
            SourceType.YOUTUBE -> YouTubeDownloadWorker.workName(episode.id)
            else -> DownloadWorker.workName(episode.id)
        }
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()
            .any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.BLOCKED }
    }

    private suspend fun cancelOrphanWork(tag: String) = withContext(Dispatchers.IO) {
        val manager = WorkManager.getInstance(context)
        val infos = manager.getWorkInfosByTag(tag).get()
        for (info in infos) {
            if (info.state.isFinished) continue
            val episodeId = episodeIdFromTags(info.tags) ?: continue
            if (episodeDao.getById(episodeId) == null) manager.cancelWorkById(info.id)
        }
    }

    private suspend fun notifyIfWaitingForWifi() {
        val wifiOnly = settingsStore.settings.first().wifiOnlyDownloads
        if (wifiOnly && !isUnmetered()) userMessages.show("Waiting for Wi-Fi")
    }

    private fun isUnmetered(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
