package dev.javohir.podcast.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.javohir.podcast.data.db.entities.DownloadStatus
import dev.javohir.podcast.data.db.entities.Episode
import dev.javohir.podcast.data.db.entities.Feed
import dev.javohir.podcast.data.db.entities.SourceType
import dev.javohir.podcast.data.db.entities.WatchedFolder
import dev.javohir.podcast.data.repository.EpisodeRepository
import dev.javohir.podcast.data.repository.FeedRepository
import dev.javohir.podcast.data.repository.FolderRepository
import dev.javohir.podcast.data.download.DownloadCoordinator
import dev.javohir.podcast.data.youtube.YouTubeMetadataFetcher
import dev.javohir.podcast.ui.UserMessages
import dev.javohir.podcast.data.youtube.YouTubeVideoMeta
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AddYouTubeState {
    object Idle : AddYouTubeState()
    object Loading : AddYouTubeState()
    data class Preview(val meta: YouTubeVideoMeta) : AddYouTubeState()
    data class Error(val message: String) : AddYouTubeState()
    object Added : AddYouTubeState()
}

data class LibraryUiState(
    val feeds: List<Feed> = emptyList(),
    val folders: List<WatchedFolder> = emptyList(),
    val youTubeEpisodes: List<Episode> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val folderRepository: FolderRepository,
    private val episodeRepository: EpisodeRepository,
    private val youTubeMetadataFetcher: YouTubeMetadataFetcher,
    private val downloadCoordinator: DownloadCoordinator,
    private val userMessages: UserMessages
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> = combine(
        feedRepository.observeAll(),
        folderRepository.observeAll(),
        episodeRepository.observeYouTubeEpisodes()
    ) { feeds, folders, ytEpisodes ->
        LibraryUiState(feeds = feeds, folders = folders, youTubeEpisodes = ytEpisodes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    private val _addYouTubeState = MutableStateFlow<AddYouTubeState>(AddYouTubeState.Idle)
    val addYouTubeState: StateFlow<AddYouTubeState> = _addYouTubeState.asStateFlow()

    fun addFeed(url: String) {
        viewModelScope.launch {
            feedRepository.subscribe(url)
        }
    }

    fun addFolder(uri: Uri, displayName: String) {
        viewModelScope.launch {
            folderRepository.addFolder(uri, displayName)
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            feedRepository.observeAll().first().forEach { feed ->
                feedRepository.refresh(feed.id)
            }
            folderRepository.scanAll(force = true)
        }
    }

    fun fetchYouTubeMetadata(url: String) {
        viewModelScope.launch {
            _addYouTubeState.value = AddYouTubeState.Loading
            val meta = youTubeMetadataFetcher.fetch(url)
            _addYouTubeState.value = if (meta != null) {
                AddYouTubeState.Preview(meta)
            } else {
                AddYouTubeState.Error("Could not fetch video info — check the URL and try again")
            }
        }
    }

    fun confirmAddYouTube(meta: YouTubeVideoMeta) {
        viewModelScope.launch {
            val existing = episodeRepository.getByYouTubeVideoId(meta.videoId)
            if (existing != null) {
                _addYouTubeState.value = AddYouTubeState.Error("Already in your library")
                return@launch
            }
            val episode = Episode(
                sourceType = SourceType.YOUTUBE,
                guid = meta.videoId,
                title = meta.title,
                author = meta.author,
                artworkUrl = meta.thumbnailUrl,
                publishedAt = System.currentTimeMillis(),
                enclosureUrl = "https://www.youtube.com/watch?v=${meta.videoId}"
            )
            episodeRepository.addEpisode(episode)
            _addYouTubeState.value = AddYouTubeState.Added
        }
    }

    fun resetAddYouTubeState() {
        _addYouTubeState.value = AddYouTubeState.Idle
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
