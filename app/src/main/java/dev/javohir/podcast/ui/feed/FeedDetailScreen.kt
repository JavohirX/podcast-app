package dev.javohir.podcast.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import dev.javohir.podcast.ui.components.TrackListAnchor
import dev.javohir.podcast.ui.components.rememberAnchoredListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.javohir.podcast.ui.components.Artwork
import dev.javohir.podcast.ui.components.EpisodeListItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FeedDetailScreen(
    feedId: Long,
    onBack: () -> Unit,
    onEpisodeClick: (Long) -> Unit,
    onPlayClick: (Long, List<Long>) -> Unit,
    viewModel: FeedDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showUnsubscribeConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.feed?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    IconButton(onClick = { showUnsubscribeConfirm = true }) {
                        Icon(Icons.Default.Delete, "Unsubscribe")
                    }
                }
            )
        }
    ) { padding ->
        val rowKeys = buildList {
            if (state.feed != null) add("header")
            state.episodes.forEach { add("ep-${it.id}") }
        }
        val listState = rememberAnchoredListState("feed-$feedId")
        TrackListAnchor(listState, rowKeys, "feed-anchor-$feedId")
        LazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            // Feed header
            state.feed?.let { feed ->
                item {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Artwork(
                            url = feed.imageUrl,
                            seed = feed.title,
                            modifier = Modifier.size(80.dp).clip(MaterialTheme.shapes.medium)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(feed.title, style = MaterialTheme.typography.titleMedium)
                            feed.author?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            feed.lastFetchError?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
            items(state.episodes, key = { "ep-${it.id}" }) { episode ->
                EpisodeListItem(
                    episode = episode,
                    modifier = Modifier.animateItem(),
                    onClick = { onPlayClick(episode.id, state.episodes.map { it.id }) },
                    onLongClick = { onEpisodeClick(episode.id) },
                    onFavouriteClick = { viewModel.toggleFavourite(episode.id, episode.isFavourite) },
                    onDownloadClick = { viewModel.toggleDownload(episode.id, episode.downloadStatus) },
                    onDeleteLocally = { viewModel.deleteLocalFile(episode.id) },
                    onDelete = { viewModel.deleteEpisode(episode.id) },
                    onToggleListened = { viewModel.toggleListened(episode.id, episode.isListened) }
                )
            }
        }
    }

    if (showUnsubscribeConfirm) {
        AlertDialog(
            onDismissRequest = { showUnsubscribeConfirm = false },
            title = { Text("Unsubscribe?") },
            text = { Text("This will remove the feed and all its episodes including playback history.") },
            confirmButton = {
                TextButton(onClick = { viewModel.unsubscribe(); onBack() }) { Text("Unsubscribe") }
            },
            dismissButton = {
                TextButton(onClick = { showUnsubscribeConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
