package dev.javohir.podcast.data.db.dao

import androidx.room.*
import dev.javohir.podcast.data.db.entities.Feed
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Query("SELECT * FROM feed ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<Feed>>

    @Query("SELECT * FROM feed WHERE id = :id")
    suspend fun getById(id: Long): Feed?

    @Query("SELECT * FROM feed WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): Feed?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(feed: Feed): Long

    @Update
    suspend fun update(feed: Feed)

    @Query("DELETE FROM feed WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM feed")
    suspend fun count(): Int
}
