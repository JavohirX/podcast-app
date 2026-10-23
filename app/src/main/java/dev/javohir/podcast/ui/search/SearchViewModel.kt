package dev.javohir.podcast.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.javohir.podcast.data.db.entities.DownloadStatus
import dev.javohir.podcast.data.db.entities.Episode
import dev.javohir.podcast.data.download.DownloadCoordinator
import dev.javohir.podcast.data.repository.EpisodeRepository
import dev.javohir.podcast.ui.UserMessages
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val episodeRepository: EpisodeRepository,
    private val downloadCoordinator: DownloadCoordinator,
    private val userMessages: UserMessages
) : ViewModel() {

    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    val results: StateFlow<List<Episode>> = query
        .debounce(300)
        .map { q ->
            if (q.isBlank()) emptyList()
            else episodeRepository.search(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(q: String) {
        savedStateHandle[KEY_QUERY] = q
    }

    fun toggleFavourite(id: Long, current: Boolean) {
        viewModelScope.launch { episodeRepository.setFavourite(id, !current) }
    }

    fun toggleListened(id: Long, current: Boolean) {
        viewModelScope.launch { episodeRepository.setListened(id, !current) }
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

    fun deleteLocalFile(id: Long) {
        viewModelScope.launch {
            downloadCoordinator.deleteFile(id)
            userMessages.show("Download deleted")
        }
    }

    fun deleteEpisode(id: Long) {
        viewModelScope.launch { episodeRepository.deleteEpisode(id) }
    }

    companion object {
        private const val KEY_QUERY = "query"
    }
}
