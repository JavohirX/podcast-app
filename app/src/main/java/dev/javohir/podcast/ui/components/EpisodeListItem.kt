package dev.javohir.podcast.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.javohir.podcast.data.db.entities.DownloadStatus
import dev.javohir.podcast.data.db.entities.Episode
import dev.javohir.podcast.data.db.entities.SourceType
import dev.javohir.podcast.data.download.DeleteTarget
import dev.javohir.podcast.data.download.DownloadWorker
import dev.javohir.podcast.data.download.deletePrompt
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeListItem(
    episode: Episode,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onFavouriteClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onDeleteLocally: () -> Unit = {},
    onDelete: () -> Unit = {},
    onToggleListened: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showErrorDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmTarget by remember { mutableStateOf<DeleteTarget?>(null) }

    if (showErrorDialog && episode.lastDownloadError != null) {
        val clipboardManager = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Download Error") },
            text = {
                Column {
                    Text(
                        text = episode.lastDownloadError,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(episode.lastDownloadError))
                }) {
                    Text("Copy Error")
                }
            },
            dismissButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showMenu) {
        EpisodeContextMenu(
            episode = episode,
            onDismiss = { showMenu = false },
            onViewDetails = { showMenu = false; onLongClick() },
            onDownloadClick = { showMenu = false; onDownloadClick() },
            onDeleteLocally = { showMenu = false; confirmTarget = DeleteTarget.DOWNLOAD },
            onToggleListened = { showMenu = false; onToggleListened() },
            onDelete = { showMenu = false; confirmTarget = DeleteTarget.EPISODE },
            onErrorClick = { showMenu = false; showErrorDialog = true }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Artwork(
                url = episode.artworkUrl,
                seed = episode.author ?: episode.title,
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.small)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (episode.sourceType) {
                        SourceType.LOCAL -> {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = "Local",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(2.dp))
                        }
                        SourceType.YOUTUBE -> {
                            Icon(
                                Icons.Default.SmartDisplay,
                                contentDescription = "YouTube",
                                modifier = Modifier.size(12.dp),
                                tint = androidx.compose.ui.graphics.Color(0xFFFF0000)
                            )
                            Spacer(Modifier.width(2.dp))
                        }
                        else -> Unit
                    }
                    Text(
                        text = formatDate(episode.publishedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    episode.durationMs?.let { dur ->
                        Text(
                            text = " · ${formatDuration(dur)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                if (episode.percentListened > 0f && !episode.isListened) {
                    Text(
                        text = "${(episode.percentListened * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onFavouriteClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (episode.isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favourite",
                        tint = if (episode.isFavourite) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (episode.sourceType == SourceType.RSS || episode.sourceType == SourceType.YOUTUBE) {
                    DownloadProgressRing(
                        episodeId = episode.id,
                        status = episode.downloadStatus,
                        sourceType = episode.sourceType,
                        onClick = onDownloadClick,
                        onErrorClick = { showErrorDialog = true }
                    )
                }
            }
        }

        if (episode.percentListened > 0f) {
            ProgressBarSmall(
                percent = episode.percentListened,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }

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
private fun EpisodeContextMenu(
    episode: Episode,
    onDismiss: () -> Unit,
    onViewDetails: () -> Unit,
    onDownloadClick: () -> Unit,
    onDeleteLocally: () -> Unit,
    onToggleListened: () -> Unit,
    onDelete: () -> Unit,
    onErrorClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
                MenuAction(
                    icon = Icons.Default.Info,
                    label = "View details",
                    onClick = onViewDetails
                )

                if (episode.sourceType == SourceType.RSS || episode.sourceType == SourceType.YOUTUBE) {
                    when (episode.downloadStatus) {
                        DownloadStatus.NONE -> MenuAction(
                            icon = Icons.Default.Download,
                            label = "Download",
                            onClick = onDownloadClick
                        )
                        DownloadStatus.FAILED -> MenuAction(
                            icon = Icons.Default.Download,
                            label = "Retry download",
                            onClick = onDownloadClick
                        )
                        DownloadStatus.QUEUED, DownloadStatus.RUNNING -> MenuAction(
                            icon = Icons.Default.Cancel,
                            label = "Cancel download",
                            onClick = onDownloadClick
                        )
                        DownloadStatus.DONE -> MenuAction(
                            icon = Icons.Default.PhoneAndroid,
                            label = DeleteTarget.DOWNLOAD.menuLabel,
                            onClick = onDeleteLocally
                        )
                    }
                }

                if (episode.lastDownloadError != null) {
                    MenuAction(
                        icon = Icons.Default.ErrorOutline,
                        label = "View error",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = onErrorClick
                    )
                }

                MenuAction(
                    icon = if (episode.isListened) Icons.Default.RadioButtonUnchecked
                           else Icons.Default.CheckCircle,
                    label = if (episode.isListened) "Mark as unlistened" else "Mark as listened",
                    onClick = onToggleListened
                )

                MenuAction(
                    icon = Icons.Default.Delete,
                    label = DeleteTarget.EPISODE.menuLabel,
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete
                )
            }
        }
    )
}

@Composable
private fun MenuAction(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, color = tint, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun DownloadProgressRing(
    episodeId: Long,
    status: DownloadStatus,
    sourceType: SourceType = SourceType.RSS,
    onClick: () -> Unit,
    onErrorClick: () -> Unit = {}
) {
    val progress = rememberDownloadProgress(episodeId, status, sourceType)
    val fraction = progress.fraction

    IconButton(
        onClick = { if (status != DownloadStatus.DONE) onClick() },
        modifier = Modifier.size(32.dp)
    ) {
        AnimatedContent(
            targetState = status,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith fadeOut() },
            label = "DownloadState"
        ) { target ->
        when (target) {
            DownloadStatus.NONE -> {
                val outlineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(color = outlineColor, style = Stroke(width = 1.5.dp.toPx()))
                    }
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            DownloadStatus.QUEUED -> {
                if (progress.waitingForWifi) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = "Waiting for Wi-Fi",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            DownloadStatus.RUNNING -> {
                if (fraction != null) {
                    val animatedFraction by animateFloatAsState(
                        targetValue = fraction,
                        animationSpec = tween(durationMillis = 200),
                        label = "dl_progress"
                    )
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { animatedFraction },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop",
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop",
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            DownloadStatus.DONE -> {
                val primaryColor = MaterialTheme.colorScheme.primary
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(color = primaryColor)
                    }
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Downloaded",
                        modifier = Modifier.size(13.dp),
                        tint = Color.White
                    )
                }
            }
            DownloadStatus.FAILED -> Icon(
                Icons.Default.ErrorOutline,
                contentDescription = "Retry download",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
        }
    }
}

private fun formatDate(epochMs: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(epochMs))
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
