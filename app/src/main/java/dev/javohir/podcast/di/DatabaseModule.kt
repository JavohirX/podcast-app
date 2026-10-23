package dev.javohir.podcast.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.javohir.podcast.data.db.AppDatabase
import dev.javohir.podcast.data.db.dao.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideFeedDao(db: AppDatabase): FeedDao = db.feedDao()
    @Provides fun provideFolderDao(db: AppDatabase): FolderDao = db.folderDao()
    @Provides fun provideEpisodeDao(db: AppDatabase): EpisodeDao = db.episodeDao()
    @Provides fun provideBookmarkDao(db: AppDatabase): BookmarkDao = db.bookmarkDao()
}
