package dev.javohir.podcast.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.javohir.podcast.data.db.dao.BookmarkDao
import dev.javohir.podcast.data.db.dao.EpisodeDao
import dev.javohir.podcast.data.db.dao.FeedDao
import dev.javohir.podcast.data.db.dao.FolderDao
import dev.javohir.podcast.data.db.entities.*
import dev.javohir.podcast.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long,
    val feeds: List<FeedBackup>,
    val folders: List<FolderBackup>,
    val episodes: List<EpisodeBackup>,
    val bookmarks: List<BookmarkBackup>,
    val settings: SettingsBackup
)

@Serializable
data class FeedBackup(val url: String, val title: String, val imageUrl: String?)

@Serializable
data class FolderBackup(val treeUri: String, val displayName: String)

@Serializable
data class EpisodeBackup(
    val sourceType: String,
    val feedUrl: String?,
    val guid: String?,
    val treeUri: String?,
    val relativePath: String?,
    val title: String,
    val positionMs: Long,
    val percentListened: Float,
    val isListened: Boolean,
    val isFavourite: Boolean,
    val playCount: Int,
    val lastPlayedAt: Long?
)

@Serializable
data class BookmarkBackup(
    val episodeGuidOrPath: String,
    val positionMs: Long,
    val note: String?
)

@Serializable
data class SettingsBackup(
    val defaultSpeed: Float,
    val completionThreshold: Float,
    val rssRefreshHours: Int,
    val wifiOnlyDownloads: Boolean,
    val autoDeleteListenedDownloads: Boolean,
    val skipSilence: Boolean,
    val theme: String
)

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val feedDao: FeedDao,
    private val folderDao: FolderDao,
    private val episodeDao: EpisodeDao,
    private val bookmarkDao: BookmarkDao,
    private val settingsStore: SettingsStore
) {
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    suspend fun export(destUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val feeds = feedDao.observeAll().first()
            val folders = folderDao.getAll()
            val settings = settingsStore.settings.first()

            val feedUrlById = feeds.associate { it.id to it.url }
            val folderUriById = folders.associate { it.id to it.treeUri }

            // Gather all episodes from all feeds + folders
            val allEpisodes = mutableListOf<Episode>()
            for (feed in feeds) allEpisodes.addAll(episodeDao.getByFeed(feed.id))
            for (folder in folders) allEpisodes.addAll(episodeDao.getByFolder(folder.id))

            val episodeBackups = allEpisodes.map { ep ->
                EpisodeBackup(
                    sourceType = ep.sourceType.name,
                    feedUrl = ep.feedId?.let { feedUrlById[it] },
                    guid = ep.guid,
                    treeUri = ep.folderId?.let { folderUriById[it] },
                    relativePath = ep.relativePath,
                    title = ep.title,
                    positionMs = ep.positionMs,
                    percentListened = ep.percentListened,
                    isListened = ep.isListened,
                    isFavourite = ep.isFavourite,
                    playCount = ep.playCount,
                    lastPlayedAt = ep.lastPlayedAt
                )
            }

            val backup = BackupData(
                exportedAt = System.currentTimeMillis(),
                feeds = feeds.map { FeedBackup(it.url, it.title, it.imageUrl) },
                folders = folders.map { FolderBackup(it.treeUri, it.displayName) },
                episodes = episodeBackups,
                bookmarks = emptyList(),
                settings = SettingsBackup(
                    defaultSpeed = settings.defaultSpeed,
                    completionThreshold = settings.completionThreshold,
                    rssRefreshHours = settings.rssRefreshHours,
                    wifiOnlyDownloads = settings.wifiOnlyDownloads,
                    autoDeleteListenedDownloads = settings.autoDeleteListenedDownloads,
                    skipSilence = settings.skipSilence,
                    theme = settings.theme
                )
            )

            context.contentResolver.openOutputStream(destUri)?.use { out ->
                out.write(json.encodeToString(backup).toByteArray())
            } ?: return@withContext false

            true
        } catch (_: Exception) { false }
    }
}
