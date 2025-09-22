// file: com/example/m/ui/library/components/LibraryCommon.kt
package com.example.m.ui.library.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.example.m.data.database.DownloadStatus
import com.example.m.data.database.Song
import java.text.DecimalFormat
import androidx.compose.ui.text.font.FontWeight

internal fun formatDuration(totalSeconds: Long): String {
    if (totalSeconds < 0) return ""
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

internal fun formatPlayCount(count: Int): String {
    if (count < 0) return ""
    if (count == 1) return "1 play"
    if (count < 1000) return "$count plays"
    val thousands = count / 1000.0
    return "${DecimalFormat("0.#").format(thousands)}K plays"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onPlayNextClick: () -> Unit,
    onAddToQueueClick: () -> Unit,
    onGoToArtistClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    onRemoveFromPlaylistClick: (() -> Unit)? = null,
    onAddToLibraryClick: (() -> Unit)? = null,
    onDownloadClick: (() -> Unit)? = null,
    onDeleteDownloadClick: (() -> Unit)? = null,
    onDeleteFromHistoryClick: (() -> Unit)? = null,
    onAddToGroupClick: (() -> Unit)? = null,
    onRemoveFromGroupClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val isDownloading = song.downloadStatus == DownloadStatus.DOWNLOADING || song.downloadStatus == DownloadStatus.QUEUED
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant

    if (showDeleteConfirmDialog) {
        ConfirmDeleteDialog(
            itemType = "download for",
            itemName = song.title,
            onDismiss = { showDeleteConfirmDialog = false },
            onConfirm = {
                onDeleteDownloadClick?.invoke()
                showDeleteConfirmDialog = false
            }
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isPlaying) Color.White.copy(alpha = 0.075f) else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 68.dp) // Use minimum height instead of fixed height
            .padding(start = 18.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically // Change back to CenterVertically for proper alignment
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = "Album art for ${song.title}",
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(3.dp)),
            contentScale = ContentScale.Crop,
            placeholder = remember { ColorPainter(placeholderColor) },
            error = remember { ColorPainter(placeholderColor) }
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center // Center the text content vertically
        ) {
            Text(
                song.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.downloadStatus != DownloadStatus.NOT_DOWNLOADED || song.isInLibrary) {
                    Box(
                        modifier = Modifier.width(18.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val iconSize = 14.dp
                        when {
                            song.downloadStatus == DownloadStatus.DOWNLOADING -> {
                                CircularProgressIndicator(
                                    progress = { song.downloadProgress / 100f },
                                    modifier = Modifier.size(iconSize),
                                    strokeWidth = 1.5.dp
                                )
                            }
                            song.downloadStatus == DownloadStatus.QUEUED -> {
                                Icon(
                                    imageVector = Icons.Default.HourglassTop,
                                    contentDescription = "Queued",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                            song.downloadStatus == DownloadStatus.FAILED -> {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Failed",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                            song.downloadStatus == DownloadStatus.DOWNLOADED -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                            song.isInLibrary -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "In Library",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                        }
                    }
                }

                var supportText = song.artist
                if (song.duration > 0) {
                    supportText += " • ${formatDuration(song.duration)}"
                }
                if (song.playCount > 0) {
                    supportText += " • ${formatPlayCount(song.playCount)}"
                }
                Text(
                    supportText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(
            onClick = { showMenu = true },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "More options",
                modifier = Modifier.size(20.dp)
            )
        }
        TranslucentDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("Play next") }, onClick = { onPlayNextClick(); showMenu = false })
            DropdownMenuItem(text = { Text("Add to queue") }, onClick = { onAddToQueueClick(); showMenu = false })
            DropdownMenuItem(text = { Text("Shuffle") }, onClick = { onShuffleClick(); showMenu = false })
            DropdownMenuItem(text = { Text("Go to artist") }, onClick = { onGoToArtistClick(); showMenu = false })
            HorizontalDivider()
            onAddToLibraryClick?.let {
                DropdownMenuItem(
                    text = { Text(if (song.isInLibrary) "In Library" else "Add to Library") },
                    enabled = !song.isInLibrary,
                    onClick = { it(); showMenu = false }
                )
            }
            onDownloadClick?.let { downloadAction ->
                val isDownloaded = song.downloadStatus == DownloadStatus.DOWNLOADED
                val downloadText = when {
                    isDownloading -> "Downloading..."
                    isDownloaded -> "Delete download"
                    else -> "Download"
                }
                DropdownMenuItem(
                    text = { Text(downloadText) },
                    enabled = !isDownloading,
                    onClick = {
                        if (isDownloaded) {
                            showDeleteConfirmDialog = true
                    } else {
                            downloadAction()
                        }
                        showMenu = false
                    }
                )
            }
            DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { onAddToPlaylistClick(); showMenu = false })
            onAddToGroupClick?.let {
                DropdownMenuItem(text = { Text("Add to group") }, onClick = { it(); showMenu = false })
            }
            onRemoveFromPlaylistClick?.let {
                DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = { it(); showMenu = false })
            }
            onRemoveFromGroupClick?.let {
                DropdownMenuItem(text = { Text("Remove from group") }, onClick = { it(); showMenu = false })
            }
            onDeleteFromHistoryClick?.let {
                DropdownMenuItem(text = { Text("Delete from history") }, onClick = { it(); showMenu = false })
            }
            onDeleteClick?.let {
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Delete from device") }, onClick = { it(); showMenu = false })
            }
        }
    }
}

@Composable
fun EmptyStateMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
    }
}

@Composable
fun CompositeThumbnailImage(
    urls: List<String>,
    contentDescription: String,
    processUrls: suspend (List<String>) -> List<String>,
    modifier: Modifier = Modifier
) {
    var finalUrls by remember { mutableStateOf<List<String>?>(null) }
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant

    LaunchedEffect(key1 = urls) {
        finalUrls = processUrls(urls)
    }

    val currentUrls = finalUrls

    if (currentUrls == null) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    } else if (currentUrls.isEmpty()) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.primary
        )
    } else if (currentUrls.size <= 2) {
        AsyncImage(
            model = currentUrls.firstOrNull(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            placeholder = remember { ColorPainter(placeholderColor) },
            error = remember { ColorPainter(placeholderColor) }
        )
    } else {
        Column(modifier = modifier) {
            Row(modifier = Modifier.weight(1f)) {
                AsyncImage(
                    model = currentUrls.getOrNull(0),
                    contentDescription = contentDescription,
                    modifier = Modifier.weight(1f),
                    contentScale = ContentScale.Crop,
                    placeholder = remember { ColorPainter(placeholderColor) },
                    error = remember { ColorPainter(placeholderColor) }
                )
                AsyncImage(
                    model = currentUrls.getOrNull(1),
                    contentDescription = contentDescription,
                    modifier = Modifier.weight(1f),
                    contentScale = ContentScale.Crop,
                    placeholder = remember { ColorPainter(placeholderColor) },
                    error = remember { ColorPainter(placeholderColor) }
                )
            }
            Row(modifier = Modifier.weight(1f)) {
                AsyncImage(
                    model = currentUrls.getOrNull(2),
                    contentDescription = contentDescription,
                    modifier = Modifier.weight(1f),
                    contentScale = ContentScale.Crop,
                    placeholder = remember { ColorPainter(placeholderColor) },
                    error = remember { ColorPainter(placeholderColor) }
                )
                AsyncImage(
                    model = currentUrls.getOrNull(3),
                    contentDescription = contentDescription,
                    modifier = Modifier.weight(1f),
                    contentScale = ContentScale.Crop,
                    placeholder = remember { ColorPainter(placeholderColor) },
                    error = remember { ColorPainter(placeholderColor) }
                )
            }
        }
    }
}

@Composable
fun TranslucentDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: androidx.compose.ui.unit.DpOffset = androidx.compose.ui.unit.DpOffset(0.dp, 0.dp),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit
) {
    // This theme makes the DropdownMenu's default surface have a "very dark translucent" tint.
    val newColorScheme = MaterialTheme.colorScheme.copy(
        surface = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        onSurface = Color.White,
        onSurfaceVariant = Color.White.copy(alpha = 0.9f)
    )

    MaterialTheme(colorScheme = newColorScheme) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            offset = offset,
            properties = properties,
            content = content
        )
    }
}
