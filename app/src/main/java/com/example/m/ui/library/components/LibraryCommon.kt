package com.example.m.ui.library.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.m.R
import com.example.m.data.database.Song
import com.example.m.managers.DownloadStatus
import java.text.DecimalFormat

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
    downloadStatus: DownloadStatus?,
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
    onDeleteFromHistoryClick: (() -> Unit)? = null,
    onAddToGroupClick: (() -> Unit)? = null,
    onRemoveFromGroupClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val isDownloading = downloadStatus is DownloadStatus.Downloading || downloadStatus is DownloadStatus.Queued

    val displayTitle = remember(song.title) {
        song.title.replace(" (Remastered)", "")
    }

    ListItem(
        headlineContent = {
            Text(
                displayTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val showStatusIcon = downloadStatus != null || song.localFilePath != null || song.isInLibrary

                if (showStatusIcon) {
                    Box(
                        modifier = Modifier.width(20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val iconSize = 16.dp
                        when (downloadStatus) {
                            is DownloadStatus.Downloading -> {
                                CircularProgressIndicator(
                                    progress = { downloadStatus.progress / 100f },
                                    modifier = Modifier.size(iconSize),
                                    strokeWidth = 1.5.dp
                                )
                            }
                            is DownloadStatus.Queued -> {
                                Icon(
                                    imageVector = Icons.Default.HourglassTop,
                                    contentDescription = "Queued",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                            is DownloadStatus.Failed -> {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Failed",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                            null -> {
                                if (song.localFilePath != null) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Downloaded",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(iconSize)
                                    )
                                } else if (song.isInLibrary) {
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
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        leadingContent = {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = "Album art for ${song.title}",
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(3.dp)),
                contentScale = ContentScale.Crop,
                error = painterResource(id = R.drawable.placeholder_gray),
                placeholder = painterResource(id = R.drawable.placeholder_gray)
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
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
                    onDownloadClick?.let {
                        val downloadText = when {
                            isDownloading -> "Downloading..."
                            song.localFilePath != null -> "Downloaded"
                            else -> "Download"
                        }
                        DropdownMenuItem(
                            text = { Text(downloadText) },
                            enabled = !isDownloading && song.localFilePath == null,
                            onClick = { it(); showMenu = false }
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
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = MaterialTheme.colorScheme.onSurface,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
            trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 72.dp)
    )
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
            error = painterResource(id = R.drawable.placeholder_gray),
            placeholder = painterResource(id = R.drawable.placeholder_gray)
        )
    } else {
        Column(modifier = modifier) {
            Row(modifier = Modifier.weight(1f)) {
                AsyncImage(
                    model = currentUrls.getOrNull(0),
                    contentDescription = contentDescription,
                    modifier = Modifier.weight(1f),
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = R.drawable.placeholder_gray),
                    placeholder = painterResource(id = R.drawable.placeholder_gray)
                )
                AsyncImage(
                    model = currentUrls.getOrNull(1),
                    contentDescription = contentDescription,
                    modifier = Modifier.weight(1f),
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = R.drawable.placeholder_gray),
                    placeholder = painterResource(id = R.drawable.placeholder_gray)
                )
            }
            Row(modifier = Modifier.weight(1f)) {
                AsyncImage(
                    model = currentUrls.getOrNull(2),
                    contentDescription = contentDescription,
                    modifier = Modifier.weight(1f),
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = R.drawable.placeholder_gray),
                    placeholder = painterResource(id = R.drawable.placeholder_gray)
                )
                AsyncImage(
                    model = currentUrls.getOrNull(3),
                    contentDescription = contentDescription,
                    modifier = Modifier.weight(1f),
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = R.drawable.placeholder_gray),
                    placeholder = painterResource(id = R.drawable.placeholder_gray)
                )
            }
        }
    }
}