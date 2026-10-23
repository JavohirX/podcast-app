package dev.javohir.podcast.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.javohir.podcast.data.db.dao.EpisodeDao
import dev.javohir.podcast.data.db.entities.Feed
import dev.javohir.podcast.data.download.DownloadCoordinator
import dev.javohir.podcast.data.download.DownloadFileStore
import dev.javohir.podcast.data.repository.FeedRepository
import dev.javohir.podcast.ui.UserMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class StorageUiState(
    val feedStats: List<Pair<Feed, Long>> = emptyList(),
    val youtubeBytes: Long = 0L
)

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val episodeDao: EpisodeDao,
    private val downloadCoordinator: DownloadCoordinator,
    private val fileStore: DownloadFileStore,
    private val userMessages: UserMessages
) : ViewModel() {

    val uiState: StateFlow<StorageUiState> = feedRepository.observeAll()
        .map { feeds ->
            withContext(Dispatchers.IO) {
                val stats = feeds.map { feed ->
                    val bytes = episodeDao.getListenedDownloads(feed.id)
                        .sumOf { fileStore.size(it.downloadedFilePath) }
                    feed to bytes
                }
                val youtubeBytes = episodeDao.getYouTubeListenedDownloads()
                    .sumOf { fileStore.size(it.downloadedFilePath) }
                StorageUiState(stats, youtubeBytes)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StorageUiState())

    fun deleteAllListened() {
        viewModelScope.launch {
            val count = downloadCoordinator.deleteAllListened()
            userMessages.show(
                if (count == 1) "Deleted 1 download" else "Deleted $count downloads"
            )
        }
    }
}
