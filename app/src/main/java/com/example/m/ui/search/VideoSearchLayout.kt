package com.example.m.ui.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.ImageLoader

@Composable
fun VideoSearchLayout(
    uiState: SearchUiState,
    videoStreamsWithStatus: List<SearchResultForList>,
    imageLoader: ImageLoader,
    onVideoClick: (Int) -> Unit,
    onPlaylistClick: (AlbumResult) -> Unit,
    onChannelClick: (ArtistResult) -> Unit,
    onShowMore: (SearchCategory) -> Unit,
    onAddToLibrary: (SearchResult) -> Unit,
    onAddToPlaylist: (SearchResult) -> Unit,
    onPlayNext: (SearchResult) -> Unit,
    onAddToQueue: (SearchResult) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        if (uiState.videoStreams.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Videos",
                    showMoreButton = uiState.videoStreams.size > 4,
                    onMoreClicked = { onShowMore(SearchCategory.VIDEOS) }
                )
            }
            itemsIndexed(videoStreamsWithStatus.take(4), key = { index, item -> (item.result.streamInfo.url ?: "") + index }) { index, item ->
                SearchResultItem(
                    result = item.result,
                    localSong = item.localSong,
                    isSong = false,
                    imageLoader = imageLoader,
                    onPlay = { onVideoClick(index) },
                    onAddToLibrary = { onAddToLibrary(item.result) },
                    onAddToPlaylist = { onAddToPlaylist(item.result) },
                    onPlayNext = { onPlayNext(item.result) },
                    onAddToQueue = { onAddToQueue(item.result) }
                )
            }
        }

        if (uiState.videoChannels.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Channels",
                    showMoreButton = uiState.videoChannels.size > 1,
                    onMoreClicked = { onShowMore(SearchCategory.CHANNELS) }
                )
            }
            items(uiState.videoChannels.take(1), key = { it.artistInfo.url!! }) { item ->
                ArtistListItem(
                    artistResult = item,
                    imageLoader = imageLoader,
                    onArtistClicked = { onChannelClick(item) }
                )
            }
        }

        if (uiState.playlists.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Playlists",
                    showMoreButton = uiState.playlists.size > 3,
                    onMoreClicked = { onShowMore(SearchCategory.PLAYLISTS) }
                )
            }
            itemsIndexed(uiState.playlists.take(3), key = { index, item -> (item.albumInfo.url ?: "") + index }) { _, item ->
                AlbumOrPlaylistItem(
                    item = item.albumInfo,
                    imageLoader = imageLoader,
                    onClicked = { onPlaylistClick(item) }
                )
            }
        }
    }
}