package com.example.m.ui.library.details

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.data.database.Song
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.library.components.*
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArtistDetailScreen(
    onBack: () -> Unit,
    onEditArtistSongs: (Long) -> Unit,
    onArtistClick: (Long) -> Unit,
    onGroupClick: (Long) -> Unit,
    onEditGroup: (Long) -> Unit
) {
    val viewModel: ArtistDetailViewModel = hiltViewModel()
    val artist by viewModel.artist.collectAsState()
    val displayList by viewModel.displayList.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()
    val artistSongGroups by viewModel.artistSongGroups.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val artistName = artist?.name ?: "Artist"
    var showMenu by remember { mutableStateOf(false) }

    val itemToAddToPlaylist by remember { derivedStateOf { viewModel.itemToAddToPlaylist } }
    val songToAddToGroup by remember { derivedStateOf { viewModel.songToAddToGroup } }
    val showCreatePlaylistDialog by remember { derivedStateOf { viewModel.showCreatePlaylistDialog } }
    val showCreateSongGroupDialog by remember { derivedStateOf { viewModel.showCreateSongGroupDialog } }
    val itemToDelete by viewModel.itemPendingDeletion
    val groupToRename by remember { derivedStateOf { viewModel.groupToRename } }
    val groupToDelete by remember { derivedStateOf { viewModel.groupToDelete } }
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(Unit) {
        viewModel.navigateToArtist.collect { artistId ->
            onArtistClick(artistId)
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.dismissCreatePlaylistDialog() },
            onCreate = { name -> viewModel.createPlaylistAndAddPendingItem(name) }
        )
    }

    if (showCreateSongGroupDialog) {
        CreateArtistSongGroupDialog(
            onDismiss = { viewModel.dismissCreateSongGroupDialog() },
            onCreate = { name -> viewModel.createGroupAndAddSong(name) }
        )
    }

    groupToRename?.let { group ->
        RenameArtistSongGroupDialog(
            initialName = group.name,
            onDismiss = { viewModel.cancelRenameGroup() },
            onConfirm = { newName -> viewModel.confirmRenameGroup(newName) }
        )
    }

    groupToDelete?.let { group ->
        ConfirmDeleteDialog(
            itemType = "group",
            itemName = group.name,
            onDismiss = { viewModel.cancelDeleteGroup() },
            onConfirm = { viewModel.confirmDeleteGroup() }
        )
    }

    val currentItem = itemToAddToPlaylist
    if (currentItem != null) {
        val songTitle = if (currentItem is Song) currentItem.title else (currentItem as StreamInfoItem).name ?: "Unknown"
        val songArtist = if (currentItem is Song) currentItem.artist else (currentItem as StreamInfoItem).uploaderName ?: "Unknown"
        val thumbnailUrl = if (currentItem is Song) currentItem.thumbnailUrl else (currentItem as StreamInfoItem).getHighQualityThumbnailUrl()

        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissAddToPlaylistSheet() },
            sheetState = sheetState
        ) {
            AddToPlaylistSheet(
                songTitle = songTitle,
                songArtist = songArtist,
                songThumbnailUrl = thumbnailUrl,
                playlists = allPlaylists,
                onPlaylistSelected = { playlistId ->
                    viewModel.onPlaylistSelectedForAddition(playlistId)
                },
                onCreateNewPlaylist = {
                    viewModel.prepareToCreatePlaylist()
                }
            )
        }
    }

    val currentSongToAddToGroup = songToAddToGroup
    if (currentSongToAddToGroup != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissAddToGroupSheet() },
            sheetState = sheetState
        ) {
            AddToArtistSongGroupSheet(
                songTitle = currentSongToAddToGroup.title,
                songArtist = currentSongToAddToGroup.artist,
                songThumbnailUrl = currentSongToAddToGroup.thumbnailUrl,
                groups = artistSongGroups,
                onGroupSelected = { groupId ->
                    viewModel.addSongToGroup(groupId)
                },
                onCreateNewGroup = {
                    viewModel.prepareToCreateGroupWithSong()
                }
            )
        }
    }

    (itemToDelete as? Song)?.let { song ->
        ConfirmDeleteDialog(
            itemType = "song",
            itemName = song.title,
            onDismiss = { viewModel.itemPendingDeletion.value = null },
            onConfirm = {
                viewModel.deleteSong(song)
                viewModel.itemPendingDeletion.value = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(artistName, maxLines = 1, modifier = Modifier.basicMarquee()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ArtistSortMenu(
                        currentSortOrder = sortOrder,
                        onSortOrderSelected = { viewModel.setSortOrder(it) }
                    )
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Play All") },
                                onClick = { viewModel.playAll(); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Shuffle All") },
                                onClick = { viewModel.shuffleAll(); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Shuffle Ungrouped") },
                                onClick = { viewModel.shuffleUngrouped(); showMenu = false }
                            )
                            val toggleText = if (artist?.downloadAutomatically == true) "Disable auto-download" else "Enable auto-download"
                            DropdownMenuItem(
                                text = { Text(toggleText) },
                                onClick = { viewModel.toggleAutoDownload(); showMenu = false }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Create group") },
                                onClick = {
                                    viewModel.prepareToCreateSongGroup()
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit song order") },
                                onClick = {
                                    artist?.artistId?.let(onEditArtistSongs)
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (displayList.isEmpty()) {
            EmptyStateMessage(message = "No songs found for this artist.")
        } else {
            val listState = rememberLazyListState()

            LazyColumn(state = listState, modifier = Modifier.padding(paddingValues)) {
                items(displayList, key = { item ->
                    when (item) {
                        is ArtistDetailListItem.GroupHeader -> "group-${item.data.group.groupId}"
                        is ArtistDetailListItem.SongItem -> "song-${item.songForList.song.songId}"
                    }
                }) { item ->
                    when (item) {
                        is ArtistDetailListItem.GroupHeader -> {
                            GroupHeaderItem(
                                data = item.data,
                                // FIX: Pass the thumbnail processor function from the view model
                                processUrls = viewModel::processThumbnails,
                                onPlayClick = { viewModel.playGroup(item.data.group.groupId) },
                                onShuffleClick = { viewModel.shuffleGroup(item.data.group.groupId) },
                                onClick = { onGroupClick(item.data.group.groupId) },
                                onEdit = { onEditGroup(item.data.group.groupId) },
                                onDelete = { viewModel.prepareToDeleteGroup(item.data.group) }
                            )
                        }
                        is ArtistDetailListItem.SongItem -> {
                            SongItem(
                                song = item.songForList.song,
                                downloadStatus = item.songForList.downloadStatus,
                                onClick = { viewModel.onSongSelected(item.songForList.song) },
                                onAddToPlaylistClick = { viewModel.selectItemForPlaylist(item.songForList.song) },
                                onDeleteClick = { viewModel.itemPendingDeletion.value = item.songForList.song },
                                onPlayNextClick = { viewModel.onPlaySongNext(item.songForList.song) },
                                onAddToQueueClick = { viewModel.onAddSongToQueue(item.songForList.song) },
                                onShuffleClick = { viewModel.onShuffleSong(item.songForList.song) },
                                onGoToArtistClick = { viewModel.onGoToArtist(item.songForList.song) },
                                onDownloadClick = { viewModel.downloadSong(item.songForList.song) },
                                onAddToGroupClick = { viewModel.selectSongToAddToGroup(item.songForList.song) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupHeaderItem(
    data: GroupHeaderData,
    processUrls: suspend (List<String>) -> List<String>, // FIX: Add processUrls parameter
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = {
            Text(
                text = data.group.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        },
        supportingContent = { Text("${data.songCount} songs") },
        leadingContent = {
            // FIX: Replaced the deleted 'FolderWithThumbnails' with the correct implementation
            Box(
                modifier = Modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Group",
                    modifier = Modifier.fillMaxSize(),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                )
                CompositeThumbnailImage(
                    urls = data.thumbnailUrls,
                    contentDescription = "Thumbnails for ${data.group.name}",
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
                    Icon(Icons.Default.MoreVert, contentDescription = "More options for group")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Play") }, onClick = { onPlayClick(); showMenu = false })
                    DropdownMenuItem(text = { Text("Shuffle") }, onClick = { onShuffleClick(); showMenu = false })
                    DropdownMenuItem(text = { Text("Edit group") }, onClick = { onEdit(); showMenu = false })
                    DropdownMenuItem(text = { Text("Delete group") }, onClick = { onDelete(); showMenu = false })
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .height(72.dp)
    )
}