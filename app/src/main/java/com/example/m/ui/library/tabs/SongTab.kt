// file: com/example/m/ui/library/tabs/SongTab.kt
package com.example.m.ui.library.tabs

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.ui.library.components.ConfirmDeleteDialog
import com.example.m.ui.library.components.SongItem

@Composable
fun SongsTabContent(
    modifier: Modifier = Modifier,
    viewModel: SongsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    uiState.itemPendingDeletion?.let { song ->
        ConfirmDeleteDialog(
            itemType = "song",
            itemName = song.title,
            onDismiss = { viewModel.onEvent(SongsTabEvent.ClearItemForDeletion) },
            onConfirm = { viewModel.onEvent(SongsTabEvent.ConfirmDeletion) }
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier.testTag("lazyColumn"), // Add this testTag
        contentPadding = PaddingValues(bottom = 16.dp) // Reduced padding for consistency
    ) {
        itemsIndexed(
            items = uiState.songs,
            key = { _, song -> song.songId }
        ) { index, song ->
            val isPlaying = song.youtubeUrl == uiState.nowPlayingMediaId || song.localFilePath == uiState.nowPlayingMediaId
            SongItem(
                song = song,
                isPlaying = isPlaying,
                onClick = { viewModel.onEvent(SongsTabEvent.SongSelected(index)) },
                onAddToPlaylistClick = { viewModel.onEvent(SongsTabEvent.AddToPlaylist(song)) },
                onDeleteClick = { viewModel.onEvent(SongsTabEvent.SetItemForDeletion(song)) },
                onPlayNextClick = { viewModel.onEvent(SongsTabEvent.PlaySongNext(song)) },
                onAddToQueueClick = { viewModel.onEvent(SongsTabEvent.AddSongToQueue(song)) },
                onShuffleClick = { viewModel.onEvent(SongsTabEvent.ShuffleSong(song)) },
                onGoToArtistClick = { viewModel.onEvent(SongsTabEvent.GoToArtist(song)) },
                onDownloadClick = { viewModel.onEvent(SongsTabEvent.DownloadSong(song)) },
                onAddToLibraryClick = { viewModel.onEvent(SongsTabEvent.AddToLibrary(song)) },
                onDeleteDownloadClick = { viewModel.onEvent(SongsTabEvent.DeleteSongDownload(song)) }
            )
        }
    }
}