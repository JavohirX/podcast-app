package dev.javohir.podcast.data.db

import androidx.room.TypeConverter
import dev.javohir.podcast.data.db.entities.DownloadStatus
import dev.javohir.podcast.data.db.entities.SourceType

class Converters {
    @TypeConverter fun fromSourceType(v: SourceType): String = v.name
    @TypeConverter fun toSourceType(v: String): SourceType = SourceType.valueOf(v)

    @TypeConverter fun fromDownloadStatus(v: DownloadStatus): String = v.name
    @TypeConverter fun toDownloadStatus(v: String): DownloadStatus = DownloadStatus.valueOf(v)
}
