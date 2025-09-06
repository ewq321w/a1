// file: com/example/m/ui/library/HistoryScreen.kt
package com.example.m.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.data.database.LibraryGroup
import com.example.m.data.database.Song
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
    val history by viewModel.history.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()
    val libraryGroups by viewModel.libraryGroups.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    val snackbarHostState = remember { SnackbarHostState() }

    val showCreatePlaylistDialog by remember { derivedStateOf { viewModel.showCreatePlaylistDialog } }
    val itemToAddToPlaylist by remember { derivedStateOf { viewModel.itemToAddToPlaylist } }
    var clearActionPending by remember { mutableStateOf<ClearAction?>(null) }
    val conflictDialogState by remember { derivedStateOf { viewModel.conflictDialogState } }
    val showCreateGroupDialog by remember { derivedStateOf { viewModel.showCreateGroupDialog } }
    val showSelectGroupDialog by remember { derivedStateOf { viewModel.showSelectGroupDialog } }


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

    if (showCreateGroupDialog) {
        CreateLibraryGroupDialog(
            onDismiss = { viewModel.dismissCreateGroupDialog() },
            onCreate = { name -> viewModel.createGroupAndProceed(name) },
            isFirstGroup = libraryGroups.isEmpty()
        )
    }

    if (showSelectGroupDialog) {
        SelectLibraryGroupDialog(
            groups = libraryGroups,
            onDismiss = { viewModel.dismissSelectGroupDialog() },
            onGroupSelected = { groupId -> viewModel.onGroupSelectedForAddition(groupId) },
            onCreateNewGroup = viewModel::prepareToCreateGroup
        )
    }

    conflictDialogState?.let { state ->
        ArtistGroupConflictDialog(
            artistName = state.song.artist,
            conflictingGroupName = state.conflict.conflictingGroupName,
            targetGroupName = state.targetGroupName,
            onDismiss = { viewModel.dismissConflictDialog() },
            onMoveArtistToTargetGroup = { viewModel.resolveConflictByMoving() }
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.dismissCreatePlaylistDialog() },
            onCreate = { name -> viewModel.createPlaylistAndAddPendingItem(name) }
        )
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
                viewModel.clearHistory(keepCount)
                clearActionPending = null
            }
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
                            DropdownMenuItem(
                                text = { Text("Keep last 50 plays") },
                                onClick = {
                                    clearActionPending = ClearAction.KEEP_50
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Keep last 100 plays") },
                                onClick = {
                                    clearActionPending = ClearAction.KEEP_100
                                    showMenu = false
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Clear all history") },
                                onClick = {
                                    clearActionPending = ClearAction.ALL
                                    showMenu = false
                                }
                            )
                        }
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerScaffoldPadding ->
        if (history.isEmpty()) {
            EmptyStateMessage(message = "Your listening history will appear here.")
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerScaffoldPadding)
                    .fillMaxSize()
            ) {
                itemsIndexed(history, key = { _, item -> item.entry.logId }) { index, item ->
                    val song = item.entry.song
                    SongItem(
                        song = song,
                        downloadStatus = item.downloadStatus,
                        onClick = { viewModel.onSongSelected(index) },
                        onAddToPlaylistClick = { viewModel.selectItemForPlaylist(song) },
                        onPlayNextClick = { viewModel.onPlaySongNext(song) },
                        onAddToQueueClick = { viewModel.onAddToQueue(song) },
                        onGoToArtistClick = { viewModel.onGoToArtist(song) },
                        onShuffleClick = { viewModel.onShuffleSong(song) },
                        onAddToLibraryClick = { viewModel.addToLibrary(song) },
                        onDownloadClick = { viewModel.download(song) },
                        onDeleteDownloadClick = { viewModel.deleteSongDownload(song) },
                        onDeleteFromHistoryClick = { viewModel.deleteFromHistory(item.entry) }
                    )
                }
            }
        }
    }
}