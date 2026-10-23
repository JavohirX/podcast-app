package dev.javohir.podcast.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import dev.javohir.podcast.ui.components.TrackListAnchor
import dev.javohir.podcast.ui.components.rememberAnchoredListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import dev.javohir.podcast.data.db.entities.DownloadStatus
import dev.javohir.podcast.data.db.entities.Episode
import dev.javohir.podcast.data.db.entities.Feed
import dev.javohir.podcast.data.db.entities.WatchedFolder
import dev.javohir.podcast.data.youtube.YouTubeVideoMeta
import dev.javohir.podcast.data.download.DeleteTarget
import dev.javohir.podcast.data.download.deletePrompt
import dev.javohir.podcast.ui.components.Artwork
import dev.javohir.podcast.ui.components.DeleteConfirmHost
import dev.javohir.podcast.ui.components.DownloadProgressRing
import dev.javohir.podcast.ui.components.GenerativeArt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onFeedClick: (Long) -> Unit,
    onFolderClick: (Long) -> Unit,
    onEpisodeClick: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val addYouTubeState by viewModel.addYouTubeState.collectAsState()
    var showAddFeedDialog by remember { mutableStateOf(false) }
    var showAddYouTubeDialog by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.addFolder(it, it.lastPathSegment ?: "Folder") }
    }

    // Dismiss YouTube dialog automatically when episode is added
    LaunchedEffect(addYouTubeState) {
        if (addYouTubeState is AddYouTubeState.Added) {
            showAddYouTubeDialog = false
            viewModel.resetAddYouTubeState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(Icons.Default.Refresh, "Refresh all")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallFloatingActionButton(onClick = { folderPicker.launch(null) }) {
                    Icon(Icons.Default.FolderOpen, "Add folder")
                }
                SmallFloatingActionButton(onClick = {
                    viewModel.resetAddYouTubeState()
                    showAddYouTubeDialog = true
                }) {
                    Icon(Icons.Default.SmartDisplay, "Add YouTube video", tint = Color(0xFFFF0000))
                }
                FloatingActionButton(onClick = { showAddFeedDialog = true }) {
                    Icon(Icons.Default.RssFeed, "Add RSS feed")
                }
            }
        }
    ) { padding ->
        val rowKeys = buildList {
            if (state.feeds.isNotEmpty()) {
                add("header-feeds")
                state.feeds.forEach { add("feed-${it.id}") }
            }
            if (state.folders.isNotEmpty()) {
                add("header-folders")
                state.folders.forEach { add("folder-${it.id}") }
            }
            if (state.youTubeEpisodes.isNotEmpty()) {
                add("header-youtube")
                state.youTubeEpisodes.forEach { add("yt-${it.id}") }
            }
        }
        val listState = rememberAnchoredListState("library")
        TrackListAnchor(listState, rowKeys, "library-anchor")
        LazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            if (state.feeds.isNotEmpty()) {
                item { SectionHeader("RSS Feeds") }
                items(state.feeds, key = { "feed-${it.id}" }) { feed ->
                    Box(Modifier.animateItem()) {
                        FeedRow(feed, onClick = { onFeedClick(feed.id) })
                    }
                }
            }
            if (state.folders.isNotEmpty()) {
                item { SectionHeader("Local Folders") }
                items(state.folders, key = { "folder-${it.id}" }) { folder ->
                    Box(Modifier.animateItem()) {
                        FolderRow(folder, onClick = { onFolderClick(folder.id) })
                    }
                }
            }
            if (state.youTubeEpisodes.isNotEmpty()) {
                item { SectionHeader("YouTube Videos") }
                items(state.youTubeEpisodes, key = { "yt-${it.id}" }) { episode ->
                    Box(Modifier.animateItem()) {
                    YouTubeEpisodeRow(
                        episode = episode,
                        onClick = { onEpisodeClick(episode.id) },
                        onDownloadClick = { viewModel.toggleDownload(episode.id, episode.downloadStatus) },
                        onDeleteLocally = { viewModel.deleteLocalFile(episode.id) },
                        onToggleListened = { viewModel.toggleListened(episode.id, episode.isListened) },
                        onDelete = { viewModel.deleteEpisode(episode.id) }
                    )
                    }
                }
            }
            if (state.feeds.isEmpty() && state.folders.isEmpty() && state.youTubeEpisodes.isEmpty()) {
                item {
                    Box(
                        Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Add an RSS feed, a local folder, or a YouTube video",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showAddFeedDialog) {
        AddFeedDialog(
            onAdd = { url ->
                viewModel.addFeed(url)
                showAddFeedDialog = false
            },
            onDismiss = { showAddFeedDialog = false }
        )
    }

    if (showAddYouTubeDialog) {
        AddYouTubeDialog(
            state = addYouTubeState,
            onFetch = { url -> viewModel.fetchYouTubeMetadata(url) },
            onConfirm = { meta -> viewModel.confirmAddYouTube(meta) },
            onDismiss = {
                showAddYouTubeDialog = false
                viewModel.resetAddYouTubeState()
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun FeedRow(feed: Feed, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(feed.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { feed.author?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingContent = {
            Artwork(
                url = feed.imageUrl,
                seed = feed.title,
                modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small)
            )
        },
        trailingContent = {
            feed.lastFetchError?.let {
                Icon(Icons.Default.ErrorOutline, "Error", tint = MaterialTheme.colorScheme.error)
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}

@Composable
private fun FolderRow(folder: WatchedFolder, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(folder.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            GenerativeArt(
                seed = folder.displayName,
                modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small)
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun YouTubeEpisodeRow(
    episode: Episode,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDeleteLocally: () -> Unit,
    onToggleListened: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var confirmTarget by remember { mutableStateOf<DeleteTarget?>(null) }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMenu = false }) { Text("Cancel") }
            },
            title = {
                Text(
                    episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column {
                    when (episode.downloadStatus) {
                        DownloadStatus.NONE,
                        DownloadStatus.FAILED -> {
                            TextButton(
                                onClick = { showMenu = false; onDownloadClick() },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Download, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text("Download", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        DownloadStatus.QUEUED,
                        DownloadStatus.RUNNING -> {
                            TextButton(
                                onClick = { showMenu = false; onDownloadClick() },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Cancel, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text("Cancel download", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        DownloadStatus.DONE -> {
                            TextButton(
                                onClick = { showMenu = false; confirmTarget = DeleteTarget.DOWNLOAD },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(DeleteTarget.DOWNLOAD.menuLabel, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { showMenu = false; onToggleListened() },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (episode.isListened) Icons.Default.RadioButtonUnchecked
                                else Icons.Default.CheckCircle,
                                null, modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (episode.isListened) "Mark as unlistened" else "Mark as listened",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    TextButton(
                        onClick = { showMenu = false; confirmTarget = DeleteTarget.EPISODE },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Delete, null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                DeleteTarget.EPISODE.menuLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        )
    }

    ListItem(
        headlineContent = { Text(episode.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            episode.author?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = {
            Box(modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small)) {
                if (episode.artworkUrl != null) {
                    AsyncImage(
                        model = episode.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Artwork(
                        url = null,
                        seed = episode.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        },
        trailingContent = {
            DownloadProgressRing(
                episodeId = episode.id,
                status = episode.downloadStatus,
                sourceType = episode.sourceType,
                onClick = onDownloadClick
            )
        },
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { showMenu = true }
        )
    )
    HorizontalDivider()
    DeleteConfirmHost(
        menuOpen = showMenu,
        target = confirmTarget,
        prompt = confirmTarget?.let {
            deletePrompt(it, episode.sourceType, episode.downloadStatus, episode.downloadedFilePath)
        },
        onConfirm = {
            val chosen = confirmTarget
            confirmTarget = null
            when (chosen) {
                DeleteTarget.DOWNLOAD -> onDeleteLocally()
                DeleteTarget.EPISODE -> onDelete()
                null -> Unit
            }
        },
        onDismiss = { confirmTarget = null }
    )
}

@Composable
private fun AddFeedDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add RSS Feed") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Feed URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (url.isNotBlank()) onAdd(url.trim()) },
                enabled = url.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddYouTubeDialog(
    state: AddYouTubeState,
    onFetch: (String) -> Unit,
    onConfirm: (YouTubeVideoMeta) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add YouTube Video") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("YouTube URL") },
                    placeholder = { Text("youtube.com/watch?v=…", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    enabled = state !is AddYouTubeState.Loading && state !is AddYouTubeState.Preview,
                    modifier = Modifier.fillMaxWidth()
                )

                when (state) {
                    is AddYouTubeState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Fetching info…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is AddYouTubeState.Preview -> {
                        YouTubePreviewCard(meta = state.meta)
                    }
                    is AddYouTubeState.Error -> {
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (state) {
                is AddYouTubeState.Preview -> {
                    TextButton(onClick = { onConfirm(state.meta) }) { Text("Add to Library") }
                }
                is AddYouTubeState.Loading -> {
                    TextButton(onClick = {}, enabled = false) { Text("Add to Library") }
                }
                else -> {
                    TextButton(
                        onClick = { if (url.isNotBlank()) onFetch(url.trim()) },
                        enabled = url.isNotBlank()
                    ) { Text("Fetch Info") }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun YouTubePreviewCard(meta: YouTubeVideoMeta) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = meta.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 80.dp, height = 56.dp)
                .clip(MaterialTheme.shapes.extraSmall)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = meta.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = meta.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
