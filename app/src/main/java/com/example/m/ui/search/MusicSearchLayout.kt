package com.example.m.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
fun MusicSearchResultsLayout(
    uiState: SearchUiState,
    imageLoader: ImageLoader,
    onShowMoreClicked: (SearchCategory) -> Unit,
    onCloseDetailedView: () -> Unit,
    onPlaySong: (Int, List<SearchResult>) -> Unit,
    onDownloadSong: (SearchResult) -> Unit,
    onAddSongToLibrary: (SearchResult) -> Unit,
    onAddSongToPlaylistClick: (SearchResult) -> Unit,
    onArtistClick: (ArtistResult) -> Unit
) {
    if (uiState.detailedViewCategory != null) {
        DetailedResultsView(
            category = uiState.detailedViewCategory,
            uiState = uiState,
            imageLoader = imageLoader,
            onBack = onCloseDetailedView,
            onPlaySong = onPlaySong,
            onDownloadSong = onDownloadSong,
            onAddSongToLibrary = onAddSongToLibrary,
            onAddSongToPlaylistClick = onAddSongToPlaylistClick,
            onArtistClick = onArtistClick
        )
    } else {
        SummaryResultsView(
            uiState = uiState,
            imageLoader = imageLoader,
            onShowMoreClicked = onShowMoreClicked,
            onPlaySong = onPlaySong,
            onDownloadSong = onDownloadSong,
            onAddSongToLibrary = onAddSongToLibrary,
            onAddSongToPlaylistClick = onAddSongToPlaylistClick,
            onArtistClick = onArtistClick
        )
    }
}

@Composable
private fun SummaryResultsView(
    uiState: SearchUiState,
    imageLoader: ImageLoader,
    onShowMoreClicked: (SearchCategory) -> Unit,
    onPlaySong: (Int, List<SearchResult>) -> Unit,
    onDownloadSong: (SearchResult) -> Unit,
    onAddSongToLibrary: (SearchResult) -> Unit,
    onAddSongToPlaylistClick: (SearchResult) -> Unit,
    onArtistClick: (ArtistResult) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (uiState.songs.isNotEmpty()) {
            item(key = "songs_section") {
                SongResultSection(
                    songs = uiState.songs.take(4),
                    imageLoader = imageLoader,
                    onMoreClicked = { onShowMoreClicked(SearchCategory.SONGS) },
                    onPlay = { index -> onPlaySong(index, uiState.songs) },
                    onDownloadSong = onDownloadSong,
                    onAddSongToLibrary = onAddSongToLibrary,
                    onAddToPlaylistClick = onAddSongToPlaylistClick
                )
            }
        }

        if (uiState.artists.isNotEmpty()) {
            item(key = "artists_section") {
                ArtistResultSection(
                    artists = uiState.artists.take(1),
                    imageLoader = imageLoader,
                    onMoreClicked = { onShowMoreClicked(SearchCategory.ARTISTS) },
                    onArtistClicked = onArtistClick,
                    showMoreButton = uiState.artists.size > 1
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailedResultsView(
    category: SearchCategory,
    uiState: SearchUiState,
    imageLoader: ImageLoader,
    onBack: () -> Unit,
    onPlaySong: (Int, List<SearchResult>) -> Unit,
    onDownloadSong: (SearchResult) -> Unit,
    onAddSongToLibrary: (SearchResult) -> Unit,
    onAddSongToPlaylistClick: (SearchResult) -> Unit,
    onArtistClick: (ArtistResult) -> Unit
) {
    val title = when (category) {
        SearchCategory.SONGS -> "Songs"
        SearchCategory.ARTISTS -> "Artists"
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
                SearchCategory.SONGS -> {
                    itemsIndexed(uiState.songs, key = { index, item -> item.streamInfo.url + index }) { index, item ->
                        SearchResultItem(
                            result = item,
                            isSong = true,
                            imageLoader = imageLoader,
                            onPlay = { onPlaySong(index, uiState.songs) },
                            onDownload = { onDownloadSong(item) },
                            onAddToLibrary = { onAddSongToLibrary(item) },
                            onAddToPlaylistClick = { onAddSongToPlaylistClick(item) }
                        )
                    }
                }
                SearchCategory.ARTISTS -> {
                    items(uiState.artists, key = { it.artistInfo.url!! }) { item ->
                        ListItem(
                            modifier = Modifier.clickable { onArtistClick(item) },
                            headlineContent = { Text(item.artistInfo.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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

@Composable
private fun SongResultSection(
    songs: List<SearchResult>,
    imageLoader: ImageLoader,
    onMoreClicked: () -> Unit,
    onPlay: (Int) -> Unit,
    onDownloadSong: (SearchResult) -> Unit,
    onAddSongToLibrary: (SearchResult) -> Unit,
    onAddToPlaylistClick: (SearchResult) -> Unit
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Songs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onMoreClicked) { Text("More") }
        }
        songs.forEachIndexed { index, songResult ->
            SearchResultItem(
                result = songResult,
                isSong = true,
                imageLoader = imageLoader,
                onPlay = { onPlay(index) },
                onDownload = { onDownloadSong(songResult) },
                onAddToLibrary = { onAddSongToLibrary(songResult) },
                onAddToPlaylistClick = { onAddToPlaylistClick(songResult) }
            )
        }
    }
}

@Composable
private fun ArtistResultSection(
    artists: List<ArtistResult>,
    imageLoader: ImageLoader,
    onMoreClicked: () -> Unit,
    onArtistClicked: (ArtistResult) -> Unit,
    showMoreButton: Boolean
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Artists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (showMoreButton) {
                TextButton(onClick = onMoreClicked) { Text("More") }
            }
        }
        artists.forEach { artistResult ->
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
    }
}