// file: com/example/m/ui/search/SearchScreen.kt
package com.example.m.ui.search

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.ImageLoader
import coil.compose.AsyncImage
import com.example.m.managers.DialogState
import com.example.m.ui.common.GradientBackground
import com.example.m.ui.library.components.ArtistGroupConflictDialog
import com.example.m.ui.library.components.SelectLibraryGroupDialog
import com.example.m.ui.library.components.TextFieldDialog
import com.example.m.ui.main.MainViewModel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavHostController,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val songsWithStatus by viewModel.songsWithStatus.collectAsState()
    val videoStreamsWithStatus by viewModel.videoStreamsWithStatus.collectAsState()
    val dialogState by viewModel.dialogState.collectAsState()
    val activity = LocalContext.current as ComponentActivity
    val mainViewModel: MainViewModel = hiltViewModel(activity)
    val (gradientColor1, gradientColor2) = mainViewModel.randomGradientColors.value

    if (uiState.detailedViewCategory != null) {
        BackHandler {
            viewModel.onEvent(SearchEvent.HideDetailedView)
        }
    }

    when (val state = dialogState) {
        is DialogState.CreateGroup -> {
            TextFieldDialog(
                title = "New Library Group",
                label = "Group name",
                confirmButtonText = "Create",
                onDismiss = { viewModel.onEvent(SearchEvent.DismissDialog) },
                onConfirm = { name -> viewModel.onEvent(SearchEvent.CreateLibraryGroup(name)) },
                content = {
                    if (state.isFirstGroup) {
                        Text(
                            "To start your library, please create a group to add songs to.",
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            )
        }
        is DialogState.SelectGroup -> {
            SelectLibraryGroupDialog(
                groups = state.groups,
                onDismiss = { viewModel.onEvent(SearchEvent.DismissDialog) },
                onGroupSelected = { groupId -> viewModel.onEvent(SearchEvent.SelectGroup(groupId)) },
                onCreateNewGroup = { viewModel.onEvent(SearchEvent.RequestCreateGroup) }
            )
        }
        is DialogState.Conflict -> {
            ArtistGroupConflictDialog(
                artistName = state.song.artist,
                conflictingGroupName = state.conflict.conflictingGroupName,
                targetGroupName = state.targetGroupName,
                onDismiss = { viewModel.onEvent(SearchEvent.DismissDialog) },
                onMoveArtistToTargetGroup = { viewModel.onEvent(SearchEvent.ResolveConflict) }
            )
        }
        is DialogState.Hidden -> {}
    }



    if (uiState.detailedViewCategory != null) {
        DetailedView(
            category = uiState.detailedViewCategory!!,
            uiState = uiState,
            songsWithStatus = songsWithStatus,
            videoStreamsWithStatus = videoStreamsWithStatus,
            nowPlayingMediaId = uiState.nowPlayingMediaId,
            imageLoader = viewModel.imageLoader,
            onBack = { viewModel.onEvent(SearchEvent.HideDetailedView) },
            onSongClicked = { index -> viewModel.onEvent(SearchEvent.SongSelected(index)) },
            onLoadMore = { viewModel.onEvent(SearchEvent.LoadMore) },
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
            onAddToLibrary = { viewModel.onEvent(SearchEvent.AddToLibrary(it)) },
            onPlayNext = { viewModel.onEvent(SearchEvent.PlayNext(it)) },
            onAddToQueue = { viewModel.onEvent(SearchEvent.AddToQueue(it)) }
        )
    } else {
        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        var isSearchFocused by remember { mutableStateOf(false) }

        // Handle back press: close suggestions first, then navigate back
        BackHandler(enabled = isSearchFocused || uiState.showSuggestions) {
            if (isSearchFocused || uiState.showSuggestions) {
                // Close suggestions and keyboard
                viewModel.onEvent(SearchEvent.HideSuggestions)
                keyboardController?.hide()
                focusManager.clearFocus()
            }
        }

        // Show suggestions when field gains focus
        LaunchedEffect(isSearchFocused) {
            if (isSearchFocused) {
                viewModel.onEvent(SearchEvent.ShowSuggestions)
            } else {
                viewModel.onEvent(SearchEvent.HideSuggestions)
            }
        }

        GradientBackground(
            gradientColor1 = gradientColor1,
            gradientColor2 = gradientColor2
        ) {
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    TopAppBar(
                        title = {
                            val interactionSource = remember { MutableInteractionSource() }
                            val searchTextStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp
                            )
                            val placeholderTextStyle = searchTextStyle.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            BasicTextField(
                                value = uiState.query,
                                onValueChange = {
                                    viewModel.onEvent(SearchEvent.QueryChange(it))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 16.dp)
                                    .height(42.dp)
                                    .onFocusChanged { focusState ->
                                        isSearchFocused = focusState.isFocused
                                        if (focusState.isFocused) {
                                            viewModel.onEvent(SearchEvent.ShowSuggestions)
                                        } else {
                                            viewModel.onEvent(SearchEvent.HideSuggestions)
                                        }
                                    },
                                textStyle = searchTextStyle,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    viewModel.onEvent(SearchEvent.Search)
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }),
                                interactionSource = interactionSource,
                                decorationBox = { innerTextField ->
                                    TextFieldDefaults.DecorationBox(
                                        value = uiState.query,
                                        innerTextField = innerTextField,
                                        enabled = true,
                                        singleLine = true,
                                        visualTransformation = VisualTransformation.None,
                                        interactionSource = interactionSource,
                                        placeholder = { Text("Search", style = placeholderTextStyle) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Search, contentDescription = "Search Icon")
                                        },
                                        shape = CircleShape,
                                        colors = TextFieldDefaults.colors(
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            disabledIndicatorColor = Color.Transparent,
                                            focusedContainerColor = Color.White.copy(alpha = 0.1f),
                                            unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                                            disabledContainerColor = Color.White.copy(alpha = 0.1f),
                                            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    )
                                }
                            )
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        )
                    )
                },
                containerColor = Color.Transparent
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Show suggestions when:
                    // 1. Search field is FOCUSED (keyboard active) AND showSuggestions=true
                    // 2. OR when on initial empty page (no query, no results) AND not focused
                    val showInitialSuggestions = uiState.query.isEmpty() &&
                                                 uiState.songs.isEmpty() &&
                                                 uiState.videoStreams.isEmpty() &&
                                                 !uiState.isLoading &&
                                                 !isSearchFocused

                    val showActiveSuggestions = isSearchFocused &&
                                                uiState.showSuggestions &&
                                                !uiState.isLoading

                    if ((showActiveSuggestions || showInitialSuggestions)) {
                        if (uiState.suggestions.isNotEmpty() || uiState.recentSearches.isNotEmpty() || showInitialSuggestions) {
                            SearchSuggestionsDropdown(
                                suggestions = uiState.suggestions,
                                recentSearches = uiState.recentSearches,
                                onSuggestionClick = { suggestion ->
                                    viewModel.onEvent(SearchEvent.SelectSuggestion(suggestion))
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                },
                                onDeleteHistory = { id ->
                                    viewModel.onEvent(SearchEvent.DeleteSearchHistory(id))
                                }
                            )
                        }
                    }

                    FilterChipSection(
                        selectedFilter = uiState.selectedFilter,
                        onFilterSelected = { viewModel.onEvent(SearchEvent.FilterChange(it)) }
                    )

                    if (uiState.isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    viewModel.onEvent(SearchEvent.HideSuggestions)
                                    focusManager.clearFocus()
                                }
                        ) {
                            if (uiState.selectedFilter == "music_songs") {
                            MusicSearchLayout(
                                uiState = uiState,
                                songsWithStatus = songsWithStatus,
                                nowPlayingMediaId = uiState.nowPlayingMediaId,
                                imageLoader = viewModel.imageLoader,
                                onSongClicked = { index -> viewModel.onEvent(SearchEvent.SongSelected(index)) },
                                onAlbumClicked = { albumResult ->
                                    val encodedUrl = URLEncoder.encode(albumResult.albumInfo.url, StandardCharsets.UTF_8.toString())
                                    navController.navigate("album_detail/music/$encodedUrl")
                                },
                                onArtistClicked = { artistResult ->
                                    val encodedUrl = URLEncoder.encode(artistResult.artistInfo.url, StandardCharsets.UTF_8.toString())
                                    navController.navigate("searched_artist_detail/music/$encodedUrl")
                                },
                                onShowMore = { viewModel.onEvent(SearchEvent.ShowDetailedView(it)) },
                                onAddToLibrary = { viewModel.onEvent(SearchEvent.AddToLibrary(it)) },
                                onPlayNext = { viewModel.onEvent(SearchEvent.PlayNext(it)) },
                                onAddToQueue = { viewModel.onEvent(SearchEvent.AddToQueue(it)) },
                                onShuffleAlbum = { viewModel.onEvent(SearchEvent.ShuffleAlbum(it)) }
                            )
                        } else {
                            VideoSearchLayout(
                                uiState = uiState,
                                videoStreamsWithStatus = videoStreamsWithStatus,
                                nowPlayingMediaId = uiState.nowPlayingMediaId,
                                imageLoader = viewModel.imageLoader,
                                onVideoClick = { index -> viewModel.onEvent(SearchEvent.SongSelected(index)) },
                                onPlaylistClick = { albumResult ->
                                    val encodedUrl = URLEncoder.encode(albumResult.albumInfo.url, StandardCharsets.UTF_8.toString())
                                    navController.navigate("album_detail/video/$encodedUrl")
                                },
                                onChannelClick = { artistResult ->
                                    val encodedUrl = URLEncoder.encode(artistResult.artistInfo.url, StandardCharsets.UTF_8.toString())
                                    navController.navigate("searched_artist_detail/video/$encodedUrl")
                                },
                                onShowMore = { viewModel.onEvent(SearchEvent.ShowDetailedView(it)) },
                                onAddToLibrary = { viewModel.onEvent(SearchEvent.AddToLibrary(it)) },
                                onPlayNext = { viewModel.onEvent(SearchEvent.PlayNext(it)) },
                                onAddToQueue = { viewModel.onEvent(SearchEvent.AddToQueue(it)) }
                            )
                            }
                        }
                    }
                }
            }
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
    nowPlayingMediaId: String?,
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
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                windowInsets = TopAppBarDefaults.windowInsets
            )
        }
    ) { paddingValues ->
        LazyColumn(state = listState, modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (category) {
                SearchCategory.SONGS, SearchCategory.VIDEOS -> {
                    val itemsToShow = if (category == SearchCategory.SONGS) songsWithStatus else videoStreamsWithStatus
                    itemsIndexed(itemsToShow, key = { index, item -> (item.result.streamInfo.url ?: "") + index }) { index, item ->
                        val normalizedUrl = item.result.streamInfo.url?.replace("music.youtube.com", "www.youtube.com")
                        val isPlaying = normalizedUrl == nowPlayingMediaId || item.localSong?.localFilePath == nowPlayingMediaId
                        SearchResultItem(
                            result = item.result,
                            localSong = item.localSong,
                            isPlaying = isPlaying,
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
                        AlbumOrPlaylistItem(
                            item = item.albumInfo,
                            imageLoader = imageLoader,
                            onClicked = { onAlbumClicked(item) }
                        )
                    }
                }
                SearchCategory.ARTISTS, SearchCategory.CHANNELS -> {
                    val items = if (uiState.selectedFilter == "music_songs") uiState.artists else uiState.videoChannels
                    itemsIndexed(items, key = { index, item -> (item.artistInfo.url ?: "") + index }) { _, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onArtistClicked(item) }
                                .heightIn(min = 68.dp) // Use minimum height instead of fixed height
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = item.artistInfo.thumbnails.lastOrNull()?.url,
                                imageLoader = imageLoader,
                                contentDescription = item.artistInfo.name,
                                modifier = Modifier.size(54.dp).clip(CircleShape)
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
                item { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
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
fun FilterChipSection(selectedFilter: String, onFilterSelected: (String) -> Unit) {
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = Color.White.copy(alpha = 0.15f),
        selectedLabelColor = Color.White,
        selectedLeadingIconColor = Color.White
    )

    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val isSongsSelected = selectedFilter == "music_songs"
        FilterChip(
            selected = isSongsSelected,
            onClick = { onFilterSelected("music_songs") },
            label = { Text("Songs") },
            leadingIcon = if (isSongsSelected) { { Icon(Icons.Default.Done, contentDescription = "Selected") } } else null,
            colors = chipColors,
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = isSongsSelected,
                borderColor = Color.White.copy(alpha = 0.2f),
                selectedBorderColor = Color.White.copy(alpha = 0.2f)
            )
        )

        val isVideosSelected = selectedFilter == "all"
        FilterChip(
            selected = isVideosSelected,
            onClick = { onFilterSelected("all") },
            label = { Text("Videos") },
            leadingIcon = if (isVideosSelected) { { Icon(Icons.Default.Done, contentDescription = "Selected") } } else null,
            colors = chipColors,
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = isVideosSelected,
                borderColor = Color.White.copy(alpha = 0.2f),
                selectedBorderColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
fun SearchSuggestionsDropdown(
    suggestions: List<String>,
    recentSearches: List<com.example.m.data.database.SearchHistory>,
    onSuggestionClick: (String) -> Unit,
    onDeleteHistory: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Recent searches section (when no active suggestions from typing)
        if (recentSearches.isNotEmpty() && suggestions.isEmpty()) {
            item {
                Text(
                    text = "Recent Searches",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                )
            }
            items(recentSearches.take(10)) { history ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSuggestionClick(history.query) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = history.query,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    androidx.compose.material3.IconButton(
                        onClick = { onDeleteHistory(history.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Close,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Empty state when no history and no suggestions
        if (recentSearches.isEmpty() && suggestions.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Search for songs, artists, albums...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your recent searches will appear here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Active suggestions (when typing)
        if (suggestions.isNotEmpty()) {
            items(suggestions) { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSuggestionClick(suggestion) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

