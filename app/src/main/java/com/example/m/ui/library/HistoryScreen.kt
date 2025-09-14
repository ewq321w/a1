// file: com/example/m/ui/library/HistoryScreen.kt
package com.example.m.ui.library

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.data.database.Song
import com.example.m.managers.DialogState
import com.example.m.managers.PlaylistActionState
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.library.components.*
import kotlinx.coroutines.flow.collectLatest
import org.schabi.newpipe.extractor.stream.StreamInfoItem

private enum class ClearAction { ALL, KEEP_50, KEEP_100 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onArtistClick: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    val snackbarHostState = remember { SnackbarHostState() }
    var clearActionPending by remember { mutableStateOf<ClearAction?>(null) }
    val dialogState by viewModel.dialogState.collectAsState()
    val playlistActionState by viewModel.playlistActionState.collectAsState()

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

    when (val state = dialogState) {
        is DialogState.CreateGroup -> {
            TextFieldDialog(
                title = "New Library Group",
                label = "Group name",
                confirmButtonText = "Create",
                onDismiss = { viewModel.onDialogDismiss() },
                onConfirm = { name -> viewModel.onDialogCreateGroup(name) },
                content = {
                    if (state.isFirstGroup) {
                        Text(
                            "To start your library, please create a group to add songs to.",
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            )
        }
        is DialogState.SelectGroup -> {
            SelectLibraryGroupDialog(
                groups = state.groups,
                onDismiss = { viewModel.onDialogDismiss() },
                onGroupSelected = { groupId -> viewModel.onDialogGroupSelected(groupId) },
                onCreateNewGroup = {
                    viewModel.onDialogDismiss()
                }
            )
        }
        is DialogState.Conflict -> {
            ArtistGroupConflictDialog(
                artistName = state.song.artist,
                conflictingGroupName = state.conflict.conflictingGroupName,
                targetGroupName = state.targetGroupName,
                onDismiss = { viewModel.onDialogDismiss() },
                onMoveArtistToTargetGroup = { viewModel.onDialogResolveConflict() }
            )
        }
        is DialogState.Hidden -> {
            // Do nothing
        }
    }

    when (val state = playlistActionState) {
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


    clearActionPending?.let { action ->
        val (itemType, itemName) = when (action) {
            ClearAction.ALL -> "history" to "all of your listening history"
            ClearAction.KEEP_50 -> "history" to "all but the last 50 plays"
            ClearAction.KEEP_100 -> "history" to "all but the last 100 plays"
        }
        ConfirmDeleteDialog(
            itemType = itemType,
            itemName = itemName,
            onDismiss = { clearActionPending = null },
            onConfirm = {
                val keepCount = when (action) {
                    ClearAction.ALL -> 0
                    ClearAction.KEEP_50 -> 50
                    ClearAction.KEEP_100 -> 100
                }
                viewModel.onEvent(HistoryEvent.ClearHistory(keepCount))
                clearActionPending = null
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Keep last 50 plays") }, onClick = { clearActionPending = ClearAction.KEEP_50; showMenu = false })
                            DropdownMenuItem(text = { Text("Keep last 100 plays") }, onClick = { clearActionPending = ClearAction.KEEP_100; showMenu = false })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Clear all history") }, onClick = { clearActionPending = ClearAction.ALL; showMenu = false })
                        }
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerScaffoldPadding ->
        if (uiState.history.isEmpty()) {
            EmptyStateMessage(message = "Your listening history will appear here.")
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerScaffoldPadding)
                    .fillMaxSize()
            ) {
                itemsIndexed(uiState.history, key = { _, item -> item.logId }) { index, item ->
                    val song = item.song
                    SongItem(
                        song = song,
                        onClick = { viewModel.onEvent(HistoryEvent.SongSelected(index)) },
                        onAddToPlaylistClick = { viewModel.onEvent(HistoryEvent.AddToPlaylist(song)) },
                        onPlayNextClick = { viewModel.onEvent(HistoryEvent.PlayNext(song)) },
                        onAddToQueueClick = { viewModel.onEvent(HistoryEvent.AddToQueue(song)) },
                        onGoToArtistClick = { viewModel.onEvent(HistoryEvent.GoToArtist(song)) },
                        onShuffleClick = { viewModel.onEvent(HistoryEvent.Shuffle(song)) },
                        onAddToLibraryClick = { viewModel.onEvent(HistoryEvent.AddToLibrary(song)) },
                        onDownloadClick = { viewModel.onEvent(HistoryEvent.Download(song)) },
                        onDeleteDownloadClick = { viewModel.onEvent(HistoryEvent.DeleteDownload(song)) },
                        onDeleteFromHistoryClick = { viewModel.onEvent(HistoryEvent.DeleteFromHistory(item)) }
                    )
                }
            }
        }
    }
}