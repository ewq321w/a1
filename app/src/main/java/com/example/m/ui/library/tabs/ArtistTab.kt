package com.example.m.ui.library.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.m.data.database.Artist
import com.example.m.data.database.ArtistGroup
import com.example.m.ui.library.ArtistForList
import com.example.m.ui.library.DeletableItem
import com.example.m.ui.library.LibraryArtistItem
import com.example.m.ui.library.LibraryViewModel
import com.example.m.ui.library.components.CompositeThumbnailImage
import com.example.m.ui.library.components.EmptyStateMessage
import com.example.m.ui.library.components.FolderWithThumbnails

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
                        is LibraryArtistItem.ArtistItem -> "artist-${item.artistForList.artist.artistId}"
                        is LibraryArtistItem.GroupItem -> "group-${item.group.groupId}"
                    }
                },
                contentType = { item ->
                    item::class.java.simpleName
                }
            ) { libraryItem ->
                when (libraryItem) {
                    is LibraryArtistItem.ArtistItem -> {
                        val artist = libraryItem.artistForList.artist
                        ArtistItem(
                            artistForList = libraryItem.artistForList,
                            onClick = { onArtistClick(artist.artistId) },
                            onPlay = { viewModel.playArtist(artist) },
                            onShuffle = { viewModel.shuffleArtist(artist) },
                            onEdit = { onEditArtistSongs(artist.artistId) },
                            onToggleAutoDownload = { viewModel.toggleAutoDownloadForArtist(artist) },
                            groupAction = "Move to group..." to { viewModel.prepareToMoveArtist(artist) },
                            onHideArtist = { viewModel.hideArtist(artist) },
                            processUrls = viewModel::processThumbnails
                        )
                    }
                    is LibraryArtistItem.GroupItem -> {
                        val group = libraryItem.group
                        GroupItem(
                            group = group,
                            thumbnailUrls = libraryItem.thumbnailUrls,
                            onClick = { onGoToArtistGroup(group.groupId) },
                            onPlayClick = { viewModel.playArtistGroup(group) },
                            onShuffleClick = { viewModel.shuffleArtistGroup(group) },
                            onRenameClick = { viewModel.prepareToRenameGroup(group) },
                            onDeleteClick = { viewModel.itemPendingDeletion.value = DeletableItem.DeletableArtistGroup(group) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistItem(
    artistForList: ArtistForList,
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
    val artist = artistForList.artist

    ListItem(
        headlineContent = { Text(artist.name) },
        supportingContent = {
            if (artist.downloadAutomatically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Downloads Automatically",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        },
        leadingContent = {
            CompositeThumbnailImage(
                urls = artistForList.allThumbnailUrls,
                contentDescription = "Thumbnail for ${artist.name}",
                processUrls = processUrls,
                modifier = Modifier.size(50.dp)
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
        modifier = modifier.clickable(onClick = onClick)
    )
}

@Composable
fun GroupItem(
    group: ArtistGroup,
    thumbnailUrls: List<String>,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(group.name, fontWeight = FontWeight.Bold) },
        leadingContent = {
            FolderWithThumbnails(
                urls = thumbnailUrls,
                modifier = Modifier.size(50.dp)
            )
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
        modifier = modifier.clickable(onClick = onClick)
    )
}