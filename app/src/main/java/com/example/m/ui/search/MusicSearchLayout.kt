package com.example.m.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.example.m.ui.common.getThumbnail

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicSearchLayout(
    uiState: SearchUiState,
    songsWithStatus: List<SearchResultForList>,
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
                SearchResultItem(
                    result = item.result,
                    downloadStatus = item.downloadStatus,
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
            val artistsToShow = if (uiState.artists.size >= 2
                && uiState.artists[1].artistInfo.name?.endsWith(" - Topic") == true) {
                uiState.artists.take(2)
            } else {
                uiState.artists.take(1)
            }
            items(artistsToShow, key = { it.artistInfo.url!! }) { item ->
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlbumClicked(item) }
                        .height(72.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = item.albumInfo.getThumbnail(),
                        imageLoader = imageLoader,
                        contentDescription = item.albumInfo.name,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.albumInfo.name ?: "",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (item.albumInfo.uploaderName != null) {
                            Text(
                                text = item.albumInfo.uploaderName!!,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlbumClicked(item) }
                        .height(72.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = item.albumInfo.getThumbnail(),
                        imageLoader = imageLoader,
                        contentDescription = item.albumInfo.name,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.albumInfo.name ?: "",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (item.albumInfo.uploaderName != null) {
                            Text(
                                text = item.albumInfo.uploaderName!!,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
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
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (showMoreButton) {
                TextButton(
                    onClick = onMoreClicked,
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) { Text("More") }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistListItem(
    artistResult: ArtistResult,
    imageLoader: ImageLoader,
    onArtistClicked: (ArtistResult) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onArtistClicked(artistResult) }
            .height(72.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = artistResult.artistInfo.thumbnails.lastOrNull()?.url,
            imageLoader = imageLoader,
            contentDescription = artistResult.artistInfo.name,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artistResult.artistInfo.name ?: "",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val subs = formatSubscriberCount(artistResult.artistInfo.subscriberCount)
            if (subs.isNotEmpty()) {
                Text(
                    text = subs,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}