package dev.javohir.podcast.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

@Composable
fun rememberAnchoredListState(bucket: String): LazyListState =
    rememberSaveable(bucket, saver = LazyListState.Saver) { LazyListState() }

/**
 * Restores the row that was on screen, not only a raw index, so inserts above the fold
 * do not land the user on a different item. The correction runs once each time this
 * composable enters composition.
 */
@Composable
fun TrackListAnchor(
    listState: LazyListState,
    keys: List<String>,
    bucket: String
) {
    var anchor by rememberSaveable(bucket) { mutableStateOf<String?>(null) }
    var applied by remember(bucket) { mutableStateOf(false) }
    val keysState by rememberUpdatedState(keys)

    LaunchedEffect(keys, bucket) {
        if (applied || keys.isEmpty()) return@LaunchedEffect
        val saved = anchor
        val index = if (saved != null) keys.indexOf(saved) else -1
        if (index >= 0 && index != listState.firstVisibleItemIndex) {
            listState.scrollToItem(index)
        }
        applied = true
    }

    LaunchedEffect(listState, bucket) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                if (!applied) return@collect
                anchor = keysState.getOrNull(index)
            }
    }
}

