// file: com/example/m/ui/library/tabs/SongsTab.kt
package com.example.m.ui.library.tabs

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.m.data.database.Song
import com.example.m.ui.library.LibraryEvent
import com.example.m.ui.library.components.SongItem

@Composable
fun SongsTabContent(
    songs: List<Song>,
    onEvent: (LibraryEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        itemsIndexed(
            items = songs,
            key = { _, song -> song.songId }
        ) { index, song ->
            SongItem(
                song = song,
                onClick = { onEvent(LibraryEvent.SongSelected(index)) },
                onAddToPlaylistClick = { onEvent(LibraryEvent.PrepareToShowPlaylistSheet(song)) },
                onDeleteClick = { onEvent(LibraryEvent.SetItemForDeletion(com.example.m.ui.library.DeletableItem.DeletableSong(song))) },
                onPlayNextClick = { onEvent(LibraryEvent.PlaySongNext(song)) },
                onAddToQueueClick = { onEvent(LibraryEvent.AddSongToQueue(song)) },
                onShuffleClick = { onEvent(LibraryEvent.ShuffleSong(song)) },
                onGoToArtistClick = { onEvent(LibraryEvent.GoToArtist(song)) },
                onDownloadClick = { onEvent(LibraryEvent.DownloadSong(song)) },
                onAddToLibraryClick = { onEvent(LibraryEvent.AddToLibrary(song)) },
                onDeleteDownloadClick = { onEvent(LibraryEvent.DeleteSongDownload(song)) }
            )
        }
    }
}