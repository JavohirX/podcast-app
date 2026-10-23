package dev.javohir.podcast.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import dev.javohir.podcast.ui.components.rememberAnchoredListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    onBack: () -> Unit,
    viewModel: StorageViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberAnchoredListState("storage")
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(state = listState, contentPadding = padding) {
            item {
                ListItem(
                    headlineContent = { Text("Delete all listened downloads") },
                    trailingContent = {
                        FilledTonalButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                )
                HorizontalDivider()
            }
            items(state.feedStats, key = { "feed-${it.first.id}" }) { (feed, bytes) ->
                ListItem(
                    headlineContent = { Text(feed.title) },
                    supportingContent = { Text(formatBytes(bytes)) }
                )
                HorizontalDivider()
            }
            item(key = "youtube") {
                ListItem(
                    headlineContent = { Text("YouTube") },
                    supportingContent = { Text(formatBytes(state.youtubeBytes)) }
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete listened downloads?") },
            text = { Text("Downloaded files for episodes you have finished will be removed. Progress stays.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteAllListened()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
    bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024f * 1024f))
    else -> "%.2f GB".format(bytes / (1024f * 1024f * 1024f))
}
