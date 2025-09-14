// file: com/example/m/ui/library/details/ArtistSongGroupDetailScreen.kt
package com.example.m.ui.library.details

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.example.m.managers.PlaylistActionState
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
    val uiState by viewModel.uiState.collectAsState()
    val playlistActionState by viewModel.playlistActionState.collectAsState()
    val groupName = uiState.groupWithSongs?.group?.name ?: "Group"
    var showMenu by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    var groupToDelete by remember { mutableStateOf<com.example.m.data.database.ArtistSongGroup?>(null) }


    LaunchedEffect(Unit) {
        viewModel.navigateToArtist.collect { artistId ->
            onArtistClick(artistId)
        }
    }

    when(val state = playlistActionState) {
        is PlaylistActionState.AddToPlaylist -> {
            val item = state.item
            val songTitle = (item as? Song)?.title ?: (item as? StreamInfoItem)?.name ?: "Unknown"
            val songArtist = (item as? Song)?.artist ?: (item as? StreamInfoItem)?.uploaderName ?: "Unknown"
            val thumbnailUrl = (item as? Song)?.thumbnailUrl ?: (item as? StreamInfoItem)?.getHighQualityThumbnailUrl() ?: ""

            ModalBottomSheet(
                onDismissRequest = { viewModel.onPlaylistActionDismiss() },
                sheetState = sheetState
            ) {
                AddToPlaylistSheet(
                    songTitle = songTitle,
                    songArtist = songArtist,
                    songThumbnailUrl = thumbnailUrl,
                    playlists = state.playlists,
                    onPlaylistSelected = { playlistId -> viewModel.onPlaylistSelected(playlistId) },
                    onCreateNewPlaylist = { viewModel.onPrepareToCreatePlaylist() }
                )
            }
        }
        is PlaylistActionState.CreatePlaylist -> {
            TextFieldDialog(
                title = "New Playlist",
                label = "Playlist name",
                confirmButtonText = "Create",
                onDismiss = { viewModel.onPlaylistActionDismiss() },
                onConfirm = { name -> viewModel.onPlaylistCreateConfirm(name) }
            )
        }
        is PlaylistActionState.Hidden -> {}
    }


    groupToDelete?.let { group ->
        ConfirmDeleteDialog(
            itemType = "group",
            itemName = group.name,
            onDismiss = { groupToDelete = null },
            onConfirm = {
                // This requires a new event in the ViewModel
                // For now, this action is not implemented via events.
                groupToDelete = null
                onBack()
            }
        )
    }

    uiState.songPendingRemoval?.let { song ->
        ConfirmRemoveDialog(
            itemType = "song",
            itemName = song.title,
            containerType = "group",
            onDismiss = { viewModel.onEvent(ArtistSongGroupDetailEvent.CancelRemoveSong) },
            onConfirm = { viewModel.onEvent(ArtistSongGroupDetailEvent.ConfirmRemoveSong) }
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
                                    viewModel.onEvent(ArtistSongGroupDetailEvent.ShuffleGroup)
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit group") },
                                onClick = {
                                    uiState.groupWithSongs?.group?.groupId?.let { onEditGroup(it) }
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete group") },
                                onClick = {
                                    groupToDelete = uiState.groupWithSongs?.group
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.songs.isEmpty()) {
            EmptyStateMessage(message = "This group is empty.")
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                itemsIndexed(uiState.songs, key = { _, item -> item.songId }) { index, item ->
                    SongItem(
                        song = item,
                        onClick = { viewModel.onEvent(ArtistSongGroupDetailEvent.SongSelected(index)) },
                        onAddToPlaylistClick = { viewModel.onEvent(ArtistSongGroupDetailEvent.AddToPlaylist(item)) },
                        onPlayNextClick = { viewModel.onEvent(ArtistSongGroupDetailEvent.PlayNext(item)) },
                        onAddToQueueClick = { viewModel.onEvent(ArtistSongGroupDetailEvent.AddToQueue(item)) },
                        onGoToArtistClick = { viewModel.onEvent(ArtistSongGroupDetailEvent.GoToArtist(item)) },
                        onShuffleClick = { viewModel.onEvent(ArtistSongGroupDetailEvent.ShuffleSong(item)) },
                        onRemoveFromGroupClick = { viewModel.onEvent(ArtistSongGroupDetailEvent.PrepareToRemoveSong(item)) }
                    )
                }
            }
        }
    }
}