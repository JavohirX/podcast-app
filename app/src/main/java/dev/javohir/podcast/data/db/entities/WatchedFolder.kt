package dev.javohir.podcast.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "watched_folder",
    indices = [Index(value = ["treeUri"], unique = true)]
)
data class WatchedFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treeUri: String,
    val displayName: String,
    val includeSubfolders: Boolean = true,
    val lastScanAt: Long? = null,
    val addedAt: Long = System.currentTimeMillis()
)
