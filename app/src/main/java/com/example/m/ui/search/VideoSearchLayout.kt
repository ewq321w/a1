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
                            text = "Videos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (uiState.videoStreams.size > 4) {
                            TextButton(
                                onClick = { onShowMore(SearchCategory.VIDEOS) },
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) { Text("More") }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            itemsIndexed(videoStreamsWithStatus.take(4), key = { index, item -> (item.result.streamInfo.url ?: "") + index }) { index, item ->
                SearchResultItem(
                    result = item.result,
                    downloadStatus = item.downloadStatus,
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
                            text = "Channels",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (uiState.videoChannels.size > 1) {
                            TextButton(
                                onClick = { onShowMore(SearchCategory.CHANNELS) },
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) { Text("More") }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            items(uiState.videoChannels.take(1), key = { it.artistInfo.url!! }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChannelClick(item) }
                        .height(72.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = item.artistInfo.thumbnails.lastOrNull()?.url,
                        imageLoader = imageLoader,
                        contentDescription = item.artistInfo.name,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.artistInfo.name ?: "",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val subs = formatSubscriberCount(item.artistInfo.subscriberCount)
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
        }

        if (uiState.playlists.isNotEmpty()) {
            item {
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
                            text = "Playlists",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (uiState.playlists.size > 3) {
                            TextButton(
                                onClick = { onShowMore(SearchCategory.PLAYLISTS) },
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) { Text("More") }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            itemsIndexed(uiState.playlists.take(3), key = { index, item -> (item.albumInfo.url ?: "") + index }) { _, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlaylistClick(item) }
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
                            .clip(RoundedCornerShape(3.dp))
                            .aspectRatio(1f),
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