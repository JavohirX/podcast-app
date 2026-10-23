package dev.javohir.podcast.ui.player

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.javohir.podcast.ui.components.Artwork
import dev.javohir.podcast.ui.components.SpeedSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSleepSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.5f))

            // Artwork
            Artwork(
                url = state.episode?.artworkUrl,
                seed = state.episode?.let { it.author ?: it.title } ?: "",
                modifier = Modifier
                    .size(260.dp)
                    .clip(MaterialTheme.shapes.large)
            )

            Spacer(Modifier.height(24.dp))

            // Title + favourite
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.episode?.title ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    state.episode?.author?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { viewModel.toggleFavourite() }) {
                    AnimatedContent(
                        targetState = state.episode?.isFavourite == true,
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

            Spacer(Modifier.height(16.dp))

            // Scrubber
            val duration = state.durationMs.coerceAtLeast(1L)
            Slider(
                value = state.positionMs.toFloat(),
                onValueChange = { viewModel.seekTo(it.toLong()) },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatMs(state.positionMs), style = MaterialTheme.typography.labelSmall)
                Text("-${formatMs(state.durationMs - state.positionMs)}", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(16.dp))

            // Transport controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.seekBack() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.Replay10, "Seek back 10s", modifier = Modifier.size(32.dp))
                }
                FilledIconButton(onClick = { viewModel.playPause() }, modifier = Modifier.size(64.dp)) {
                    AnimatedContent(
                        targetState = state.isPlaying,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "PlayPauseAnimation"
                    ) { isPlaying ->
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            "Play/Pause", modifier = Modifier.size(36.dp)
                        )
                    }
                }
                IconButton(onClick = { viewModel.seekForward() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.Forward30, "Seek forward 30s", modifier = Modifier.size(32.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Secondary controls: speed, sleep timer, bookmark
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Speed chip
                FilterChip(
                    selected = state.speed != 1.0f,
                    onClick = { showSpeedSheet = true },
                    label = { Text("%.1f×".format(state.speed)) },
                    leadingIcon = { Icon(Icons.Default.Speed, null, modifier = Modifier.size(16.dp)) }
                )
                // Sleep timer
                IconButton(onClick = { showSleepSheet = true }) {
                    Icon(Icons.Default.Bedtime, "Sleep timer")
                }
                // Bookmark
                IconButton(onClick = { viewModel.addBookmark(null) }) {
                    Icon(Icons.Default.Bookmark, "Bookmark")
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }

    if (showSpeedSheet) {
        SpeedSheet(
            currentSpeed = state.speed,
            onSpeedChange = { speed ->
                viewModel.setSpeed(speed)
            },
            onDismiss = { showSpeedSheet = false }
        )
    }

    if (showSleepSheet) {
        SleepTimerSheet(
            onSelect = { ms ->
                viewModel.setSleepTimer(ms)
                showSleepSheet = false
            },
            onDismiss = { showSleepSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheet(
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        "10 min" to 10 * 60_000L,
        "20 min" to 20 * 60_000L,
        "30 min" to 30 * 60_000L,
        "45 min" to 45 * 60_000L,
        "60 min" to 60 * 60_000L
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Sleep Timer", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
            options.forEach { (label, ms) ->
                ListItem(
                    headlineContent = { Text(label) },
                    modifier = Modifier.clickableListItem { onSelect(ms) }
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun Modifier.clickableListItem(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
