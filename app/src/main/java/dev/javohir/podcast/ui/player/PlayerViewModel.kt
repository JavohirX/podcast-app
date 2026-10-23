package dev.javohir.podcast.ui.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.javohir.podcast.data.db.entities.Episode
import dev.javohir.podcast.data.repository.EpisodeRepository
import dev.javohir.podcast.data.settings.SettingsStore
import dev.javohir.podcast.playback.PlaybackService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class PlayerUiState(
    val episode: Episode? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val sleepRemainingMs: Long? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val episodeRepository: EpisodeRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var positionJob: Job? = null

    init {
        connectToService()
    }

    private fun connectToService() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = future.get()
            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(isPlaying = isPlaying) }
                    if (isPlaying) startPositionPolling() else stopPositionPolling()
                }
                override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                    val id = item?.mediaId?.toLongOrNull() ?: return
                    viewModelScope.launch {
                        val ep = episodeRepository.getById(id)
                        _state.update { it.copy(episode = ep) }
                    }
                }
                override fun onPlaybackParametersChanged(params: androidx.media3.common.PlaybackParameters) {
                    _state.update { it.copy(speed = params.speed) }
                }
            })
            // Sync initial state
            val ctrl = controller ?: return@addListener
            val currentId = ctrl.currentMediaItem?.mediaId?.toLongOrNull()
            _state.update { it.copy(isPlaying = ctrl.isPlaying, speed = ctrl.playbackParameters.speed) }
            if (currentId != null) {
                viewModelScope.launch {
                    val ep = episodeRepository.getById(currentId)
                    _state.update { it.copy(episode = ep) }
                }
            }
            if (ctrl.isPlaying) startPositionPolling()
        }, MoreExecutors.directExecutor())
    }

    private fun startPositionPolling() {
        stopPositionPolling()
        positionJob = viewModelScope.launch {
            while (isActive) {
                val ctrl = controller ?: break
                _state.update {
                    it.copy(
                        positionMs = ctrl.currentPosition.coerceAtLeast(0L),
                        durationMs = ctrl.duration.takeIf { d -> d > 0 } ?: it.durationMs
                    )
                }
                delay(1_000L)
            }
        }
    }

    private fun stopPositionPolling() {
        positionJob?.cancel()
        positionJob = null
    }

    fun playPause() {
        val ctrl = controller ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    fun seekForward(ms: Long = 30_000L) {
        controller?.seekForward()
    }

    fun seekBack(ms: Long = 10_000L) {
        controller?.seekBack()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun setSpeed(speed: Float) {
        val tenths = (speed * 10).toInt().coerceIn(5, 20)
        val rounded = tenths / 10f
        val args = Bundle().apply { putFloat(PlaybackService.ARG_SPEED, rounded) }
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.CUSTOM_CMD_SET_SPEED, Bundle.EMPTY), args
        )
    }

    fun setSleepTimer(durationMs: Long) {
        val args = Bundle().apply { putLong(PlaybackService.ARG_SLEEP_MS, durationMs) }
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.CUSTOM_CMD_SLEEP_TIMER, Bundle.EMPTY), args
        )
    }

    fun cancelSleepTimer() {
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.CUSTOM_CMD_SLEEP_TIMER, Bundle.EMPTY),
            Bundle().apply { putLong(PlaybackService.ARG_SLEEP_MS, -1L) }
        )
    }

    fun toggleFavourite() {
        val ep = _state.value.episode ?: return
        viewModelScope.launch { episodeRepository.setFavourite(ep.id, !ep.isFavourite) }
    }

    fun addBookmark(note: String?) {
        val ep = _state.value.episode ?: return
        val pos = _state.value.positionMs
        viewModelScope.launch { episodeRepository.addBookmark(ep.id, pos, note) }
    }

    override fun onCleared() {
        stopPositionPolling()
        controller?.release()
    }
}
