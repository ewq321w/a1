package com.example.m.ui.library

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.data.database.Song
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.library.components.AddToPlaylistSheet
import com.example.m.ui.library.components.CreatePlaylistDialog
import com.example.m.ui.library.components.EmptyStateMessage
import com.example.m.ui.library.components.SongItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (history.isEmpty()) {
            EmptyStateMessage(message = "Your listening history will appear here.")
        } else {
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                itemsIndexed(history, key = { _, entry -> entry.logId }) { index, entry ->
                    val song = entry.song
                    SongItem(
                        song = song,
                        onClick = { viewModel.onSongSelected(index) },
                        onAddToPlaylistClick = { viewModel.selectItemForPlaylist(song) },
                        onPlayNextClick = { viewModel.onPlaySongNext(song) },
                        onAddToQueueClick = { viewModel.onAddSongToQueue(song) },
                        onGoToArtistClick = { viewModel.onGoToArtist(song) },
                        onShuffleClick = { viewModel.onShuffleSong(song) },
                        onAddToLibraryClick = { viewModel.addToLibrary(song) },
                        onDownloadClick = { viewModel.download(song) },
                        onDeleteFromHistoryClick = { viewModel.deleteFromHistory(entry) }
                    )
                }
            }
        }
    }
}