package dev.javohir.podcast.ui.folder

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.javohir.podcast.ui.components.EpisodeListItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FolderDetailScreen(
    folderId: Long,
    onBack: () -> Unit,
    onEpisodeClick: (Long) -> Unit,
    onPlayClick: (Long, List<Long>) -> Unit,
    viewModel: FolderDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showRemoveConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.folder?.displayName ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (state.isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { viewModel.rescan() }) {
                            Icon(Icons.Default.Refresh, "Rescan")
                        }
                    }
                    IconButton(onClick = { showRemoveConfirm = true }) {
                        Icon(Icons.Default.Delete, "Remove folder")
                    }
                }
            )
        }
    ) { padding ->
        val rowKeys = buildList {
            add("count")
            state.episodes.forEach { add("ep-${it.id}") }
        }
        val listState = rememberAnchoredListState("folder-$folderId")
        TrackListAnchor(listState, rowKeys, "folder-anchor-$folderId")
        LazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    "${state.episodes.size} files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(state.episodes, key = { "ep-${it.id}" }) { episode ->
                EpisodeListItem(
                    episode = episode,
                    modifier = Modifier.animateItem(),
                    onClick = { onPlayClick(episode.id, state.episodes.map { it.id }) },
                    onLongClick = { onEpisodeClick(episode.id) },
                    onFavouriteClick = { viewModel.toggleFavourite(episode.id, episode.isFavourite) },
                    onDelete = { viewModel.deleteEpisode(episode.id) },
                    onToggleListened = { viewModel.toggleListened(episode.id, episode.isListened) }
                )
            }
        }
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove folder?") },
            text = { Text("This will remove the folder and all its episodes including playback history.") },
            confirmButton = {
                TextButton(onClick = { viewModel.removeFolder(); onBack() }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
