// file: com/example/m/ui/library/details/ArtistDetailScreen.kt
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
import com.example.m.managers.ThumbnailProcessor
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.library.components.*
import kotlinx.coroutines.flow.collectLatest
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
    val uiState by viewModel.uiState.collectAsState()
    val artistName = uiState.artist?.name ?: "Artist"
    var showMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(Unit) {
        viewModel.navigateToArtist.collect { artistId ->
            onArtistClick(artistId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (uiState.showRemoveDownloadsConfirm) {
        uiState.artist?.let {
            ConfirmDeleteDialog(
                itemType = "downloads for",
                itemName = it.name,
                onDismiss = { viewModel.onEvent(ArtistDetailEvent.HideRemoveDownloadsConfirm) },
                onConfirm = { viewModel.onEvent(ArtistDetailEvent.ConfirmRemoveDownloads) }
            )
        }
    }

    if (uiState.showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.onEvent(ArtistDetailEvent.DismissCreatePlaylistDialog) },
            onCreate = { name -> viewModel.onEvent(ArtistDetailEvent.CreatePlaylist(name)) }
        )
    }

    if (uiState.showCreateSongGroupDialog) {
        CreateArtistSongGroupDialog(
            onDismiss = { viewModel.onEvent(ArtistDetailEvent.DismissCreateSongGroupDialog) },
            onCreate = { name -> viewModel.onEvent(ArtistDetailEvent.CreateGroupAndAddSong(name)) }
        )
    }

    uiState.groupToRename?.let { group ->
        RenameArtistSongGroupDialog(
            initialName = group.name,
            onDismiss = { viewModel.onEvent(ArtistDetailEvent.CancelRenameGroup) },
            onConfirm = { newName -> viewModel.onEvent(ArtistDetailEvent.ConfirmRenameGroup(newName)) }
        )
    }

    uiState.groupToDelete?.let { group ->
        ConfirmDeleteDialog(
            itemType = "group",
            itemName = group.name,
            onDismiss = { viewModel.onEvent(ArtistDetailEvent.CancelDeleteGroup) },
            onConfirm = { viewModel.onEvent(ArtistDetailEvent.ConfirmDeleteGroup) }
        )
    }

    uiState.itemToAddToPlaylist?.let { item ->
        val songTitle = (item as? Song)?.title ?: (item as? StreamInfoItem)?.name ?: "Unknown"
        val songArtist = (item as? Song)?.artist ?: (item as? StreamInfoItem)?.uploaderName ?: "Unknown"
        val thumbnailUrl = (item as? Song)?.thumbnailUrl ?: (item as? StreamInfoItem)?.getHighQualityThumbnailUrl() ?: ""

        ModalBottomSheet(onDismissRequest = { viewModel.onEvent(ArtistDetailEvent.DismissAddToPlaylistSheet) }, sheetState = sheetState) {
            AddToPlaylistSheet(
                songTitle = songTitle,
                songArtist = songArtist,
                songThumbnailUrl = thumbnailUrl,
                playlists = uiState.allPlaylists,
                onPlaylistSelected = { playlistId -> viewModel.onEvent(ArtistDetailEvent.PlaylistSelectedForAddition(playlistId)) },
                onCreateNewPlaylist = { viewModel.onEvent(ArtistDetailEvent.PrepareToCreatePlaylist) }
            )
        }
    }

    uiState.songToAddToGroup?.let { song ->
        ModalBottomSheet(onDismissRequest = { viewModel.onEvent(ArtistDetailEvent.DismissAddToGroupSheet) }, sheetState = sheetState) {
            AddToArtistSongGroupSheet(
                songTitle = song.title,
                songArtist = song.artist,
                songThumbnailUrl = song.thumbnailUrl,
                groups = uiState.artistSongGroups,
                onGroupSelected = { groupId -> viewModel.onEvent(ArtistDetailEvent.AddSongToGroup(groupId)) },
                onCreateNewGroup = { viewModel.onEvent(ArtistDetailEvent.PrepareToCreateGroupWithSong) }
            )
        }
    }

    (uiState.itemPendingDeletion as? Song)?.let { song ->
        ConfirmDeleteDialog(
            itemType = "song",
            itemName = song.title,
            onDismiss = { viewModel.onEvent(ArtistDetailEvent.ClearItemForDeletion) },
            onConfirm = { viewModel.onEvent(ArtistDetailEvent.ConfirmDeletion) }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        currentSortOrder = uiState.sortOrder,
                        onSortOrderSelected = { viewModel.onEvent(ArtistDetailEvent.SetSortOrder(it)) }
                    )
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Play All") },
                                onClick = { viewModel.onEvent(ArtistDetailEvent.PlayAll); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Shuffle All") },
                                onClick = { viewModel.onEvent(ArtistDetailEvent.ShuffleAll); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Shuffle Ungrouped") },
                                onClick = { viewModel.onEvent(ArtistDetailEvent.ShuffleUngrouped); showMenu = false }
                            )
                            val toggleText = if (uiState.artist?.downloadAutomatically == true) "Disable auto-download" else "Enable auto-download"
                            DropdownMenuItem(
                                text = { Text(toggleText) },
                                onClick = { viewModel.onEvent(ArtistDetailEvent.ToggleAutoDownload); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Remove all downloads") },
                                onClick = {
                                    viewModel.onEvent(ArtistDetailEvent.ShowRemoveDownloadsConfirm)
                                    showMenu = false
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Create group") },
                                onClick = {
                                    viewModel.onEvent(ArtistDetailEvent.PrepareToCreateSongGroup)
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit song order") },
                                onClick = {
                                    uiState.artist?.artistId?.let(onEditArtistSongs)
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
        if (uiState.displayList.isEmpty()) {
            EmptyStateMessage(message = "No songs found for this artist.")
        } else {
            val listState = rememberLazyListState()

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                items(uiState.displayList, key = { item ->
                    when (item) {
                        is ArtistDetailListItem.GroupHeader -> "group-${item.data.group.groupId}"
                        is ArtistDetailListItem.SongItem -> "song-${item.song.songId}"
                    }
                }) { item ->
                    when (item) {
                        is ArtistDetailListItem.GroupHeader -> {
                            GroupHeaderItem(
                                data = item.data,
                                onPlayClick = { viewModel.onEvent(ArtistDetailEvent.PlayGroup(item.data.group.groupId)) },
                                onShuffleClick = { viewModel.onEvent(ArtistDetailEvent.ShuffleGroup(item.data.group.groupId)) },
                                onClick = { onGroupClick(item.data.group.groupId) },
                                onEdit = { onEditGroup(item.data.group.groupId) },
                                onDelete = { viewModel.onEvent(ArtistDetailEvent.PrepareToDeleteGroup(item.data.group)) }
                            )
                        }
                        is ArtistDetailListItem.SongItem -> {
                            val song = item.song
                            SongItem(
                                song = song,
                                onClick = { viewModel.onEvent(ArtistDetailEvent.SongSelected(song)) },
                                onAddToPlaylistClick = { viewModel.onEvent(ArtistDetailEvent.SelectItemForPlaylist(song)) },
                                onDeleteClick = { viewModel.onEvent(ArtistDetailEvent.SetItemForDeletion(song)) },
                                onPlayNextClick = { viewModel.onEvent(ArtistDetailEvent.PlayNext(song)) },
                                onAddToQueueClick = { viewModel.onEvent(ArtistDetailEvent.AddToQueue(song)) },
                                onShuffleClick = { viewModel.onEvent(ArtistDetailEvent.ShuffleSong(song)) },
                                onGoToArtistClick = { viewModel.onEvent(ArtistDetailEvent.GoToArtist(song)) },
                                onDownloadClick = { viewModel.onEvent(ArtistDetailEvent.DownloadSong(song)) },
                                onDeleteDownloadClick = { viewModel.onEvent(ArtistDetailEvent.DeleteDownload(song)) },
                                onAddToGroupClick = { viewModel.onEvent(ArtistDetailEvent.SelectSongToAddToGroup(song)) }
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