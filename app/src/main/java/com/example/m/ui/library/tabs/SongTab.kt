package com.example.m.ui.library.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    onDownloadClick: (Song) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f)
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
                    onDownloadClick = rememberedOnDownloadClick
                )
            }
        }
    }
}