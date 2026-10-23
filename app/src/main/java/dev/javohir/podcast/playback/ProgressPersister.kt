package dev.javohir.podcast.playback

import androidx.media3.common.Player
import dev.javohir.podcast.data.repository.EpisodeRepository
import dev.javohir.podcast.data.settings.SettingsStore
import dev.javohir.podcast.domain.UpdateProgressUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class ProgressPersister(
    private val episodeRepository: EpisodeRepository,
    private val updateProgressUseCase: UpdateProgressUseCase,
    private val settingsStore: SettingsStore
) {
    // Main dispatcher: the player may only be touched from the main thread.
    // Room suspend DAOs are main-safe, so DB writes can launch from here too.
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tickJob: Job? = null
    private var currentEpisodeId: Long? = null

    fun attach(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startTicking(player) else { flush(player); stopTicking() }
            }
            override fun onPositionDiscontinuity(oldPos: Player.PositionInfo, newPos: Player.PositionInfo, reason: Int) {
                flush(player)
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                flush(player)
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) flush(player)
            }
        })
    }

    fun setCurrentEpisode(id: Long?) {
        currentEpisodeId = id
    }

    private fun startTicking(player: Player) {
        stopTicking()
        tickJob = scope.launch {
            while (isActive) {
                delay(5_000L)
                flush(player)
            }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun flush(player: Player) {
        val epId = currentEpisodeId ?: return
        val pos = player.currentPosition
        val dur = player.duration.takeIf { it > 0 } ?: return
        scope.launch { updateProgressUseCase(epId, pos, dur) }
    }

    fun release() {
        stopTicking()
        scope.cancel()
    }
}
