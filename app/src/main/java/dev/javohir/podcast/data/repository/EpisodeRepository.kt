package dev.javohir.podcast.data.repository

import dev.javohir.podcast.data.db.dao.BookmarkDao
import dev.javohir.podcast.data.db.dao.EpisodeDao
import dev.javohir.podcast.data.db.entities.Bookmark
import dev.javohir.podcast.data.db.entities.Episode
import dev.javohir.podcast.data.download.DownloadCoordinator
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpisodeRepository @Inject constructor(
    private val episodeDao: EpisodeDao,
    private val bookmarkDao: BookmarkDao,
    private val downloadCoordinator: DownloadCoordinator
) {
    fun observeNew(): Flow<List<Episode>> = episodeDao.observeNew()
    fun observeLeftOff(): Flow<List<Episode>> = episodeDao.observeLeftOff()
    fun observeFavourites(): Flow<List<Episode>> = episodeDao.observeFavourites()
    fun observeListened(): Flow<List<Episode>> = episodeDao.observeListened()
    fun observeByFeed(feedId: Long): Flow<List<Episode>> = episodeDao.observeByFeed(feedId)
    fun observeByFolder(folderId: Long): Flow<List<Episode>> = episodeDao.observeByFolder(folderId)
    fun observeById(id: Long): Flow<Episode?> = episodeDao.observeById(id)
    fun observeYouTubeEpisodes(): Flow<List<Episode>> = episodeDao.observeYouTubeEpisodes()
    fun observeBookmarks(episodeId: Long): Flow<List<Bookmark>> = bookmarkDao.observeByEpisode(episodeId)

    suspend fun getById(id: Long): Episode? = episodeDao.getById(id)
    suspend fun addEpisode(episode: Episode): Long = episodeDao.insert(episode)
    suspend fun getByYouTubeVideoId(videoId: String): Episode? = episodeDao.getByYouTubeVideoId(videoId)

    suspend fun updateProgress(id: Long, positionMs: Long, percentListened: Float) {
        episodeDao.updateProgress(id, positionMs, percentListened, System.currentTimeMillis())
    }

    suspend fun updateDuration(id: Long, durationMs: Long) {
        episodeDao.updateDuration(id, durationMs)
    }

    suspend fun setListened(id: Long, listened: Boolean) {
        episodeDao.setListened(id, listened)
    }

    suspend fun setFavourite(id: Long, fav: Boolean) {
        episodeDao.setFavourite(id, fav)
    }

    suspend fun addBookmark(episodeId: Long, positionMs: Long, note: String?): Long {
        return bookmarkDao.insert(Bookmark(episodeId = episodeId, positionMs = positionMs, note = note))
    }

    suspend fun deleteBookmark(bookmarkId: Long) {
        bookmarkDao.delete(bookmarkId)
    }

    suspend fun search(query: String): List<Episode> = episodeDao.search(query)

    suspend fun deleteLocalFile(id: Long) = downloadCoordinator.deleteFile(id)

    suspend fun deleteEpisode(id: Long) = downloadCoordinator.deleteEpisode(id)
}
