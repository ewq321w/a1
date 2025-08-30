package com.example.m.ui.library.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.m.data.database.Artist
import com.example.m.data.database.ArtistGroup
import com.example.m.data.database.ArtistWithSongs
import com.example.m.ui.library.DeletableItem
import com.example.m.ui.library.LibraryArtistItem
import com.example.m.ui.library.LibraryViewModel
import com.example.m.ui.library.components.CompositeThumbnailImage
import com.example.m.ui.library.components.EmptyStateMessage

@Composable
fun ArtistsTabContent(
    artists: List<LibraryArtistItem>,
    onArtistClick: (Long) -> Unit,
    onGoToArtistGroup: (Long) -> Unit,
    onEditArtistSongs: (Long) -> Unit,
    viewModel: LibraryViewModel
) {
    if (artists.isEmpty()) {
        EmptyStateMessage(message = "Your artists and groups will appear here.")
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(
                items = artists,
                key = { item ->
                    when (item) {
                        is LibraryArtistItem.ArtistItem -> "artist-${item.artistWithSongs.artist.artistId}"
                        is LibraryArtistItem.GroupItem -> "group-${item.group.groupId}"
                    }
                },
                contentType = { item ->
                    item::class.java.simpleName
                }
            ) { libraryItem ->
                when (libraryItem) {
                    is LibraryArtistItem.ArtistItem -> {
                        val artistWithSongs = libraryItem.artistWithSongs
                        ArtistItem(
                            artistWithSongs = artistWithSongs,
                            onClick = { onArtistClick(artistWithSongs.artist.artistId) },
                            onPlay = { viewModel.playArtist(artistWithSongs.artist) },
                            onShuffle = { viewModel.shuffleArtist(artistWithSongs.artist) },
                            onEdit = { onEditArtistSongs(artistWithSongs.artist.artistId) },
                            onToggleAutoDownload = { viewModel.toggleAutoDownloadForArtist(artistWithSongs.artist) },
                            groupAction = "Move to group..." to { viewModel.prepareToMoveArtist(artistWithSongs.artist) },
                            onHideArtist = { viewModel.hideArtist(artistWithSongs.artist) },
                            processUrls = viewModel::processThumbnails
                        )
                    }
                    is LibraryArtistItem.GroupItem -> {
                        val group = libraryItem.group
                        GroupItem(
                            group = group,
                            thumbnailUrls = libraryItem.thumbnailUrls,
                            artistCount = libraryItem.artistCount,
                            onClick = { onGoToArtistGroup(group.groupId) },
                            onPlayClick = { viewModel.playArtistGroup(group) },
                            onShuffleClick = { viewModel.shuffleArtistGroup(group) },
                            onRenameClick = { viewModel.prepareToRenameGroup(group) },
                            onDeleteClick = { viewModel.itemPendingDeletion.value = DeletableItem.DeletableArtistGroup(group) },
                            // FIX: Pass the thumbnail processor function to the GroupItem
                            processUrls = viewModel::processThumbnails
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistItem(
    artistWithSongs: ArtistWithSongs,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onEdit: () -> Unit,
    onToggleAutoDownload: () -> Unit,
    processUrls: suspend (List<String>) -> List<String>,
    groupAction: Pair<String, () -> Unit>? = null,
    onHideArtist: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val artist = artistWithSongs.artist
    val librarySongs = artistWithSongs.songs.filter { it.isInLibrary }

    ListItem(
        headlineContent = {
            Text(
                artist.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (artist.downloadAutomatically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Downloads Automatically",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text("${librarySongs.size} songs")
            }
        },
        leadingContent = {
            CompositeThumbnailImage(
                urls = librarySongs.map { it.thumbnailUrl },
                contentDescription = "Thumbnail for ${artist.name}",
                processUrls = processUrls,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Play") }, onClick = { onPlay(); showMenu = false })
                    DropdownMenuItem(text = { Text("Shuffle") }, onClick = { onShuffle(); showMenu = false })
                    DropdownMenuItem(text = { Text("Edit song order") }, onClick = { onEdit(); showMenu = false })

                    val toggleText = if (artist.downloadAutomatically) "Disable auto-download" else "Enable auto-download"
                    DropdownMenuItem(text = { Text(toggleText) }, onClick = { onToggleAutoDownload(); showMenu = false })

                    onHideArtist?.let { hideAction ->
                        DropdownMenuItem(text = { Text("Hide Artist") }, onClick = {
                            hideAction()
                            showMenu = false
                        })
                    }

                    groupAction?.let { (text, action) ->
                        DropdownMenuItem(text = { Text(text) }, onClick = {
                            action()
                            showMenu = false
                        })
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
            .clickable(onClick = onClick)
            .height(72.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupItem(
    group: ArtistGroup,
    thumbnailUrls: List<String>,
    artistCount: Int,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    processUrls: suspend (List<String>) -> List<String>,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Text(
                group.name,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text("$artistCount artists")
        },
        leadingContent = {
            // FIX: Replaced FolderWithThumbnails with a Box containing the Folder icon
            // and the more robust CompositeThumbnailImage.
            Box(
                modifier = Modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Artist Group",
                    modifier = Modifier.fillMaxSize(),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                )
                CompositeThumbnailImage(
                    urls = thumbnailUrls,
                    contentDescription = "Thumbnails for ${group.name}",
                    processUrls = processUrls,
                    modifier = Modifier
                        .fillMaxSize(0.65f)
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Play") }, onClick = {
                        onPlayClick()
                        showMenu = false
                    })
                    DropdownMenuItem(text = { Text("Shuffle") }, onClick = {
                        onShuffleClick()
                        showMenu = false
                    })
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(text = { Text("Rename") }, onClick = {
                        onRenameClick()
                        showMenu = false
                    })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = {
                        onDeleteClick()
                        showMenu = false
                    })
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
            .clickable(onClick = onClick)
            .height(72.dp)
    )
}