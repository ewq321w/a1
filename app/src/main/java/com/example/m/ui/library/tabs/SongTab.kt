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
import com.example.m.ui.library.SongForList
import com.example.m.ui.library.SongSortOrder
import com.example.m.ui.library.components.SongItem

@Composable
fun SongsTabContent(
    songs: List<SongForList>,
    sortOrder: SongSortOrder,
    onSongSelected: (Int) -> Unit,
    onAddToPlaylistClick: (Song) -> Unit,
    onDeleteSongClick: (Song) -> Unit,
    onPlayNextClick: (Song) -> Unit,
    onAddToQueueClick: (Song) -> Unit,
    onShuffleClick: (Song) -> Unit,
    onGoToArtistClick: (Song) -> Unit,
    onDownloadClick: (Song) -> Unit,
    onAddToLibraryClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    onDeleteDownloadClick: (Song) -> Unit
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        itemsIndexed(
            items = songs,
            key = { _, item -> item.song.songId }
        ) { index, item ->
            val song = item.song
            val rememberedOnSongSelected = remember { { onSongSelected(index) } }
            val rememberedOnAddToPlaylistClick = remember { { onAddToPlaylistClick(song) } }
            val rememberedOnDeleteClick = remember { { onDeleteSongClick(song) } }
            val rememberedOnPlayNextClick = remember { { onPlayNextClick(song) } }
            val rememberedOnAddToQueueClick = remember { { onAddToQueueClick(song) } }
            val rememberedOnShuffleClick = remember { { onShuffleClick(song) } }
            val rememberedOnGoToArtistClick = remember { { onGoToArtistClick(song) } }
            val rememberedOnDownloadClick = remember { { onDownloadClick(song) } }
            val rememberedOnAddToLibraryClick = remember { { onAddToLibraryClick(song) } }
            val rememberedOnDeleteDownloadClick = remember { { onDeleteDownloadClick(song) } }

            SongItem(
                song = song,
                downloadStatus = item.downloadStatus,
                onClick = rememberedOnSongSelected,
                onAddToPlaylistClick = rememberedOnAddToPlaylistClick,
                onDeleteClick = rememberedOnDeleteClick,
                onPlayNextClick = rememberedOnPlayNextClick,
                onAddToQueueClick = rememberedOnAddToQueueClick,
                onShuffleClick = rememberedOnShuffleClick,
                onGoToArtistClick = rememberedOnGoToArtistClick,
                onDownloadClick = rememberedOnDownloadClick,
                onAddToLibraryClick = rememberedOnAddToLibraryClick,
                onDeleteDownloadClick = rememberedOnDeleteDownloadClick
            )
        }
    }
}