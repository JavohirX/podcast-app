package dev.javohir.podcast.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import dev.javohir.podcast.data.db.entities.Episode
import dev.javohir.podcast.data.db.entities.SourceType
import dev.javohir.podcast.data.db.entities.DownloadStatus
import dev.javohir.podcast.ui.components.EpisodeListItem
import dev.javohir.podcast.ui.components.TrackListAnchor
import dev.javohir.podcast.ui.components.rememberAnchoredListState

private val TABS = listOf("New", "Left off", "Favourites", "Listened")
private val EMPTY_MESSAGES = listOf(
    "Add a feed or folder to get started",
    "Start listening and your progress will appear here",
    "Heart an episode to add it here",
    "Finished episodes will appear here"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onEpisodeClick: (Long) -> Unit,
    onPlayClick: (Long, List<Long>) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(pageCount = { TABS.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Podcast") })

        ScrollableTabRow(selectedTabIndex = pagerState.currentPage) {
            TABS.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(title) }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            val episodes = when (page) {
                0 -> state.newEpisodes
                1 -> state.leftOffEpisodes
                2 -> state.favourites
                3 -> state.listened
                else -> emptyList()
            }
            if (episodes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = EMPTY_MESSAGES[page],
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                EpisodePage(
                    page = page,
                    episodes = episodes,
                    onEpisodeClick = onEpisodeClick,
                    onPlayClick = { ep -> onPlayClick(ep.id, episodes.map { it.id }) },
                    onFavouriteClick = { ep -> viewModel.toggleFavourite(ep.id, ep.isFavourite) },
                    onDownloadClick = { ep -> viewModel.toggleDownload(ep.id, ep.downloadStatus) },
                    onDeleteLocally = { ep -> viewModel.deleteLocalFile(ep.id) },
                    onDelete = { ep -> viewModel.deleteEpisode(ep.id) },
                    onToggleListened = { ep -> viewModel.toggleListened(ep.id, ep.isListened) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodePage(
    page: Int,
    episodes: List<Episode>,
    onEpisodeClick: (Long) -> Unit,
    onPlayClick: (Episode) -> Unit,
    onFavouriteClick: (Episode) -> Unit,
    onDownloadClick: (Episode) -> Unit,
    onDeleteLocally: (Episode) -> Unit,
    onDelete: (Episode) -> Unit,
    onToggleListened: (Episode) -> Unit
) {
    val listState = rememberAnchoredListState("home-$page")
    TrackListAnchor(listState, episodes.map { "ep-${it.id}" }, "home-anchor-$page")
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(episodes, key = { "ep-${it.id}" }) { episode ->
            EpisodeListItem(
                episode = episode,
                modifier = Modifier.animateItem(),
                onClick = {
                    if (episode.sourceType == SourceType.YOUTUBE &&
                        episode.downloadStatus != DownloadStatus.DONE
                    ) {
                        onEpisodeClick(episode.id)
                    } else {
                        onPlayClick(episode)
                    }
                },
                onLongClick = { onEpisodeClick(episode.id) },
                onFavouriteClick = { onFavouriteClick(episode) },
                onDownloadClick = { onDownloadClick(episode) },
                onDeleteLocally = { onDeleteLocally(episode) },
                onDelete = { onDelete(episode) },
                onToggleListened = { onToggleListened(episode) }
            )
        }
    }
}
