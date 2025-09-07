package com.example.m.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
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
fun VideoSearchLayout(
    uiState: SearchUiState,
    videoStreamsWithStatus: List<SearchResultForList>,
    imageLoader: ImageLoader,
    onVideoClick: (Int) -> Unit,
    onPlaylistClick: (AlbumResult) -> Unit,
    onChannelClick: (ArtistResult) -> Unit,
    onShowMore: (SearchCategory) -> Unit,
    onAddToLibrary: (SearchResult) -> Unit,
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
                AlbumListItem(
                    albumResult = item,
                    imageLoader = imageLoader,
                    onAlbumClicked = { onPlaylistClick(item) }
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
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
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

@Composable
private fun AlbumListItem(
    albumResult: AlbumResult,
    imageLoader: ImageLoader,
    onAlbumClicked: (AlbumResult) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAlbumClicked(albumResult) }
            .height(72.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = albumResult.albumInfo.getThumbnail(),
            imageLoader = imageLoader,
            contentDescription = albumResult.albumInfo.name,
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(3.dp))
                .aspectRatio(1f),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = albumResult.albumInfo.name ?: "",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (albumResult.albumInfo.uploaderName != null) {
                Text(
                    text = albumResult.albumInfo.uploaderName!!,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}