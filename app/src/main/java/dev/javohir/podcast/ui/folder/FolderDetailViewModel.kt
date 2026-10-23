package dev.javohir.podcast.ui.folder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.javohir.podcast.data.db.entities.Episode
import dev.javohir.podcast.data.db.entities.WatchedFolder
import dev.javohir.podcast.data.repository.EpisodeRepository
import dev.javohir.podcast.data.repository.FolderRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FolderDetailUiState(
    val folder: WatchedFolder? = null,
    val episodes: List<Episode> = emptyList(),
    val isScanning: Boolean = false
)

@HiltViewModel
class FolderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val folderRepository: FolderRepository,
    private val episodeRepository: EpisodeRepository
) : ViewModel() {

    private val folderId: Long = savedStateHandle["folderId"] ?: 0L
    private val _scanning = MutableStateFlow(false)

    val uiState: StateFlow<FolderDetailUiState> = combine(
        flow { emit(folderRepository.getById(folderId)) },
        episodeRepository.observeByFolder(folderId),
        _scanning
    ) { folder, episodes, scanning ->
        FolderDetailUiState(folder, episodes, scanning)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FolderDetailUiState())

    fun rescan() {
        viewModelScope.launch {
            _scanning.value = true
            folderRepository.scanFolder(folderId, force = true)
            _scanning.value = false
        }
    }

    fun removeFolder() {
        viewModelScope.launch { folderRepository.removeFolder(folderId) }
    }

    fun toggleFavourite(id: Long, current: Boolean) {
        viewModelScope.launch { episodeRepository.setFavourite(id, !current) }
    }

    fun toggleListened(id: Long, current: Boolean) {
        viewModelScope.launch { episodeRepository.setListened(id, !current) }
    }

    fun deleteEpisode(id: Long) {
        viewModelScope.launch { episodeRepository.deleteEpisode(id) }
    }
}
