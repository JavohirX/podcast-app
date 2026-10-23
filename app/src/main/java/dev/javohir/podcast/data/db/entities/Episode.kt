package dev.javohir.podcast.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SourceType { RSS, LOCAL, YOUTUBE }

enum class DownloadStatus { NONE, QUEUED, RUNNING, DONE, FAILED }

@Entity(
    tableName = "episode",
    foreignKeys = [
        ForeignKey(
            entity = Feed::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WatchedFolder::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["feedId", "guid"], unique = true),
        Index(value = ["folderId", "relativePath"], unique = true),
        Index(value = ["publishedAt"]),
        Index(value = ["percentListened"]),
        Index(value = ["isFavourite"]),
        Index(value = ["isListened"]),
        Index(value = ["feedId"]),
        Index(value = ["folderId"])
    ]
)
data class Episode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // Identity & source
    val sourceType: SourceType,
    val feedId: Long? = null,
    val folderId: Long? = null,
    val guid: String? = null,
    val relativePath: String? = null,
    val fileSize: Long? = null,

    // Display metadata
    val title: String,
    val description: String? = null,
    val author: String? = null,
    val publishedAt: Long,
    val durationMs: Long? = null,
    val artworkUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis(),

    // Playback state
    val positionMs: Long = 0L,
    val percentListened: Float = 0f,
    val isListened: Boolean = false,
    val lastPlayedAt: Long? = null,
    val playCount: Int = 0,

    // Flags
    val isFavourite: Boolean = false,
    val isMissing: Boolean = false,

    // Download (RSS only)
    val downloadStatus: DownloadStatus = DownloadStatus.NONE,
    val downloadedFilePath: String? = null,
    val enclosureUrl: String? = null,
    val enclosureSize: Long? = null,
    val lastDownloadError: String? = null
)
