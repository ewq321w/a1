package com.example.m.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.ImageLoader
import coil.compose.AsyncImage
import com.example.m.ui.common.PlaylistActionUI
import com.example.m.ui.common.getThumbnail
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavHostController,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()
    val songsWithStatus by viewModel.songsWithStatus.collectAsState()
    val videoStreamsWithStatus by viewModel.videoStreamsWithStatus.collectAsState()

    if (uiState.detailedViewCategory != null) {
        BackHandler {
            viewModel.hideDetailedView()
        }
    }

    PlaylistActionUI(
        handler = viewModel.playlistActionHandler,
        allPlaylists = allPlaylists
    )

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        if (uiState.detailedViewCategory == null) {
            // Main search view with search bar and filters
            SearchBar(
                query = uiState.query,
                onQueryChanged = viewModel::onQueryChange,
                onSearch = viewModel::search
            )
            FilterChipSection(
                selectedFilter = uiState.selectedFilter,
                onFilterSelected = viewModel::onFilterChange
            )

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                if (uiState.selectedFilter == "music_songs") {
                    MusicSearchLayout(
                        uiState = uiState,
                        songsWithStatus = songsWithStatus,
                        imageLoader = viewModel.imageLoader,
                        onSongClicked = { index -> viewModel.onSongSelected(index) },
                        onAlbumClicked = { albumResult ->
                            val encodedUrl = URLEncoder.encode(albumResult.albumInfo.url, StandardCharsets.UTF_8.toString())
                            navController.navigate("album_detail/music/$encodedUrl")
                        },
                        onArtistClicked = { artistResult ->
                            val encodedUrl = URLEncoder.encode(artistResult.artistInfo.url, StandardCharsets.UTF_8.toString())
                            navController.navigate("searched_artist_detail/music/$encodedUrl")
                        },
                        onShowMore = viewModel::showDetailedView,
                        onDownloadSong = viewModel::downloadSong,
                        onAddToLibrary = viewModel::addSongToLibrary,
                        onAddToPlaylist = { result -> viewModel.playlistActionHandler.selectItemForPlaylist(result) }
                    )
                } else {
                    VideoSearchLayout(
                        uiState = uiState,
                        videoStreamsWithStatus = videoStreamsWithStatus,
                        imageLoader = viewModel.imageLoader,
                        onVideoClick = { index -> viewModel.onSongSelected(index) },
                        onPlaylistClick = { albumResult ->
                            val encodedUrl = URLEncoder.encode(albumResult.albumInfo.url, StandardCharsets.UTF_8.toString())
                            navController.navigate("album_detail/video/$encodedUrl")
                        },
                        onChannelClick = { artistResult ->
                            val encodedUrl = URLEncoder.encode(artistResult.artistInfo.url, StandardCharsets.UTF_8.toString())
                            navController.navigate("searched_artist_detail/video/$encodedUrl")
                        },
                        onShowMore = viewModel::showDetailedView,
                        onDownloadSong = viewModel::downloadSong,
                        onAddToLibrary = viewModel::addSongToLibrary,
                        onAddToPlaylist = { result -> viewModel.playlistActionHandler.selectItemForPlaylist(result) }
                    )
                }
            }
        } else {
            // Full-screen detailed view
            DetailedView(
                category = uiState.detailedViewCategory!!,
                uiState = uiState,
                songsWithStatus = songsWithStatus,
                videoStreamsWithStatus = videoStreamsWithStatus,
                imageLoader = viewModel.imageLoader,
                onBack = viewModel::hideDetailedView,
                onSongClicked = { index -> viewModel.onSongSelected(index) },
                onAlbumClicked = { albumResult ->
                    val encodedUrl = URLEncoder.encode(albumResult.albumInfo.url, StandardCharsets.UTF_8.toString())
                    val searchType = if (uiState.selectedFilter == "music_songs") "music" else "video"
                    navController.navigate("album_detail/$searchType/$encodedUrl")
                },
                onArtistClicked = { artistResult ->
                    val encodedUrl = URLEncoder.encode(artistResult.artistInfo.url, StandardCharsets.UTF_8.toString())
                    val searchType = if (uiState.selectedFilter == "music_songs") "music" else "video"
                    navController.navigate("searched_artist_detail/$searchType/$encodedUrl")
                },
                onDownloadSong = viewModel::downloadSong,
                onAddToLibrary = viewModel::addSongToLibrary,
                onAddToPlaylist = { result -> viewModel.playlistActionHandler.selectItemForPlaylist(result) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailedView(
    category: SearchCategory,
    uiState: SearchUiState,
    songsWithStatus: List<SearchResultForList>,
    videoStreamsWithStatus: List<SearchResultForList>,
    imageLoader: ImageLoader,
    onBack: () -> Unit,
    onSongClicked: (Int) -> Unit,
    onAlbumClicked: (AlbumResult) -> Unit,
    onArtistClicked: (ArtistResult) -> Unit,
    onDownloadSong: (SearchResult) -> Unit,
    onAddToLibrary: (SearchResult) -> Unit,
    onAddToPlaylist: (SearchResult) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category.name.lowercase().replaceFirstChar { it.titlecase() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            when (category) {
                SearchCategory.SONGS -> {
                    itemsIndexed(songsWithStatus, key = { _, item -> item.result.streamInfo.url!! }) { index, item ->
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
                SearchCategory.ALBUMS -> {
                    val items = if (uiState.selectedFilter == "music_songs") uiState.albums else uiState.videoPlaylists
                    items(items, key = { it.albumInfo.url!! }) { item ->
                        ListItem(
                            modifier = Modifier.clickable { onAlbumClicked(item) },
                            headlineContent = { Text(item.albumInfo.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { if (item.albumInfo.uploaderName != null) { Text(item.albumInfo.uploaderName, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                            leadingContent = { AsyncImage( model = item.albumInfo.getThumbnail(), imageLoader = imageLoader, contentDescription = item.albumInfo.name, modifier = Modifier.size(50.dp)) }
                        )
                    }
                }
                SearchCategory.ARTISTS, SearchCategory.CHANNELS -> {
                    val items = if (uiState.selectedFilter == "music_songs") uiState.artists else uiState.videoChannels
                    items(items, key = { it.artistInfo.url!! }) { item ->
                        ListItem(
                            modifier = Modifier.clickable { onArtistClicked(item) },
                            headlineContent = { Text(item.artistInfo.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { val subs = formatSubscriberCount(item.artistInfo.subscriberCount); if (subs.isNotEmpty()) { Text(text = subs) } },
                            leadingContent = { AsyncImage( model = item.artistInfo.thumbnails.lastOrNull()?.url, imageLoader = imageLoader, contentDescription = item.artistInfo.name, modifier = Modifier.size(50.dp).clip(CircleShape)) }
                        )
                    }
                }
                SearchCategory.VIDEOS -> {
                    itemsIndexed(videoStreamsWithStatus, key = { _, item -> item.result.streamInfo.url!! }) { index, item ->
                        SearchResultItem(
                            result = item.result,
                            downloadStatus = item.downloadStatus,
                            isSong = false,
                            imageLoader = imageLoader,
                            onPlay = { onSongClicked(index) },
                            onDownload = { onDownloadSong(item.result) },
                            onAddToLibrary = { onAddToLibrary(item.result) },
                            onAddToPlaylistClick = { onAddToPlaylist(item.result) }
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun SearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        label = { Text("Search") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            onSearch()
            keyboardController?.hide()
            focusManager.clearFocus()
        }),
        colors = OutlinedTextFieldDefaults.colors()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChipSection(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter == "music_songs",
            onClick = { onFilterSelected("music_songs") },
            label = { Text("Songs") },
            leadingIcon = if (selectedFilter == "music_songs") {
                { Icon(Icons.Default.Done, contentDescription = "Selected") }
            } else {
                null
            }
        )
        FilterChip(
            selected = selectedFilter == "all",
            onClick = { onFilterSelected("all") },
            label = { Text("Videos") },
            leadingIcon = if (selectedFilter == "all") {
                { Icon(Icons.Default.Done, contentDescription = "Selected") }
            } else {
                null
            }
        )
    }
}