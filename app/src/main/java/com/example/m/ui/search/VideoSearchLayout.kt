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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.example.m.ui.common.getThumbnail

@Composable
fun VideoSearchLayout(
    uiState: SearchUiState,
    videoStreamsWithStatus: List<SearchResultForList>,
    imageLoader: ImageLoader,
    onVideoClick: (Int) -> Unit,
    onPlaylistClick: (AlbumResult) -> Unit,
    onChannelClick: (ArtistResult) -> Unit,
    onShowMore: (SearchCategory) -> Unit,
    onDownloadSong: (SearchResult) -> Unit,
    onAddToLibrary: (SearchResult) -> Unit,
    onAddToPlaylist: (SearchResult) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        if (uiState.videoStreams.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Videos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (uiState.videoStreams.size > 4) {
                        TextButton(onClick = { onShowMore(SearchCategory.VIDEOS) }) { Text("More") }
                    }
                }
            }
            itemsIndexed(videoStreamsWithStatus.take(4), key = { index, item -> (item.result.streamInfo.url ?: "") + index }) { index, item ->
                SearchResultItem(
                    result = item.result,
                    downloadStatus = item.downloadStatus,
                    isSong = false,
                    imageLoader = imageLoader,
                    onPlay = { onVideoClick(index) },
                    onDownload = { onDownloadSong(item.result) },
                    onAddToLibrary = { onAddToLibrary(item.result) },
                    onAddToPlaylistClick = { onAddToPlaylist(item.result) }
                )
            }
        }

        if (uiState.videoPlaylists.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (uiState.videoPlaylists.size > 3) {
                        TextButton(onClick = { onShowMore(SearchCategory.ALBUMS) }) { Text("More") }
                    }
                }
            }
            itemsIndexed(uiState.videoPlaylists.take(3), key = { index, item -> (item.albumInfo.url ?: "") + index }) { _, item ->
                ListItem(
                    modifier = Modifier.clickable { onPlaylistClick(item) },
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
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }

        if (uiState.videoChannels.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Channels", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (uiState.videoChannels.size > 1) {
                        TextButton(onClick = { onShowMore(SearchCategory.CHANNELS) }) { Text("More") }
                    }
                }
            }
            items(uiState.videoChannels.take(1), key = { it.artistInfo.url!! }) { item ->
                ListItem(
                    modifier = Modifier.clickable { onChannelClick(item) },
                    headlineContent = { Text(item.artistInfo.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        val subs = formatSubscriberCount(item.artistInfo.subscriberCount)
                        if (subs.isNotEmpty()) {
                            Text(text = subs)
                        }
                    },
                    leadingContent = {
                        AsyncImage(
                            model = item.artistInfo.thumbnails.lastOrNull()?.url,
                            imageLoader = imageLoader,
                            contentDescription = item.artistInfo.name,
                            modifier = Modifier.size(50.dp).clip(CircleShape)
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}
