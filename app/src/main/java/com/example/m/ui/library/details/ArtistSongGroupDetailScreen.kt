package com.example.m.ui.library.details

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.data.database.Song
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.library.components.*
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArtistSongGroupDetailScreen(
    onBack: () -> Unit,
    onArtistClick: (Long) -> Unit,
    onEditGroup: (Long) -> Unit,
    viewModel: ArtistSongGroupDetailViewModel = hiltViewModel()
) {
    val songs by viewModel.songs.collectAsState()
    val groupWithSongs by viewModel.groupWithSongs.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()
    val groupName = groupWithSongs?.group?.name ?: "Group"
    var showMenu by remember { mutableStateOf(false) }

    val showCreatePlaylistDialog by remember { derivedStateOf { viewModel.showCreatePlaylistDialog } }
    val itemToAddToPlaylist by remember { derivedStateOf { viewModel.itemToAddToPlaylist } }
    val groupToRename by remember { derivedStateOf { viewModel.groupToRename } }
    val groupToDelete by remember { derivedStateOf { viewModel.groupToDelete } }
    val songPendingRemoval by remember { derivedStateOf { viewModel.songPendingRemoval } }
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
            onConfirm = {
                viewModel.confirmDeleteGroup()
                onBack()
            }
        )
    }

    songPendingRemoval?.let { song ->
        ConfirmRemoveDialog(
            itemType = "song",
            itemName = song.title,
            containerType = "group",
            onDismiss = { viewModel.cancelRemoveSongFromGroup() },
            onConfirm = { viewModel.confirmRemoveSongFromGroup() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(groupName, maxLines = 1, modifier = Modifier.basicMarquee()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Shuffle") },
                                onClick = {
                                    viewModel.shuffleGroup()
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit group") },
                                onClick = {
                                    groupWithSongs?.group?.groupId?.let { onEditGroup(it) }
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete group") },
                                onClick = {
                                    viewModel.prepareToDeleteGroup()
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (songs.isEmpty()) {
            EmptyStateMessage(message = "This group is empty.")
        } else {
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                itemsIndexed(songs, key = { _, item -> item.song.songId }) { index, item ->
                    SongItem(
                        song = item.song,
                        downloadStatus = item.downloadStatus,
                        onClick = { viewModel.onSongSelected(index) },
                        onAddToPlaylistClick = { viewModel.selectItemForPlaylist(item.song) },
                        onPlayNextClick = { viewModel.onPlayNext(item.song) },
                        onAddToQueueClick = { viewModel.onAddToQueue(item.song) },
                        onGoToArtistClick = { viewModel.onGoToArtist(item.song) },
                        onShuffleClick = { viewModel.onShuffleSong(item.song) },
                        onRemoveFromGroupClick = { viewModel.prepareToRemoveSongFromGroup(item.song) }
                    )
                }
            }
        }
    }
}