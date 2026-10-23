package dev.javohir.podcast.ui.episode

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.javohir.podcast.data.db.entities.Bookmark
import dev.javohir.podcast.data.db.entities.Episode
import dev.javohir.podcast.data.download.DownloadCoordinator
import dev.javohir.podcast.data.repository.EpisodeRepository
import dev.javohir.podcast.ui.UserMessages
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class EpisodeDetailEvent {
    object NavigateBack : EpisodeDetailEvent()
}

data class EpisodeDetailUiState(
    val episode: Episode? = null,
    val bookmarks: List<Bookmark> = emptyList(),
    val loaded: Boolean = false
)

@HiltViewModel
class EpisodeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val episodeRepository: EpisodeRepository,
    private val downloadCoordinator: DownloadCoordinator,
    private val userMessages: UserMessages
) : ViewModel() {

    private val episodeId: Long = savedStateHandle["episodeId"] ?: 0L

    private val _events = MutableSharedFlow<EpisodeDetailEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<EpisodeDetailEvent> = _events.asSharedFlow()

    val uiState: StateFlow<EpisodeDetailUiState> = combine(
        episodeRepository.observeById(episodeId),
        episodeRepository.observeBookmarks(episodeId)
    ) { episode, bookmarks ->
        EpisodeDetailUiState(episode, bookmarks, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EpisodeDetailUiState())

    fun toggleFavourite() {
        val current = uiState.value.episode?.isFavourite ?: return
        viewModelScope.launch { episodeRepository.setFavourite(episodeId, !current) }
    }

    fun toggleListened() {
        val current = uiState.value.episode?.isListened ?: return
        viewModelScope.launch { episodeRepository.setListened(episodeId, !current) }
    }

    fun downloadEpisode() {
        viewModelScope.launch { downloadCoordinator.enqueue(episodeId) }
    }

    fun cancelDownload() {
        viewModelScope.launch { downloadCoordinator.cancel(episodeId) }
    }

    fun deleteLocalFile() {
        viewModelScope.launch {
            downloadCoordinator.deleteFile(episodeId)
            userMessages.show("Download deleted")
        }
    }

    fun deleteEpisode() {
        viewModelScope.launch {
            episodeRepository.deleteEpisode(episodeId)
            _events.emit(EpisodeDetailEvent.NavigateBack)
        }
    }

    fun addBookmark(positionMs: Long, note: String?) {
        viewModelScope.launch { episodeRepository.addBookmark(episodeId, positionMs, note) }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch { episodeRepository.deleteBookmark(bookmarkId) }
    }
}
