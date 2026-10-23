package dev.javohir.podcast.data.db.dao

import androidx.room.*
import dev.javohir.podcast.data.db.entities.DownloadStatus
import dev.javohir.podcast.data.db.entities.Episode
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {

    // ── Category views ──────────────────────────────────────────────────────

    @Query("""
        SELECT * FROM episode
        WHERE isListened = 0 AND isMissing = 0
        ORDER BY publishedAt DESC
    """)
    fun observeNew(): Flow<List<Episode>>

    @Query("""
        SELECT * FROM episode
        WHERE isListened = 0 AND isMissing = 0
          AND percentListened > 0 AND percentListened < 1.0
        ORDER BY percentListened DESC, lastPlayedAt DESC
    """)
    fun observeLeftOff(): Flow<List<Episode>>

    @Query("""
        SELECT * FROM episode
        WHERE isFavourite = 1 AND isMissing = 0
        ORDER BY publishedAt DESC
    """)
    fun observeFavourites(): Flow<List<Episode>>

    @Query("""
        SELECT * FROM episode
        WHERE isListened = 1 AND isMissing = 0
        ORDER BY lastPlayedAt DESC
    """)
    fun observeListened(): Flow<List<Episode>>

    // ── Feed / folder detail ────────────────────────────────────────────────

    @Query("SELECT * FROM episode WHERE feedId = :feedId ORDER BY publishedAt DESC")
    fun observeByFeed(feedId: Long): Flow<List<Episode>>

    @Query("SELECT * FROM episode WHERE folderId = :folderId ORDER BY publishedAt DESC")
    fun observeByFolder(folderId: Long): Flow<List<Episode>>

    // ── Lookups ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM episode WHERE id = :id")
    suspend fun getById(id: Long): Episode?

    @Query("SELECT * FROM episode WHERE id = :id")
    fun observeById(id: Long): Flow<Episode?>

    @Query("SELECT * FROM episode WHERE feedId = :feedId AND guid = :guid LIMIT 1")
    suspend fun getByGuid(feedId: Long, guid: String): Episode?

    @Query("SELECT * FROM episode WHERE folderId = :folderId AND relativePath = :path LIMIT 1")
    suspend fun getByPath(folderId: Long, path: String): Episode?

    @Query("SELECT * FROM episode WHERE sourceType = 'YOUTUBE' AND guid = :videoId LIMIT 1")
    suspend fun getByYouTubeVideoId(videoId: String): Episode?

    @Query("SELECT * FROM episode WHERE sourceType = 'YOUTUBE' ORDER BY addedAt DESC")
    fun observeYouTubeEpisodes(): Flow<List<Episode>>

    @Query("SELECT * FROM episode WHERE folderId = :folderId")
    suspend fun getByFolder(folderId: Long): List<Episode>

    @Query("SELECT * FROM episode WHERE feedId = :feedId")
    suspend fun getByFeed(feedId: Long): List<Episode>

    @Query("""
        SELECT * FROM episode WHERE folderId = :folderId
          AND isMissing = 1
          AND title = :title
          AND fileSize = :size
        LIMIT 1
    """)
    suspend fun findMovedFile(folderId: Long, title: String, size: Long): Episode?

    // ── Mutations ───────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(episode: Episode): Long

    @Update
    suspend fun update(episode: Episode)

    @Query("""
        UPDATE episode SET
            title = :title,
            description = :description,
            author = :author,
            publishedAt = :publishedAt,
            durationMs = :durationMs,
            artworkUrl = :artworkUrl,
            enclosureUrl = :enclosureUrl,
            enclosureSize = :enclosureSize
        WHERE feedId = :feedId AND guid = :guid
    """)
    suspend fun updateMetadata(
        feedId: Long, guid: String,
        title: String, description: String?, author: String?,
        publishedAt: Long, durationMs: Long?, artworkUrl: String?,
        enclosureUrl: String?, enclosureSize: Long?
    )

    @Query("UPDATE episode SET positionMs = :pos, percentListened = :pct, lastPlayedAt = :time WHERE id = :id")
    suspend fun updateProgress(id: Long, pos: Long, pct: Float, time: Long)

    @Query("UPDATE episode SET durationMs = :durationMs WHERE id = :id")
    suspend fun updateDuration(id: Long, durationMs: Long)

    @Query("UPDATE episode SET isListened = :listened, playCount = playCount + 1 WHERE id = :id")
    suspend fun setListened(id: Long, listened: Boolean)

    @Query("UPDATE episode SET isFavourite = :fav WHERE id = :id")
    suspend fun setFavourite(id: Long, fav: Boolean)

    @Query("UPDATE episode SET isMissing = :missing WHERE id = :id")
    suspend fun setMissing(id: Long, missing: Boolean)

    @Query("UPDATE episode SET downloadStatus = :status WHERE id = :id")
    suspend fun setDownloadStatus(id: Long, status: DownloadStatus)

    @Query("UPDATE episode SET downloadStatus = :status, lastDownloadError = :error WHERE id = :id")
    suspend fun setDownloadError(id: Long, status: DownloadStatus, error: String?)

    @Query("""
        UPDATE episode SET downloadStatus = 'RUNNING'
        WHERE id = :id AND downloadStatus IN ('QUEUED', 'RUNNING', 'FAILED')
    """)
    suspend fun markRunningIfActive(id: Long): Int

    @Query("""
        UPDATE episode SET downloadStatus = :status, lastDownloadError = :error
        WHERE id = :id AND downloadStatus IN ('QUEUED', 'RUNNING')
    """)
    suspend fun setDownloadErrorIfActive(id: Long, status: DownloadStatus, error: String?): Int

    @Query("UPDATE episode SET downloadStatus = :status, downloadedFilePath = :path, lastDownloadError = NULL WHERE id = :id")
    suspend fun setDownloadDone(id: Long, status: DownloadStatus, path: String)

    @Query("""
        UPDATE episode SET downloadStatus = 'DONE', downloadedFilePath = :path, lastDownloadError = NULL
        WHERE id = :id AND downloadStatus IN ('QUEUED', 'RUNNING')
    """)
    suspend fun setDownloadDoneIfActive(id: Long, path: String): Int

    @Query("UPDATE episode SET downloadStatus = 'NONE', downloadedFilePath = NULL, lastDownloadError = NULL WHERE id = :id")
    suspend fun clearDownload(id: Long)

    @Query("""
        SELECT * FROM episode
        WHERE sourceType != 'LOCAL'
          AND downloadStatus IN ('DONE', 'QUEUED', 'RUNNING')
    """)
    suspend fun getManagedDownloads(): List<Episode>

    @Query("""
        SELECT * FROM episode
        WHERE sourceType != 'LOCAL' AND downloadStatus = 'DONE' AND isListened = 1
    """)
    suspend fun getListenedRemoteDownloads(): List<Episode>

    @Query("""
        SELECT * FROM episode
        WHERE sourceType = 'YOUTUBE' AND downloadStatus = 'DONE' AND isListened = 1
    """)
    suspend fun getYouTubeListenedDownloads(): List<Episode>

    @Query("DELETE FROM episode WHERE id = :id")
    suspend fun delete(id: Long)

    // ── Search ──────────────────────────────────────────────────────────────

    @Query("""
        SELECT e.* FROM episode e
        LEFT JOIN feed f ON e.feedId = f.id
        WHERE e.isMissing = 0
          AND (e.title LIKE '%' || :query || '%'
            OR e.author LIKE '%' || :query || '%'
            OR f.title LIKE '%' || :query || '%')
        ORDER BY e.publishedAt DESC
        LIMIT 100
    """)
    suspend fun search(query: String): List<Episode>

    // ── Storage helpers ─────────────────────────────────────────────────────

    @Query("""
        SELECT * FROM episode
        WHERE feedId = :feedId AND downloadStatus = 'DONE' AND isListened = 1
    """)
    suspend fun getListenedDownloads(feedId: Long): List<Episode>

    @Query("SELECT COUNT(*) FROM episode WHERE feedId = :feedId AND downloadStatus = 'DONE'")
    suspend fun countDownloaded(feedId: Long): Int
}
