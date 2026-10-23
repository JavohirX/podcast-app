package dev.javohir.podcast.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.javohir.podcast.data.db.dao.*
import dev.javohir.podcast.data.db.entities.*

@Database(
    entities = [Feed::class, WatchedFolder::class, Episode::class, Bookmark::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun folderDao(): FolderDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        const val DATABASE_NAME = "podcast.db"
    }
}
