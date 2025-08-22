package com.example.m.ui.search

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.m.data.database.Song
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.library.components.AddToPlaylistSheet
import com.example.m.ui.library.components.CreatePlaylistDialog
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfoItem
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
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    var resultToDownload by remember { mutableStateOf<SearchResult?>(null) }

    val showCreatePlaylistDialog by remember { derivedStateOf { viewModel.showCreatePlaylistDialog } }
    val itemToAddToPlaylist by remember { derivedStateOf { viewModel.itemToAddToPlaylist } }

    if (uiState.detailedViewCategory != null) {
        BackHandler(enabled = true) {
            viewModel.closeDetailedView()
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            resultToDownload?.let { viewModel.downloadSong(it) }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Notification permission is required to see download progress.")
            }
        }
        resultToDownload = null
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.dismissCreatePlaylistDialog() },
            onCreate = { name ->
                viewModel.createPlaylistAndAddPendingItem(name)
            }
        )
    }

    val currentItemToAdd = itemToAddToPlaylist
    if (currentItemToAdd != null) {
        val songTitle: String
        val songArtist: String
        val thumbnailUrl: String

        when (currentItemToAdd) {
            is Song -> {
                songTitle = currentItemToAdd.title
                songArtist = currentItemToAdd.artist
                thumbnailUrl = currentItemToAdd.getHighQualityThumbnailUrl()
            }
            is StreamInfoItem -> {
                songTitle = currentItemToAdd.name ?: "Unknown"
                songArtist = currentItemToAdd.uploaderName ?: "Unknown"
                thumbnailUrl = currentItemToAdd.getHighQualityThumbnailUrl()
            }
            else -> {
                songTitle = ""; songArtist = ""; thumbnailUrl = ""
            }
        }

        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissAddToPlaylistSheet() },
            sheetState = sheetState
        ) {
            AddToPlaylistSheet(
                songTitle = songTitle,
                songArtist = songArtist,
                songThumbnailUrl = thumbnailUrl,
                playlists = allPlaylists,
                onPlaylistSelected = { playlistId ->
                    viewModel.onPlaylistSelectedForAddition(playlistId)
                },
                onCreateNewPlaylist = {
                    viewModel.prepareToCreatePlaylist()
                }
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.detailedViewCategory == null) {
                TopAppBar(
                    title = { Text("Search") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        val modifier = if (uiState.detailedViewCategory != null) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .padding(paddingValues)
                .fillMaxSize()
        }

        Column(modifier = modifier) {
            if (uiState.detailedViewCategory == null) {
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChanged = viewModel::onSearchQueryChanged,
                    onSearch = {
                        viewModel.executeSearch()
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                )
                FilterChipSection(
                    selectedFilter = uiState.selectedFilter,
                    onFilterSelected = viewModel::onFilterChanged
                )
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val onArtistClick: (ArtistResult) -> Unit = { artistResult ->
                    artistResult.artistInfo.url?.let { url ->
                        val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                        val searchType = if (uiState.selectedFilter == "music_songs") "music" else "video"
                        navController.navigate(
                            "searched_artist_detail/$searchType/$encodedUrl"
                        )
                    }
                }

                if (uiState.selectedFilter == "music_songs") {
                    MusicSearchResultsLayout(
                        uiState = uiState,
                        imageLoader = viewModel.imageLoader,
                        onShowMoreClicked = viewModel::showMore,
                        onCloseDetailedView = viewModel::closeDetailedView,
                        onPlaySong = viewModel::onSongSelected,
                        onDownloadSong = { result ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                resultToDownload = result
                                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.downloadSong(result)
                            }
                        },
                        onAddSongToLibrary = viewModel::addSongToLibrary,
                        onAddSongToPlaylistClick = { result -> viewModel.selectItemForPlaylist(result) },
                        onArtistClick = onArtistClick
                    )
                } else {
                    VideoSearchResultsLayout(
                        uiState = uiState,
                        imageLoader = viewModel.imageLoader,
                        onShowMoreClicked = viewModel::showMore,
                        onCloseDetailedView = viewModel::closeDetailedView,
                        onPlay = { index, items -> viewModel.onSongSelected(index, items) },
                        onDownload = { result ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                resultToDownload = result
                                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.downloadSong(result)
                            }
                        },
                        onAddToLibrary = viewModel::addSongToLibrary,
                        onAddToPlaylistClick = { result -> viewModel.selectItemForPlaylist(result) },
                        onChannelClick = onArtistClick
                    )
                }
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        label = { Text("Search YouTube") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        keyboardOptions = KeyboardOptions.Default.copy(
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(
            onSearch = {
                onSearch()
                keyboardController?.hide()
            }
        ),
        trailingIcon = {
            IconButton(onClick = {
                onSearch()
                keyboardController?.hide()
            }) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurface)
            }
        },
        colors = TextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
            disabledIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
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