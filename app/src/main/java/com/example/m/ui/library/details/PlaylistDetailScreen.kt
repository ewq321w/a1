// file: com/example/m/ui/library/details/PlaylistDetailScreen.kt
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.data.database.Playlist
import com.example.m.data.database.Song
import com.example.m.managers.PlaylistActionState
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.library.components.*
import kotlinx.coroutines.flow.collectLatest
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onEditPlaylist: (Long) -> Unit,
    onArtistClick: (Long) -> Unit
) {
    val viewModel: PlaylistDetailViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val playlistActionState by viewModel.playlistActionState.collectAsState()
    var showDeleteConfirmation by remember { mutableStateOf(false) }
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

    if (uiState.showConfirmRemoveDownloadsOnDisableDialog) {
        DisableAutoDownloadConfirmationDialog(
            itemType = "playlist",
            onDismiss = { viewModel.onEvent(PlaylistDetailEvent.DismissDisableAutoDownloadDialog) },
            onConfirmDisableOnly = { viewModel.onEvent(PlaylistDetailEvent.DisableAutoDownload(removeFiles = false)) },
            onConfirmAndRemove = { viewModel.onEvent(PlaylistDetailEvent.DisableAutoDownload(removeFiles = true)) }
        )
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
        is PlaylistActionState.SelectGroupForNewPlaylist -> {
            SelectLibraryGroupDialog(
                groups = state.groups,
                onDismiss = { viewModel.onPlaylistActionDismiss() },
                onGroupSelected = { groupId -> viewModel.onGroupSelectedForNewPlaylist(groupId) },
                onCreateNewGroup = {
                    // This is complex because it can be triggered from multiple viewmodels.
                    // A direct call or a shared event bus might be better.
                    // For now, this might require a temporary dismiss and re-trigger.
                }
            )
        }
        is PlaylistActionState.Hidden -> {}
    }


    uiState.playlist?.let { pl ->
        if (showDeleteConfirmation) {
            ConfirmDeleteDialog(
                itemType = "playlist",
                itemName = pl.name,
                onDismiss = { showDeleteConfirmation = false },
                onConfirm = {
                    onBack()
                    viewModel.onEvent(PlaylistDetailEvent.DeletePlaylist)
                }
            )
        }

        PlaylistDetailContent(
            playlist = pl,
            songs = uiState.songs,
            onEvent = viewModel::onEvent,
            sortOrder = uiState.sortOrder,
            snackbarHostState = snackbarHostState,
            onBack = onBack,
            onEdit = { onEditPlaylist(pl.playlistId) },
            onShowDeleteDialog = { showDeleteConfirmation = true }
        )
    } ?: run {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Loading...") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PlaylistDetailContent(
    playlist: Playlist,
    songs: List<Song>,
    onEvent: (PlaylistDetailEvent) -> Unit,
    sortOrder: PlaylistSortOrder,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onShowDeleteDialog: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(playlist.name, maxLines = 1, modifier = Modifier.basicMarquee()) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    PlaylistSortMenu(
                        currentSortOrder = sortOrder,
                        onSortOrderSelected = { onEvent(PlaylistDetailEvent.SetSortOrder(it)) }
                    )
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Play") }, onClick = { onEvent(PlaylistDetailEvent.SongSelected(0)); showMenu = false })
                            DropdownMenuItem(text = { Text("Shuffle") }, onClick = { onEvent(PlaylistDetailEvent.ShufflePlaylist); showMenu = false })
                            DropdownMenuItem(text = { Text("Edit playlist") }, onClick = { onEdit(); showMenu = false })
                            val toggleText = if (playlist.downloadAutomatically) "Disable auto-download" else "Enable auto-download"
                            DropdownMenuItem(text = { Text(toggleText) }, onClick = { onEvent(PlaylistDetailEvent.PrepareToToggleAutoDownload); showMenu = false })
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { onShowDeleteDialog(); showMenu = false })
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (songs.isEmpty()) {
            EmptyStateMessage(message = "This playlist is empty.")
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                itemsIndexed(songs, key = { _, item -> item.songId }) { index, item ->
                    SongItem(
                        song = item,
                        onClick = { onEvent(PlaylistDetailEvent.SongSelected(index)) },
                        onAddToPlaylistClick = { onEvent(PlaylistDetailEvent.AddToPlaylist(item)) },
                        onRemoveFromPlaylistClick = { onEvent(PlaylistDetailEvent.RemoveSong(item.songId)) },
                        onPlayNextClick = { onEvent(PlaylistDetailEvent.PlayNext(item)) },
                        onAddToQueueClick = { onEvent(PlaylistDetailEvent.AddToQueue(item)) },
                        onShuffleClick = { onEvent(PlaylistDetailEvent.ShuffleSong(item)) },
                        onGoToArtistClick = { onEvent(PlaylistDetailEvent.GoToArtist(item)) },
                        onDownloadClick = { onEvent(PlaylistDetailEvent.DownloadSong(item)) },
                        onDeleteDownloadClick = { onEvent(PlaylistDetailEvent.DeleteDownload(item)) }
                    )
                }
            }
        }
    }
}