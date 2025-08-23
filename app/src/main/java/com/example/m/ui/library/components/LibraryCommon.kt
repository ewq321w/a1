package com.example.m.ui.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

@Composable
fun SongItem(
    song: Song,
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
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.localFilePath != null) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp)
                    )
                } else if (song.isInLibrary) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "In Library",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp)
                    )
                }
                Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = "Album art for ${song.title}",
                modifier = Modifier.size(50.dp),
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
                        DropdownMenuItem(
                            text = { Text(if (song.localFilePath != null) "Downloaded" else "Download") },
                            enabled = song.localFilePath == null,
                            onClick = { it(); showMenu = false }
                        )
                    }
                    DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { onAddToPlaylistClick(); showMenu = false })
                    onRemoveFromPlaylistClick?.let {
                        DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = { it(); showMenu = false })
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
        modifier = modifier.clickable(onClick = onClick)
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
        Text(text = message, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
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

@Composable
fun FolderWithThumbnails(
    urls: List<String>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = "Artist Group",
            modifier = Modifier.fillMaxSize(),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )

        val gridModifier = Modifier
            .fillMaxSize(0.65f)
            .padding(top = 4.dp)

        if (urls.isNotEmpty()) {
            Column(
                modifier = gridModifier,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ThumbnailCell(url = urls.getOrNull(0), modifier = Modifier.weight(1f))
                    ThumbnailCell(url = urls.getOrNull(1), modifier = Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ThumbnailCell(url = urls.getOrNull(2), modifier = Modifier.weight(1f))
                    ThumbnailCell(url = urls.getOrNull(3), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ThumbnailCell(url: String?, modifier: Modifier) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = modifier
                .fillMaxSize()
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            error = painterResource(id = R.drawable.placeholder_gray)
        )
    } else {
        Spacer(modifier = modifier.fillMaxSize())
    }
}