package com.example.m.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.example.m.ui.library.components.AddToPlaylistSheet
import com.example.m.ui.library.components.ConfirmDeleteDialog
import com.example.m.ui.library.components.CreatePlaylistDialog
import com.example.m.ui.library.components.EmptyStateMessage
import com.example.m.ui.library.components.SongItem
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
    val sheetState = rememberModalBottomSheetState()

    val showCreatePlaylistDialog by remember { derivedStateOf { viewModel.showCreatePlaylistDialog } }
    val itemToAddToPlaylist by remember { derivedStateOf { viewModel.itemToAddToPlaylist } }
    var clearActionPending by remember { mutableStateOf<ClearAction?>(null) }

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
            LazyColumn(modifier = Modifier
                .padding(innerScaffoldPadding)
                .navigationBarsPadding()) {
                itemsIndexed(history, key = { _, item -> item.entry.logId }) { index, item ->
                    val song = item.entry.song
                    SongItem(
                        song = song,
                        downloadStatus = item.downloadStatus,
                        onClick = { viewModel.onSongSelected(index) },
                        onAddToPlaylistClick = { viewModel.selectItemForPlaylist(song) },
                        onPlayNextClick = { viewModel.onPlaySongNext(song) },
                        onAddToQueueClick = { viewModel.onAddSongToQueue(song) },
                        onGoToArtistClick = { viewModel.onGoToArtist(song) },
                        onShuffleClick = { viewModel.onShuffleSong(song) },
                        onAddToLibraryClick = { viewModel.addToLibrary(song) },
                        onDownloadClick = { viewModel.download(song) },
                        onDeleteFromHistoryClick = { viewModel.deleteFromHistory(item.entry) }
                    )
                }
            }
        }
    }
}