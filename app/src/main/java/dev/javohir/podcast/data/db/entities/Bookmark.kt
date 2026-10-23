package dev.javohir.podcast.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmark",
    foreignKeys = [
        ForeignKey(
            entity = Episode::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["episodeId"])]
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val episodeId: Long,
    val positionMs: Long,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
