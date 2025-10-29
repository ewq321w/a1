// file: com/example/m/ui/search/MusicSearchLayout.kt
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
fun MusicSearchLayout(
    uiState: SearchUiState,
    songsWithStatus: List<SearchResultForList>,
    nowPlayingMediaId: String?,
    imageLoader: ImageLoader,
    onSongClicked: (Int) -> Unit,
    onAlbumClicked: (AlbumResult) -> Unit,
    onArtistClicked: (ArtistResult) -> Unit,
    onShowMore: (SearchCategory) -> Unit,
    onAddToLibrary: (SearchResult) -> Unit,
    onPlayNext: (SearchResult) -> Unit,
    onAddToQueue: (SearchResult) -> Unit,
    onShuffleAlbum: (AlbumResult) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        if (uiState.songs.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Songs",
                    showMoreButton = uiState.songs.size > 4,
                    onMoreClicked = { onShowMore(SearchCategory.SONGS) }
                )
            }
            itemsIndexed(songsWithStatus.take(4), key = { index, item -> (item.result.streamInfo.url ?: "") + index }) { index, item ->
                val normalizedUrl = item.result.streamInfo.url?.replace("music.youtube.com", "www.youtube.com")
                val isPlaying = normalizedUrl == nowPlayingMediaId || item.localSong?.localFilePath == nowPlayingMediaId
                SearchResultItem(
                    result = item.result,
                    localSong = item.localSong,
                    isPlaying = isPlaying,
                    isSong = true,
                    imageLoader = imageLoader,
                    onPlay = { onSongClicked(index) },
                    onAddToLibrary = { onAddToLibrary(item.result) },
                    onPlayNext = { onPlayNext(item.result) },
                    onAddToQueue = { onAddToQueue(item.result) }
                )
            }
        }

        if (uiState.artists.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Artists",
                    showMoreButton = uiState.artists.size > 1,
                    onMoreClicked = { onShowMore(SearchCategory.ARTISTS) }
                )
            }
            items(uiState.artists.take(2).distinctBy { it.artistInfo.name }, key = { it.artistInfo.url!! }) { item ->
                ArtistListItem(
                    artistResult = item,
                    imageLoader = imageLoader,
                    onArtistClicked = { onArtistClicked(item) }
                )
            }
        }

        if (uiState.albums.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Albums",
                    showMoreButton = uiState.albums.size > 3,
                    onMoreClicked = { onShowMore(SearchCategory.ALBUMS) }
                )
            }
            itemsIndexed(uiState.albums.take(3), key = { index, item -> (item.albumInfo.url ?: "") + index }) { _, item ->
                AlbumOrPlaylistItem(
                    item = item.albumInfo,
                    imageLoader = imageLoader,
                    onClicked = { onAlbumClicked(item) }
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
                    onClicked = { onAlbumClicked(item) }
                )
            }
        }
    }
}