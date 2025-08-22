package com.example.m.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage

@Composable
fun VideoSearchResultsLayout(
    uiState: SearchUiState,
    imageLoader: ImageLoader,
    onShowMoreClicked: (SearchCategory) -> Unit,
    onCloseDetailedView: () -> Unit,
    onPlay: (Int, List<SearchResult>) -> Unit,
    onDownload: (SearchResult) -> Unit,
    onAddToLibrary: (SearchResult) -> Unit,
    onAddToPlaylistClick: (SearchResult) -> Unit,
    onChannelClick: (ArtistResult) -> Unit
) {
    if (uiState.detailedViewCategory != null) {
        DetailedVideoResultsView(
            category = uiState.detailedViewCategory,
            uiState = uiState,
            imageLoader = imageLoader,
            onBack = onCloseDetailedView,
            onPlay = onPlay,
            onDownload = onDownload,
            onAddToLibrary = onAddToLibrary,
            onAddToPlaylistClick = onAddToPlaylistClick,
            onChannelClick = onChannelClick
        )
    } else {
        SummaryVideoResultsView(
            uiState = uiState,
            imageLoader = imageLoader,
            onShowMoreClicked = onShowMoreClicked,
            onPlay = onPlay,
            onAddToPlaylistClick = onAddToPlaylistClick,
            onChannelClick = onChannelClick
        )
    }
}

@Composable
private fun SummaryVideoResultsView(
    uiState: SearchUiState,
    imageLoader: ImageLoader,
    onShowMoreClicked: (SearchCategory) -> Unit,
    onPlay: (Int, List<SearchResult>) -> Unit,
    onAddToPlaylistClick: (SearchResult) -> Unit,
    onChannelClick: (ArtistResult) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (uiState.videos.isNotEmpty()) {
            item(key = "videos_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Videos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (uiState.videos.size > 4) {
                        TextButton(onClick = { onShowMoreClicked(SearchCategory.VIDEOS) }) { Text("More") }
                    }
                }
            }
            itemsIndexed(
                items = uiState.videos.take(4),
                key = { _, result -> result.streamInfo.url ?: result.streamInfo.hashCode() }
            ) { index, result ->
                SearchResultItem(
                    result = result,
                    isSong = false,
                    imageLoader = imageLoader,
                    onPlay = { onPlay(index, uiState.videos) },
                    onDownload = { },
                    onAddToLibrary = { },
                    onAddToPlaylistClick = { onAddToPlaylistClick(result) }
                )
            }
        }

        if (uiState.videoChannels.isNotEmpty()) {
            item(key = "channels_section") {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Channels", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (uiState.videoChannels.size > 1) {
                            TextButton(onClick = { onShowMoreClicked(SearchCategory.CHANNELS) }) { Text("More") }
                        }
                    }
                    uiState.videoChannels.take(1).forEach { artistResult ->
                        ListItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChannelClick(artistResult) },
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
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailedVideoResultsView(
    category: SearchCategory,
    uiState: SearchUiState,
    imageLoader: ImageLoader,
    onBack: () -> Unit,
    onPlay: (Int, List<SearchResult>) -> Unit,
    onDownload: (SearchResult) -> Unit,
    onAddToLibrary: (SearchResult) -> Unit,
    onAddToPlaylistClick: (SearchResult) -> Unit,
    onChannelClick: (ArtistResult) -> Unit
) {
    val title = when (category) {
        SearchCategory.VIDEOS -> "Videos"
        SearchCategory.CHANNELS -> "Channels"
        else -> ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            when (category) {
                SearchCategory.VIDEOS -> {
                    itemsIndexed(uiState.videos, key = { index, item -> item.streamInfo.url + index }) { index, item ->
                        SearchResultItem(
                            result = item,
                            isSong = false,
                            imageLoader = imageLoader,
                            onPlay = { onPlay(index, uiState.videos) },
                            onDownload = { onDownload(item) },
                            onAddToLibrary = { onAddToLibrary(item) },
                            onAddToPlaylistClick = { onAddToPlaylistClick(item) }
                        )
                    }
                }
                SearchCategory.CHANNELS -> {
                    items(uiState.videoChannels, key = { it.artistInfo.url!! }) { item ->
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
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                )
                            }
                        )
                    }
                }
                else -> {}
            }
        }
    }
}