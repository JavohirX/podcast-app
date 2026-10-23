package dev.javohir.podcast.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.javohir.podcast.ui.components.EpisodeListItem
import dev.javohir.podcast.ui.components.TrackListAnchor
import dev.javohir.podcast.ui.components.rememberAnchoredListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    onEpisodeClick: (Long) -> Unit,
    onPlayClick: (Long, List<Long>) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val listState = rememberAnchoredListState("search-$query")
    TrackListAnchor(listState, results.map { "ep-${it.id}" }, "search-anchor-$query")

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Search") })

        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = viewModel::onQueryChange,
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    placeholder = { Text("Search episodes, authors, feeds…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) }
                )
            },
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {}

        if (query.isNotBlank() && results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No results", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(results, key = { "ep-${it.id}" }) { episode ->
                    EpisodeListItem(
                        episode = episode,
                        modifier = Modifier.animateItem(),
                        onClick = { onPlayClick(episode.id, results.map { it.id }) },
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
    }
}
