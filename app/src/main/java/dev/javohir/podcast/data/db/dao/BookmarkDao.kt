package dev.javohir.podcast.data.db.dao

import androidx.room.*
import dev.javohir.podcast.data.db.entities.Bookmark
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmark WHERE episodeId = :episodeId ORDER BY positionMs ASC")
    fun observeByEpisode(episodeId: Long): Flow<List<Bookmark>>

    @Insert
    suspend fun insert(bookmark: Bookmark): Long

    @Query("DELETE FROM bookmark WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM bookmark WHERE episodeId = :episodeId")
    suspend fun deleteAllForEpisode(episodeId: Long)
}
