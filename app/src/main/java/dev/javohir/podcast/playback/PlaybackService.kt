package dev.javohir.podcast.playback

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import dev.javohir.podcast.data.db.entities.SourceType
import dev.javohir.podcast.data.download.DownloadCoordinator
import dev.javohir.podcast.data.download.DownloadFileStore
import dev.javohir.podcast.data.db.entities.DownloadStatus
import dev.javohir.podcast.data.repository.EpisodeRepository
import dev.javohir.podcast.data.settings.SettingsStore
import dev.javohir.podcast.domain.UpdateProgressUseCase
import dev.javohir.podcast.ui.UserMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    companion object {
        const val CUSTOM_CMD_SLEEP_TIMER = "sleep_timer"
        const val CUSTOM_CMD_SET_SPEED = "set_speed"
        const val ARG_SPEED = "speed"
        const val ARG_SLEEP_MS = "sleep_ms"
        const val ARG_SLEEP_END_OF_EP = "sleep_end_of_ep"
        const val EXTRA_EPISODE_ID = "episode_id"
    }

    @Inject lateinit var episodeRepository: EpisodeRepository
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var updateProgressUseCase: UpdateProgressUseCase
    @Inject lateinit var playbackSession: PlaybackSession
    @Inject lateinit var playbackUriResolver: PlaybackUriResolver
    @Inject lateinit var downloadCoordinator: DownloadCoordinator
    @Inject lateinit var fileStore: DownloadFileStore
    @Inject lateinit var userMessages: UserMessages

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private val persister: ProgressPersister by lazy {
        ProgressPersister(episodeRepository, updateProgressUseCase, settingsStore)
    }
    private val sleepTimer = SleepTimer()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val stopEpisode: (Long) -> Unit = { episodeId ->
        if (::player.isInitialized) {
            val target = episodeId.toString()
            val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == target }
            if (index != null) {
                val wasCurrent = player.currentMediaItemIndex == index
                player.removeMediaItem(index)
                if (wasCurrent) player.pause()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handle audio focus
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        serviceScope.launch {
            val settings = settingsStore.settings.first()
            player.skipSilenceEnabled = settings.skipSilence
        }

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId?.toLongOrNull()
                persister.setCurrentEpisode(id)
                if (id != null) {
                    serviceScope.launch {
                        val ep = episodeRepository.getById(id) ?: return@launch
                        val settings = settingsStore.settings.first()
                        val rewind = settings.resumeRewindMs.toLong()
                        val resume = (ep.positionMs - rewind).coerceAtLeast(0L)
                        if (resume > 0) player.seekTo(resume)
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
                    val dur = player.duration.takeIf { it > 0 } ?: return
                    serviceScope.launch { episodeRepository.updateDuration(id, dur) }
                }
                if (state == Player.STATE_ENDED) {
                    sleepTimer.onEpisodeEnd()
                    persister.setCurrentEpisode(null)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
                serviceScope.launch(Dispatchers.IO) {
                    val ep = episodeRepository.getById(id) ?: return@launch
                    if (ep.sourceType == SourceType.LOCAL) return@launch
                    val path = ep.downloadedFilePath
                    if (ep.downloadStatus != DownloadStatus.DONE || path == null) return@launch
                    if (fileStore.exists(path)) return@launch
                    downloadCoordinator.noteMissingFile(id)
                    val message = if (ep.sourceType == SourceType.YOUTUBE) {
                        "File was deleted. Download it again."
                    } else {
                        "Saved file is gone. Streaming instead."
                    }
                    userMessages.show(message)
                    withContext(Dispatchers.Main) { stopEpisode(id) }
                }
            }
        })

        playbackSession.register(stopEpisode)

        persister.attach(player)

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.isPlaying) stopSelf()
    }

    override fun onDestroy() {
        playbackSession.unregister(stopEpisode)
        persister.release()
        sleepTimer.release()
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    fun playEpisode(episodeId: Long, queue: List<Long> = emptyList()) {
        serviceScope.launch {
            val allIds = if (queue.isEmpty()) listOf(episodeId) else queue
            val items = allIds.mapNotNull { id ->
                val ep = episodeRepository.getById(id) ?: return@mapNotNull null
                val uri = playbackUriResolver.resolve(ep) ?: return@mapNotNull null
                MediaItem.Builder()
                    .setMediaId(id.toString())
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(ep.title)
                            .setArtist(ep.author)
                            .setArtworkUri(ep.artworkUrl?.let { Uri.parse(it) })
                            .build()
                    )
                    .build()
            }

            if (items.isEmpty()) return@launch
            val startIndex = allIds.indexOf(episodeId).coerceAtLeast(0)
            player.setMediaItems(items, startIndex, 0)
            player.prepare()
            player.play()
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(SessionCommand(CUSTOM_CMD_SET_SPEED, Bundle.EMPTY))
                .add(SessionCommand(CUSTOM_CMD_SLEEP_TIMER, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CUSTOM_CMD_SET_SPEED -> {
                    val speed = args.getFloat(ARG_SPEED, 1f)
                    player.setPlaybackSpeed(speed)
                    serviceScope.launch { settingsStore.setDefaultSpeed(speed) }
                }
                CUSTOM_CMD_SLEEP_TIMER -> {
                    val ms = args.getLong(ARG_SLEEP_MS, -1L)
                    val endOfEp = args.getBoolean(ARG_SLEEP_END_OF_EP, false)
                    if (ms < 0 && !endOfEp) {
                        sleepTimer.cancel()
                    } else {
                        val target = if (endOfEp) SleepTimerTarget.EndOfEpisode
                                     else SleepTimerTarget.Duration(ms)
                        sleepTimer.start(target, { vol -> player.volume = vol }) {
                            player.pause()
                        }
                    }
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
}
