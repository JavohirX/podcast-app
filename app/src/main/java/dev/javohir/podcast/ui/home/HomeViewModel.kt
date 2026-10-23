package dev.javohir.podcast.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.javohir.podcast.data.db.entities.DownloadStatus
import dev.javohir.podcast.data.db.entities.Episode
import dev.javohir.podcast.data.download.DownloadCoordinator
import dev.javohir.podcast.data.repository.EpisodeRepository
import dev.javohir.podcast.ui.UserMessages
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val newEpisodes: List<Episode> = emptyList(),
    val leftOffEpisodes: List<Episode> = emptyList(),
    val favourites: List<Episode> = emptyList(),
    val listened: List<Episode> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val episodeRepository: EpisodeRepository,
    private val downloadCoordinator: DownloadCoordinator,
    private val userMessages: UserMessages
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        episodeRepository.observeNew(),
        episodeRepository.observeLeftOff(),
        episodeRepository.observeFavourites(),
        episodeRepository.observeListened()
    ) { new, leftOff, favs, listened ->
        HomeUiState(new, leftOff, favs, listened)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun toggleFavourite(episodeId: Long, current: Boolean) {
        viewModelScope.launch {
            episodeRepository.setFavourite(episodeId, !current)
        }
    }

    fun toggleListened(episodeId: Long, current: Boolean) {
        viewModelScope.launch {
            episodeRepository.setListened(episodeId, !current)
        }
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

    fun deleteLocalFile(episodeId: Long) {
        viewModelScope.launch {
            downloadCoordinator.deleteFile(episodeId)
            userMessages.show("Download deleted")
        }
    }

    fun deleteEpisode(episodeId: Long) {
        viewModelScope.launch { episodeRepository.deleteEpisode(episodeId) }
    }
}
