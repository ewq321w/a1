package com.example.m.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.example.m.ui.common.getThumbnail

@Composable
fun MusicSearchLayout(
    uiState: SearchUiState,
    songsWithStatus: List<SearchResultForList>,
    imageLoader: ImageLoader,
    onSongClicked: (Int) -> Unit,
    onAlbumClicked: (AlbumResult) -> Unit,
    onArtistClicked: (ArtistResult) -> Unit,
    onShowMore: (SearchCategory) -> Unit,
    onDownloadSong: (SearchResult) -> Unit,
    onAddToLibrary: (SearchResult) -> Unit,
    onAddToPlaylist: (SearchResult) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        if (uiState.songs.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Songs",
                    showMoreButton = uiState.songs.size > 3,
                    onMoreClicked = { onShowMore(SearchCategory.SONGS) }
                )
            }
            itemsIndexed(songsWithStatus.take(3), key = { _, item -> item.result.streamInfo.url!! }) { index, item ->
                SearchResultItem(
                    result = item.result,
                    downloadStatus = item.downloadStatus,
                    isSong = true,
                    imageLoader = imageLoader,
                    onPlay = { onSongClicked(index) },
                    onDownload = { onDownloadSong(item.result) },
                    onAddToLibrary = { onAddToLibrary(item.result) },
                    onAddToPlaylistClick = { onAddToPlaylist(item.result) }
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
            items(uiState.albums.take(3), key = { it.albumInfo.url!! }) { item ->
                ListItem(
                    modifier = Modifier.clickable { onAlbumClicked(item) },
                    headlineContent = { Text(item.albumInfo.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        if (item.albumInfo.uploaderName != null) {
                            Text(item.albumInfo.uploaderName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    leadingContent = {
                        AsyncImage(
                            model = item.albumInfo.getThumbnail(),
                            imageLoader = imageLoader,
                            contentDescription = item.albumInfo.name,
                            modifier = Modifier.size(50.dp)
                        )
                    }
                )
            }
        }

        if (uiState.artists.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Artists",
                    showMoreButton = uiState.artists.size > 3,
                    onMoreClicked = { onShowMore(SearchCategory.ARTISTS) }
                )
            }
            items(uiState.artists.take(3), key = { it.artistInfo.url!! }) { item ->
                ArtistListItem(
                    artistResult = item,
                    imageLoader = imageLoader,
                    onArtistClicked = { onArtistClicked(item) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    showMoreButton: Boolean,
    onMoreClicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (showMoreButton) {
            TextButton(onClick = onMoreClicked) { Text("More") }
        }
    }
}

@Composable
private fun ArtistListItem(
    artistResult: ArtistResult,
    imageLoader: ImageLoader,
    onArtistClicked: (ArtistResult) -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onArtistClicked(artistResult) },
        headlineContent = { Text(artistResult.artistInfo.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val subs = formatSubscriberCount(artistResult.artistInfo.subscriberCount)
            if (subs.isNotEmpty()) {
                Text(text = subs, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = {
            AsyncImage(
                model = artistResult.artistInfo.thumbnails.lastOrNull()?.url,
                imageLoader = imageLoader,
                contentDescription = artistResult.artistInfo.name,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
            )
        }
    )
}