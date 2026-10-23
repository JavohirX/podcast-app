package dev.javohir.podcast.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "feed",
    indices = [Index(value = ["url"], unique = true)]
)
data class Feed(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val description: String? = null,
    val author: String? = null,
    val imageUrl: String? = null,
    val link: String? = null,
    val lastFetchAt: Long? = null,
    val lastFetchError: String? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)
