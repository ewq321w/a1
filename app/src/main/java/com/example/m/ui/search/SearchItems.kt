package com.example.m.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.example.m.data.database.DownloadStatus
import com.example.m.data.database.Song
import com.example.m.ui.common.getThumbnail
import com.example.m.ui.library.components.TranslucentDropdownMenu
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import java.text.DecimalFormat

@Composable
fun SectionHeader(
    title: String,
    showMoreButton: Boolean,
    onMoreClicked: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp), // Reduced vertical padding
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (showMoreButton) {
                TextButton(
                    onClick = onMoreClicked,
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) { Text("More") }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
fun AlbumOrPlaylistItem(
    item: PlaylistInfoItem,
    imageLoader: ImageLoader,
    onClicked: () -> Unit
) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClicked)
            .height(72.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.getThumbnail(),
            imageLoader = imageLoader,
            contentDescription = item.name,
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(3.dp)),
            contentScale = ContentScale.Crop,
            placeholder = remember { ColorPainter(placeholderColor) },
            error = remember { ColorPainter(placeholderColor) }
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name ?: "",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (item.uploaderName != null) {
                Text(
                    text = item.uploaderName!!,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ArtistListItem(
    artistResult: ArtistResult,
    imageLoader: ImageLoader,
    onArtistClicked: (ArtistResult) -> Unit
) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onArtistClicked(artistResult) }
            .height(72.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = artistResult.artistInfo.thumbnails.lastOrNull()?.url,
            imageLoader = imageLoader,
            contentDescription = artistResult.artistInfo.name,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape),
            placeholder = remember { ColorPainter(placeholderColor) },
            error = remember { ColorPainter(placeholderColor) }
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artistResult.artistInfo.name ?: "",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val subs = formatSubscriberCount(artistResult.artistInfo.subscriberCount)
            if (subs.isNotEmpty()) {
                Text(
                    text = subs,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


@Composable
fun AlbumItem(
    album: PlaylistInfoItem,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    showArtist: Boolean = true,
    itemSize: Dp = 120.dp // Default to the smaller size
) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = Modifier
            .width(itemSize)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start // Align content to the left
    ) {
        AsyncImage(
            model = album.getThumbnail(),
            imageLoader = imageLoader,
            contentDescription = album.name,
            modifier = Modifier
                .size(itemSize)
                .clip(RoundedCornerShape(3.dp)),
            contentScale = ContentScale.Crop,
            placeholder = remember { ColorPainter(placeholderColor) },
            error = remember { ColorPainter(placeholderColor) }
        )
        Text(
            text = album.name ?: "Unknown Album",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(top = 4.dp)
                .height(36.dp)
                .fillMaxWidth(), // Allow text to fill width before aligning
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start // Align text to the left
        )
        if (showArtist) {
            Text(
                text = album.uploaderName ?: "Unknown Artist",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start // Align text to the left
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchResultItem(
    result: SearchResult,
    localSong: Song?,
    isSong: Boolean,
    imageLoader: ImageLoader,
    onPlay: () -> Unit,
    onAddToLibrary: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val downloadStatus = localSong?.downloadStatus
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .height(72.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val imageModifier = if (isSong) {
            Modifier.size(54.dp).clip(RoundedCornerShape(3.dp))
        } else {
            Modifier.size(90.dp, 54.dp).clip(RoundedCornerShape(3.dp))
        }

        AsyncImage(
            model = result.streamInfo.thumbnails.lastOrNull()?.url,
            imageLoader = imageLoader,
            contentDescription = "Thumbnail for ${result.streamInfo.name}",
            modifier = imageModifier,
            contentScale = ContentScale.Crop,
            placeholder = remember { ColorPainter(placeholderColor) },
            error = remember { ColorPainter(placeholderColor) }
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
                val shouldShowStatusIcon = result.isInLibrary ||
                        downloadStatus == DownloadStatus.DOWNLOADED ||
                        downloadStatus == DownloadStatus.DOWNLOADING ||
                        downloadStatus == DownloadStatus.QUEUED ||
                        downloadStatus == DownloadStatus.FAILED

                if (shouldShowStatusIcon) {
                    Box(modifier = Modifier.width(20.dp), contentAlignment = Alignment.CenterStart) {
                        val iconSize = 16.dp
                        when (downloadStatus) {
                            DownloadStatus.DOWNLOADING -> CircularProgressIndicator(progress = { (localSong?.downloadProgress ?: 0) / 100f }, modifier = Modifier.size(iconSize), strokeWidth = 1.5.dp)
                            DownloadStatus.QUEUED -> Icon(imageVector = Icons.Default.HourglassTop, contentDescription = "Queued", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(iconSize))
                            DownloadStatus.FAILED -> Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = "Failed", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(iconSize))
                            DownloadStatus.DOWNLOADED -> Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Downloaded", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(iconSize))
                            else -> {
                                if (result.isInLibrary) Icon(imageVector = Icons.Default.Check, contentDescription = "In Library", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(iconSize))
                            }
                        }
                    }
                }
                Text(text = result.streamInfo.uploaderName ?: "Unknown Artist", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, style = MaterialTheme.typography.bodySmall, overflow = TextOverflow.Ellipsis)
                if (result.streamInfo.duration > 0) Text(text = " • ${formatDuration(result.streamInfo.duration)}", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                if (result.streamInfo.viewCount >= 0) Text(text = " • ${formatViewCount(result.streamInfo.viewCount)}", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, style = MaterialTheme.typography.bodySmall)
            }
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TranslucentDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Play next") }, onClick = { onPlayNext(); showMenu = false })
                DropdownMenuItem(text = { Text("Add to queue") }, onClick = { onAddToQueue(); showMenu = false })
                DropdownMenuItem(text = { Text(if (result.isInLibrary) "In Library" else "Add to Library") }, enabled = !result.isInLibrary, onClick = { onAddToLibrary(); showMenu = false })
                DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { onAddToPlaylist(); showMenu = false })
            }
        }
    }
}

internal fun formatDuration(totalSeconds: Long): String {
    if (totalSeconds < 0) return ""
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%d:%02d", minutes, seconds)
}

internal fun formatViewCount(count: Long): String {
    if (count < 0) return ""
    if (count < 1000) return "$count views"
    val thousands = count / 1000.0
    if (count < 1_000_000) return "${DecimalFormat("0.#").format(thousands)}K views"
    val millions = count / 1_000_000.0
    if (count < 1_000_000_000) return "${DecimalFormat("0.##").format(millions)}M views"
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