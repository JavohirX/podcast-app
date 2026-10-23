package dev.javohir.podcast.ui.episode

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import dev.javohir.podcast.ui.components.rememberAnchoredListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.javohir.podcast.data.db.entities.DownloadStatus
import dev.javohir.podcast.ui.components.Artwork
import dev.javohir.podcast.ui.components.DeleteConfirmHost
import dev.javohir.podcast.data.db.entities.Episode
import dev.javohir.podcast.data.db.entities.SourceType
import dev.javohir.podcast.data.download.DeleteTarget
import dev.javohir.podcast.data.download.deletePrompt
import dev.javohir.podcast.ui.components.ProgressBarSmall
import dev.javohir.podcast.ui.components.rememberDownloadProgress
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDetailScreen(
    episodeId: Long,
    onBack: () -> Unit,
    onPlayClick: (Long, List<Long>) -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: EpisodeDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val episode = state.episode
    var confirmTarget by remember { mutableStateOf<DeleteTarget?>(null) }
    val listState = rememberAnchoredListState("episode-$episodeId")

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EpisodeDetailEvent.NavigateBack -> onBack()
            }
        }
    }

    if (episode == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (state.loaded) "Episode" else "") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.loaded) {
                    Text("Episode unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    CircularProgressIndicator()
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavourite() }) {
                        AnimatedContent(
                            targetState = episode.isFavourite,
                            transitionSpec = {
                                scaleIn() togetherWith scaleOut()
                            },
                            label = "FavouriteAnimation"
                        ) { isFavourite ->
                            Icon(
                                if (isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                "Favourite",
                                tint = if (isFavourite) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                EpisodeHeader(
                    episode = episode,
                    onPlayClick = { onPlayClick(episode.id, emptyList()) },
                    onDownloadClick = {
                        when (episode.downloadStatus) {
                            DownloadStatus.NONE, DownloadStatus.FAILED -> viewModel.downloadEpisode()
                            DownloadStatus.QUEUED, DownloadStatus.RUNNING -> viewModel.cancelDownload()
                            DownloadStatus.DONE -> Unit
                        }
                    },
                    onListenedClick = { viewModel.toggleListened() },
                    onDeleteLocally = { confirmTarget = DeleteTarget.DOWNLOAD },
                    onDelete = { confirmTarget = DeleteTarget.EPISODE }
                )
            }

            if (state.bookmarks.isNotEmpty()) {
                item {
                    Text(
                        "Bookmarks",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(state.bookmarks, key = { it.id }) { bookmark ->
                    ListItem(
                        headlineContent = { Text(formatMs(bookmark.positionMs)) },
                        supportingContent = bookmark.note?.let { { Text(it) } },
                        trailingContent = {
                            IconButton(onClick = { viewModel.deleteBookmark(bookmark.id) }) {
                                Icon(Icons.Default.Delete, "Delete bookmark")
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }

            episode.description?.let { desc ->
                item {
                    Text(
                        "Show Notes",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Text(
                        text = desc.replace(Regex("<[^>]*>"), "").trim(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    DeleteConfirmHost(
        menuOpen = false,
        target = confirmTarget,
        prompt = confirmTarget?.let {
            deletePrompt(it, episode.sourceType, episode.downloadStatus, episode.downloadedFilePath)
        },
        onConfirm = {
            val chosen = confirmTarget
            confirmTarget = null
            when (chosen) {
                DeleteTarget.DOWNLOAD -> viewModel.deleteLocalFile()
                DeleteTarget.EPISODE -> viewModel.deleteEpisode()
                null -> Unit
            }
        },
        onDismiss = { confirmTarget = null }
    )
}

@Composable
private fun EpisodeHeader(
    episode: Episode,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onListenedClick: () -> Unit,
    onDeleteLocally: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Artwork(
                url = episode.artworkUrl,
                seed = episode.author ?: episode.title,
                modifier = Modifier.size(96.dp).clip(MaterialTheme.shapes.medium)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(episode.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                episode.author?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(episode.publishedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (episode.percentListened > 0f) {
            ProgressBarSmall(percent = episode.percentListened)
            Spacer(Modifier.height(8.dp))
        }

        if (episode.sourceType == SourceType.YOUTUBE && episode.downloadStatus != DownloadStatus.DONE) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Download this video to listen offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Row 1: Play + Download/Progress
        val canPlay = when (episode.sourceType) {
            SourceType.LOCAL -> true
            SourceType.YOUTUBE -> episode.downloadStatus == DownloadStatus.DONE
            else -> episode.downloadStatus == DownloadStatus.DONE || episode.enclosureUrl != null
        }
        val showDownloadButton = episode.sourceType == SourceType.RSS || episode.sourceType == SourceType.YOUTUBE

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onPlayClick,
                enabled = canPlay,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (episode.positionMs > 0) "Resume" else "Play")
            }

            if (showDownloadButton && episode.downloadStatus != DownloadStatus.DONE) {
                DownloadProgressButton(
                    episode = episode,
                    onClick = onDownloadClick
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onListenedClick,
                modifier = Modifier.weight(1f)
            ) {
                AnimatedContent(
                    targetState = episode.isListened,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "ListenedAnim"
                ) { listened ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (listened) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (listened) "Listened" else "Mark listened", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (showDownloadButton && episode.downloadStatus == DownloadStatus.DONE) {
                OutlinedButton(
                    onClick = onDeleteLocally,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        DeleteTarget.DOWNLOAD.menuLabel,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }

            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    DeleteTarget.EPISODE.menuLabel,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun DownloadProgressButton(
    episode: Episode,
    onClick: () -> Unit
) {
    val progress = rememberDownloadProgress(
        episode.id, episode.downloadStatus, episode.sourceType
    )
    val fraction = progress.fraction

    OutlinedButton(onClick = onClick) {
        AnimatedContent(
            targetState = episode.downloadStatus,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "DetailDownload"
        ) { status ->
        when (status) {
            DownloadStatus.NONE, DownloadStatus.FAILED -> {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (status == DownloadStatus.FAILED) "Retry" else "Download",
                    style = MaterialTheme.typography.labelMedium)
            }
            DownloadStatus.QUEUED -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (progress.waitingForWifi) "Waiting for Wi-Fi" else "Queued…",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            DownloadStatus.RUNNING -> {
                if (fraction != null) {
                    CircularProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${(fraction * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Downloading…", style = MaterialTheme.typography.labelMedium)
                }
            }
            DownloadStatus.DONE -> {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
            }
        }
        }
    }
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}
