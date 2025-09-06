// file: com/example/m/ui/search/SearchScreen.kt
package com.example.m.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.ImageLoader
import coil.compose.AsyncImage
import com.example.m.data.database.LibraryGroup
import com.example.m.ui.common.PlaylistActionUI
import com.example.m.ui.common.getThumbnail
import com.example.m.ui.library.components.ArtistGroupConflictDialog
import com.example.m.ui.library.components.CreateLibraryGroupDialog
import com.example.m.ui.library.components.SelectLibraryGroupDialog
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
    val libraryGroups by viewModel.libraryGroups.collectAsState()
    val songsWithStatus by viewModel.songsWithStatus.collectAsState()
    val videoStreamsWithStatus by viewModel.videoStreamsWithStatus.collectAsState()
    val conflictDialogState by remember { derivedStateOf { viewModel.conflictDialogState } }
    val showCreateGroupDialog by remember { derivedStateOf { viewModel.showCreateGroupDialog } }
    val showSelectGroupDialog by remember { derivedStateOf { viewModel.showSelectGroupDialog } }


    if (uiState.detailedViewCategory != null) {
        BackHandler {
            viewModel.hideDetailedView()
        }
    }

    if (showCreateGroupDialog) {
        CreateLibraryGroupDialog(
            onDismiss = { viewModel.dismissCreateGroupDialog() },
            onCreate = { name -> viewModel.createGroupAndProceed(name) },
            isFirstGroup = libraryGroups.isEmpty()
        )
    }

    if (showSelectGroupDialog) {
        SelectLibraryGroupDialog(
            groups = libraryGroups,
            onDismiss = { viewModel.dismissSelectGroupDialog() },
            onGroupSelected = { groupId -> viewModel.onGroupSelectedForAddition(groupId) },
            onCreateNewGroup = viewModel::prepareToCreateGroup
        )
    }

    conflictDialogState?.let { state ->
        ArtistGroupConflictDialog(
            artistName = state.song.artist,
            conflictingGroupName = state.conflict.conflictingGroupName,
            targetGroupName = state.targetGroupName,
            onDismiss = { viewModel.dismissConflictDialog() },
            onMoveArtistToTargetGroup = { viewModel.resolveConflictByMoving() }
        )
    }

    PlaylistActionUI(
        handler = viewModel.playlistActionHandler,
        allPlaylists = allPlaylists,
        onCreatePlaylist = { name ->
            viewModel.handlePlaylistCreation(name)
        }
    )

    Scaffold(
        topBar = {
            if (uiState.detailedViewCategory == null) {
                val keyboardController = LocalSoftwareKeyboardController.current
                val focusManager = LocalFocusManager.current
                TopAppBar(
                    title = {
                        val searchTextStyle = TextStyle(fontSize = 16.sp)
                        TextField(
                            value = uiState.query,
                            onValueChange = viewModel::onQueryChange,
                            placeholder = { Text("Search", style = searchTextStyle) },
                            leadingIcon = {
                                IconButton(onClick = {
                                    viewModel.search()
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search Icon")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                viewModel.search()
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }),
                            textStyle = searchTextStyle,
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                disabledContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    windowInsets = TopAppBarDefaults.windowInsets
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (uiState.detailedViewCategory == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
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
                            onAddToLibrary = viewModel::addSongToLibrary,
                            onPlayNext = viewModel::onPlayNext,
                            onAddToQueue = viewModel::onAddToQueue,
                            onShuffleAlbum = viewModel::onShuffleAlbum
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
                            onAddToLibrary = viewModel::addSongToLibrary,
                            onPlayNext = viewModel::onPlayNext,
                            onAddToQueue = viewModel::onAddToQueue
                        )
                    }
                }
            }
        } else {
            DetailedView(
                category = uiState.detailedViewCategory!!,
                uiState = uiState,
                songsWithStatus = songsWithStatus,
                videoStreamsWithStatus = videoStreamsWithStatus,
                imageLoader = viewModel.imageLoader,
                onBack = viewModel::hideDetailedView,
                onSongClicked = { index -> viewModel.onSongSelected(index) },
                onLoadMore = viewModel::loadMoreResults,
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
                onAddToLibrary = viewModel::addSongToLibrary,
                onPlayNext = viewModel::onPlayNext,
                onAddToQueue = viewModel::onAddToQueue
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
    onLoadMore: () -> Unit,
    onAlbumClicked: (AlbumResult) -> Unit,
    onArtistClicked: (ArtistResult) -> Unit,
    onAddToLibrary: (SearchResult) -> Unit,
    onPlayNext: (SearchResult) -> Unit,
    onAddToQueue: (SearchResult) -> Unit
) {
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = when (category) {
                        SearchCategory.ALBUMS -> "Albums"
                        SearchCategory.PLAYLISTS -> "Playlists"
                        SearchCategory.ARTISTS -> "Artists"
                        SearchCategory.CHANNELS -> "Channels"
                        else -> category.name.lowercase().replaceFirstChar { it.titlecase() }
                    }
                    Text(titleText)
                },
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
            state = listState,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            when (category) {
                SearchCategory.SONGS, SearchCategory.VIDEOS -> {
                    val itemsToShow = if (category == SearchCategory.SONGS) songsWithStatus else videoStreamsWithStatus
                    itemsIndexed(itemsToShow, key = { index, item -> (item.result.streamInfo.url ?: "") + index }) { index, item ->
                        SearchResultItem(
                            result = item.result,
                            downloadStatus = item.downloadStatus,
                            isSong = category == SearchCategory.SONGS,
                            imageLoader = imageLoader,
                            onPlay = { onSongClicked(index) },
                            onAddToLibrary = { onAddToLibrary(item.result) },
                            onPlayNext = { onPlayNext(item.result) },
                            onAddToQueue = { onAddToQueue(item.result) }
                        )
                    }
                }
                SearchCategory.ALBUMS, SearchCategory.PLAYLISTS -> {
                    val items = if (category == SearchCategory.ALBUMS) uiState.albums else uiState.playlists
                    itemsIndexed(items, key = { index, item -> (item.albumInfo.url ?: "") + index }) { _, item ->
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
                SearchCategory.ARTISTS, SearchCategory.CHANNELS -> {
                    val items = if (uiState.selectedFilter == "music_songs") uiState.artists else uiState.videoChannels
                    itemsIndexed(items, key = { index, item -> (item.artistInfo.url ?: "") + index }) { _, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onArtistClicked(item) }
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

            if (uiState.isLoadingMore) {
                item {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        val layoutInfo = remember { derivedStateOf { listState.layoutInfo } }
        LaunchedEffect(layoutInfo.value.visibleItemsInfo) {
            val totalItems = layoutInfo.value.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.value.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (totalItems > 0 && lastVisibleItemIndex >= totalItems - 5) {
                onLoadMore()
            }
        }
    }
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
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
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