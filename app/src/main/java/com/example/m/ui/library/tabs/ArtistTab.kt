// file: com/example/m/ui/library/tabs/ArtistTab.kt
package com.example.m.ui.library.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.data.database.ArtistGroup
import com.example.m.data.database.ArtistWithSongs
import com.example.m.ui.library.DeletableItem
import com.example.m.ui.library.LibraryArtistItem
import com.example.m.ui.library.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistsTabContent(
    onArtistClick: (Long) -> Unit,
    onGoToArtistGroup: (Long) -> Unit,
    onEditArtistSongs: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    uiState.artistPendingDisableAutoDownload?.let { artist ->
        DisableAutoDownloadConfirmationDialog(
            itemType = "artist",
            onDismiss = { viewModel.onEvent(ArtistTabEvent.DismissDisableAutoDownloadDialog) },
            onConfirmDisableOnly = { viewModel.onEvent(ArtistTabEvent.DisableAutoDownloadForArtist(removeFiles = false)) },
            onConfirmAndRemove = { viewModel.onEvent(ArtistTabEvent.DisableAutoDownloadForArtist(removeFiles = true)) }
        )
    }

    uiState.itemPendingDeletion?.let { item ->
        ConfirmDeleteDialog(
            itemType = "artist group",
            itemName = item.group.name,
            onDismiss = { viewModel.onEvent(ArtistTabEvent.ClearItemForDeletion) },
            onConfirm = { viewModel.onEvent(ArtistTabEvent.ConfirmDeletion) }
        )
    }

    if (uiState.showCreateArtistGroupDialog) {
        TextFieldDialog(
            title = "New Artist Group",
            label = "Group name",
            confirmButtonText = "Create",
            onDismiss = { viewModel.onEvent(ArtistTabEvent.DismissCreateArtistGroupDialog) },
            onConfirm = { name -> viewModel.onEvent(ArtistTabEvent.CreateArtistGroup(name)) }
        )
    }

    uiState.groupToRename?.let { group ->
        TextFieldDialog(
            title = "Rename Artist Group",
            label = "Group name",
            initialValue = group.name,
            confirmButtonText = "Rename",
            onDismiss = { viewModel.onEvent(ArtistTabEvent.CancelRenameGroup) },
            onConfirm = { newName -> viewModel.onEvent(ArtistTabEvent.ConfirmRenameGroup(newName)) }
        )
    }

    uiState.artistToMove?.let { artist ->
        ModalBottomSheet(onDismissRequest = { viewModel.onEvent(ArtistTabEvent.DismissMoveArtistSheet) }) {
            // Filter out the artist's current group, if they are in one.
            val availableGroups = uiState.allArtistGroups.filter { it.groupId != artist.parentGroupId }
            MoveToGroupSheet(
                artistName = artist.name,
                groups = availableGroups, // Use the filtered list
                onGroupSelected = { groupId -> viewModel.onEvent(ArtistTabEvent.MoveArtistToGroup(groupId)) }
            )
        }
    }

    if (uiState.libraryArtistItems.isEmpty()) {
        EmptyStateMessage(message = "Your artists and groups will appear here.")
    } else {
        LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 90.dp)) {
            items(
                items = uiState.libraryArtistItems,
                key = { item ->
                    when (item) {
                        is LibraryArtistItem.ArtistItem -> "artist-${item.artistWithSongs.artist.artistId}"
                        is LibraryArtistItem.GroupItem -> "group-${item.group.groupId}"
                    }
                }
            ) { libraryItem ->
                when (libraryItem) {
                    is LibraryArtistItem.ArtistItem -> {
                        val artist = libraryItem.artistWithSongs.artist
                        ArtistItem(
                            artistWithSongs = libraryItem.artistWithSongs,
                            onClick = { onArtistClick(artist.artistId) },
                            onPlay = { viewModel.onEvent(ArtistTabEvent.PlayArtist(artist)) },
                            onShuffle = { viewModel.onEvent(ArtistTabEvent.ShuffleArtist(artist)) },
                            onShuffleUngrouped = { viewModel.onEvent(ArtistTabEvent.ShuffleUngroupedSongsForArtist(artist)) },
                            onEdit = { onEditArtistSongs(artist.artistId) },
                            onToggleAutoDownload = { viewModel.onEvent(ArtistTabEvent.PrepareToToggleAutoDownloadArtist(artist)) },
                            groupAction = "Move to group..." to { viewModel.onEvent(ArtistTabEvent.PrepareToMoveArtist(artist)) },
                            onHideArtist = { viewModel.onEvent(ArtistTabEvent.HideArtist(artist)) },
                            processUrls = { urls -> viewModel.thumbnailProcessor.process(urls) }
                        )
                    }
                    is LibraryArtistItem.GroupItem -> {
                        val group = libraryItem.group
                        GroupItem(
                            group = group,
                            thumbnailUrls = libraryItem.thumbnailUrls,
                            artistCount = libraryItem.artistCount,
                            onClick = { onGoToArtistGroup(group.groupId) },
                            onPlayClick = { viewModel.onEvent(ArtistTabEvent.PlayArtistGroup(group)) },
                            onShuffleClick = { viewModel.onEvent(ArtistTabEvent.ShuffleArtistGroup(group)) },
                            onRenameClick = { viewModel.onEvent(ArtistTabEvent.PrepareToRenameGroup(group)) },
                            onDeleteClick = { viewModel.onEvent(ArtistTabEvent.SetItemForDeletion(group)) },
                            processUrls = { urls -> viewModel.thumbnailProcessor.process(urls) }
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
    onShuffleUngrouped: () -> Unit,
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
        headlineContent = { Text(artist.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (artist.downloadAutomatically) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Downloads Automatically", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(text = "${librarySongs.size} songs", style = MaterialTheme.typography.bodySmall)
            }
        },
        leadingContent = {
            CompositeThumbnailImage(
                urls = librarySongs.map { it.thumbnailUrl },
                contentDescription = "Thumbnail for ${artist.name}",
                processUrls = processUrls,
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(3.dp))
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                TranslucentDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Play") }, onClick = { onPlay(); showMenu = false })
                    DropdownMenuItem(text = { Text("Shuffle All") }, onClick = { onShuffle(); showMenu = false })
                    DropdownMenuItem(text = { Text("Shuffle Ungrouped") }, onClick = { onShuffleUngrouped(); showMenu = false })
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(text = { Text("Edit song order") }, onClick = { onEdit(); showMenu = false })
                    groupAction?.let { (text, action) -> DropdownMenuItem(text = { Text(text) }, onClick = { action(); showMenu = false }) }
                    onHideArtist?.let { hideAction -> DropdownMenuItem(text = { Text("Hide Artist") }, onClick = { hideAction(); showMenu = false }) }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    val toggleText = if (artist.downloadAutomatically) "Disable auto-download" else "Enable auto-download"
                    DropdownMenuItem(text = { Text(toggleText) }, onClick = { onToggleAutoDownload(); showMenu = false })
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = MaterialTheme.colorScheme.onSurface),
        modifier = modifier.clickable(onClick = onClick).height(72.dp)
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
        headlineContent = { Text(group.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) },
        supportingContent = { Text(text = "$artistCount artists", style = MaterialTheme.typography.bodySmall) },
        leadingContent = {
            Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Default.Folder, contentDescription = "Artist Group", modifier = Modifier.fillMaxSize(), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
                CompositeThumbnailImage(
                    urls = thumbnailUrls,
                    contentDescription = "Thumbnails for ${group.name}",
                    processUrls = processUrls,
                    modifier = Modifier.fillMaxSize(0.65f).padding(top = 4.dp).clip(RoundedCornerShape(3.dp))
                )
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                TranslucentDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Play") }, onClick = { onPlayClick(); showMenu = false })
                    DropdownMenuItem(text = { Text("Shuffle") }, onClick = { onShuffleClick(); showMenu = false })
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { onRenameClick(); showMenu = false })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { onDeleteClick(); showMenu = false })
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick).height(72.dp)
    )
}