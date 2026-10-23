package dev.javohir.podcast.data.db.dao

import androidx.room.*
import dev.javohir.podcast.data.db.entities.WatchedFolder
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM watched_folder ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WatchedFolder>>

    @Query("SELECT * FROM watched_folder")
    suspend fun getAll(): List<WatchedFolder>

    @Query("SELECT * FROM watched_folder WHERE id = :id")
    suspend fun getById(id: Long): WatchedFolder?

    @Query("SELECT * FROM watched_folder WHERE treeUri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): WatchedFolder?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(folder: WatchedFolder): Long

    @Update
    suspend fun update(folder: WatchedFolder)

    @Query("DELETE FROM watched_folder WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE watched_folder SET lastScanAt = :time WHERE id = :id")
    suspend fun updateLastScanAt(id: Long, time: Long)
}
