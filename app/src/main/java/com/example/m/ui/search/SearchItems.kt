package com.example.m.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.example.m.R
import com.example.m.managers.DownloadStatus
import com.example.m.ui.common.getThumbnail
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import java.text.DecimalFormat

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumItem(
    album: PlaylistInfoItem,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    showArtist: Boolean = true
) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = album.getThumbnail(),
            imageLoader = imageLoader,
            contentDescription = album.name,
            modifier = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(3.dp)),
            contentScale = ContentScale.Crop,
            error = painterResource(id = R.drawable.placeholder_gray),
            placeholder = painterResource(id = R.drawable.placeholder_gray)
        )
        Text(
            text = album.name ?: "Unknown Album",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 4.dp)
                .height(36.dp), // Set a fixed height to prevent grid resizing
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (showArtist) {
            Text(
                text = album.uploaderName ?: "Unknown Artist",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchResultItem(
    result: SearchResult,
    downloadStatus: DownloadStatus?,
    isSong: Boolean,
    imageLoader: ImageLoader,
    onPlay: () -> Unit,
    onAddToLibrary: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val showStatusIcon = downloadStatus != null || result.isDownloaded || result.isInLibrary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .height(72.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val imageModifier = if (isSong) {
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(3.dp))
        } else {
            Modifier
                .size(90.dp, 54.dp)
                .clip(RoundedCornerShape(3.dp))
        }

        AsyncImage(
            model = result.streamInfo.thumbnails.lastOrNull()?.url,
            imageLoader = imageLoader,
            contentDescription = "Thumbnail for ${result.streamInfo.name}",
            modifier = imageModifier,
            contentScale = ContentScale.Crop,
            error = painterResource(id = R.drawable.placeholder_gray),
            placeholder = painterResource(id = R.drawable.placeholder_gray)
        )
        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.streamInfo.name ?: "No Title",
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showStatusIcon) {
                    Box(
                        modifier = Modifier.width(20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val iconSize = 16.dp
                        when (downloadStatus) {
                            is DownloadStatus.Downloading -> CircularProgressIndicator(
                                progress = { downloadStatus.progress / 100f },
                                modifier = Modifier.size(iconSize),
                                strokeWidth = 1.5.dp
                            )
                            is DownloadStatus.Queued -> Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = "Queued",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(iconSize)
                            )
                            is DownloadStatus.Failed -> Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Failed",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(iconSize)
                            )
                            null -> {
                                if (result.isDownloaded) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Downloaded",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(iconSize)
                                    )
                                } else if (result.isInLibrary) {
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
                Text(
                    text = result.streamInfo.uploaderName ?: "Unknown Artist",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    overflow = TextOverflow.Ellipsis
                )
                if (result.streamInfo.duration > 0) {
                    Text(
                        text = " • ${formatDuration(result.streamInfo.duration)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (result.streamInfo.viewCount >= 0) {
                    Text(
                        text = " • ${formatViewCount(result.streamInfo.viewCount)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Play next") },
                    onClick = {
                        onPlayNext()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    onClick = {
                        onAddToQueue()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (result.isInLibrary) "In Library" else "Add to Library") },
                    enabled = !result.isInLibrary,
                    onClick = {
                        onAddToLibrary()
                        showMenu = false
                    }
                )
            }
        }
    }
}

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

internal fun formatViewCount(count: Long): String {
    if (count < 0) return ""
    if (count < 1000) return "$count views"
    val thousands = count / 1000.0
    if (count < 1_000_000) {
        return "${DecimalFormat("0.#").format(thousands)}K views"
    }
    val millions = count / 1_000_000.0
    if (count < 1_000_000_000) {
        return "${DecimalFormat("0.##").format(millions)}M views"
    }
    val billions = count / 1_000_000_000.0
    return "${DecimalFormat("0.###").format(billions)}B views"
}

internal fun formatSubscriberCount(count: Long): String {
    if (count < 0) return ""
    if (count < 1000) return "$count subscribers"
    if (count < 1_000_000) {
        val thousands = count / 1000.0
        return "${DecimalFormat("0.#").format(thousands)}K subscribers"
    }
    val millions = count / 1_000_000.0
    return "${DecimalFormat("0.##").format(millions)}M subscribers"
}