package dev.javohir.podcast

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.lifecycle.HiltViewModel
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import dev.javohir.podcast.data.db.dao.EpisodeDao
import dev.javohir.podcast.data.download.DownloadCoordinator
import dev.javohir.podcast.data.settings.AppSettings
import dev.javohir.podcast.data.settings.SettingsStore
import dev.javohir.podcast.playback.PlaybackService
import dev.javohir.podcast.playback.PlaybackUriResolver
import dev.javohir.podcast.ui.UserMessages
import dev.javohir.podcast.ui.components.MiniPlayer
import dev.javohir.podcast.ui.navigation.AppNavHost
import dev.javohir.podcast.ui.navigation.BottomNavBar
import dev.javohir.podcast.ui.navigation.Routes
import dev.javohir.podcast.ui.player.PlayerViewModel
import dev.javohir.podcast.ui.theme.PodcastTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var episodeDao: EpisodeDao
    @Inject lateinit var playbackUriResolver: PlaybackUriResolver
    @Inject lateinit var downloadCoordinator: DownloadCoordinator

    private var mediaController: MediaController? = null
    private var pendingPlay: Pair<Long, List<Long>>? = null
    private val playLock = Any()
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        MediaController.Builder(this, token).buildAsync().also { future ->
            future.addListener({
                try {
                    val controller = future.get()
                    val pending = synchronized(playLock) {
                        mediaController = controller
                        val queued = pendingPlay
                        pendingPlay = null
                        queued
                    }
                    if (pending != null) startPlayback(controller, pending.first, pending.second)
                } catch (_: Exception) {
                }
            }, MoreExecutors.directExecutor())
        }

        val sharedUrl = extractSharedUrl(intent)

        setContent {
            val settings by settingsStore.settings.collectAsState(initial = AppSettings())
            PodcastTheme(themePreference = settings.theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PodcastAppContent(
                        initialFeedUrl = sharedUrl,
                        onPlayEpisode = { episodeId, queue -> playEpisode(episodeId, queue) }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        activityScope.launch(Dispatchers.IO) { downloadCoordinator.reconcile() }
    }

    override fun onDestroy() {
        mediaController?.release()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }

    private fun playEpisode(episodeId: Long, queue: List<Long>) {
        val ctrl = synchronized(playLock) {
            val ready = mediaController
            if (ready == null) {
                pendingPlay = episodeId to queue
                null
            } else {
                ready
            }
        } ?: return
        startPlayback(ctrl, episodeId, queue)
    }

    private fun startPlayback(ctrl: MediaController, episodeId: Long, queue: List<Long>) {
        val ids = queue.ifEmpty { listOf(episodeId) }
        activityScope.launch(Dispatchers.IO) {
            val items = ids.mapNotNull { id ->
                val ep = episodeDao.getById(id) ?: return@mapNotNull null
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
            val startIndex = ids.indexOf(episodeId).coerceAtLeast(0)
            launch(Dispatchers.Main) {
                ctrl.setMediaItems(items, startIndex, 0)
                ctrl.prepare()
                ctrl.play()
            }
        }
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
                ?.takeIf { it.startsWith("http") }
            Intent.ACTION_VIEW -> intent.dataString?.takeIf { it.startsWith("http") }
            else -> null
        }
    }
}

@Composable
private fun PodcastAppContent(
    initialFeedUrl: String? = null,
    onPlayEpisode: (Long, List<Long>) -> Unit
) {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val messagesViewModel: AppMessageViewModel = hiltViewModel()
    val playerState by playerViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val tabHistory = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf(Routes.HOME) }
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showChrome = route != Routes.PLAYER

    fun openTab(target: String) {
        if (tabHistory.lastOrNull() == target) {
            navController.popBackStack(Routes.tabRoot(target), inclusive = false)
            return
        }
        tabHistory.remove(target)
        tabHistory.add(target)
        navController.navigate(target) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BackHandler(enabled = route in Routes.TAB_ROOTS && tabHistory.size > 1) {
        tabHistory.removeAt(tabHistory.lastIndex)
        val previous = tabHistory.last()
        navController.navigate(previous) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(messagesViewModel) {
        messagesViewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showChrome) {
                Column {
                    MiniPlayer(
                        title = playerState.episode?.title,
                        artworkUrl = playerState.episode?.artworkUrl,
                        isPlaying = playerState.isPlaying,
                        progress = if (playerState.durationMs > 0)
                            playerState.positionMs.toFloat() / playerState.durationMs else 0f,
                        onPlayPauseClick = playerViewModel::playPause,
                        onClick = {
                            navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                        }
                    )
                    BottomNavBar(navController, onTabSelected = ::openTab)
                }
            }
        }
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            onPlayEpisode = onPlayEpisode,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@HiltViewModel
class AppMessageViewModel @Inject constructor(
    userMessages: UserMessages
) : ViewModel() {
    val messages = userMessages.messages
}
