package dev.javohir.podcast.ui.feed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.javohir.podcast.data.db.entities.DownloadStatus
import dev.javohir.podcast.data.db.entities.Episode
import dev.javohir.podcast.data.db.entities.Feed
import dev.javohir.podcast.data.download.DownloadCoordinator
import dev.javohir.podcast.data.repository.EpisodeRepository
import dev.javohir.podcast.data.repository.FeedRepository
import dev.javohir.podcast.ui.UserMessages
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedDetailUiState(
    val feed: Feed? = null,
    val episodes: List<Episode> = emptyList(),
    val isRefreshing: Boolean = false
)

@HiltViewModel
class FeedDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val feedRepository: FeedRepository,
    private val episodeRepository: EpisodeRepository,
    private val downloadCoordinator: DownloadCoordinator,
    private val userMessages: UserMessages
) : ViewModel() {

    private val feedId: Long = savedStateHandle["feedId"] ?: 0L
    private val _refreshing = MutableStateFlow(false)

    val uiState: StateFlow<FeedDetailUiState> = combine(
        flow { emit(feedRepository.getById(feedId)) },
        episodeRepository.observeByFeed(feedId),
        _refreshing
    ) { feed, episodes, refreshing ->
        FeedDetailUiState(feed, episodes, refreshing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedDetailUiState())

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            feedRepository.refresh(feedId)
            _refreshing.value = false
        }
    }

    fun unsubscribe() {
        viewModelScope.launch { feedRepository.unsubscribe(feedId) }
    }

    fun toggleFavourite(id: Long, current: Boolean) {
        viewModelScope.launch { episodeRepository.setFavourite(id, !current) }
    }

    fun toggleDownload(episodeId: Long, status: DownloadStatus) {
        viewModelScope.launch {
            when (status) {
                DownloadStatus.NONE, DownloadStatus.FAILED -> downloadCoordinator.enqueue(episodeId)
                DownloadStatus.QUEUED, DownloadStatus.RUNNING -> downloadCoordinator.cancel(episodeId)
                DownloadStatus.DONE -> Unit
            }
        }
    }

    fun toggleListened(id: Long, current: Boolean) {
        viewModelScope.launch { episodeRepository.setListened(id, !current) }
    }

    fun deleteLocalFile(id: Long) {
        viewModelScope.launch {
            downloadCoordinator.deleteFile(id)
            userMessages.show("Download deleted")
        }
    }

    fun deleteEpisode(id: Long) {
        viewModelScope.launch { episodeRepository.deleteEpisode(id) }
    }
}
