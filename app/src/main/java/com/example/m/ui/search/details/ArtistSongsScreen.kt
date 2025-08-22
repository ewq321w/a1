package com.example.m.ui.search.details

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.data.database.Song
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.library.components.AddToPlaylistSheet
import com.example.m.ui.library.components.CreatePlaylistDialog
import com.example.m.ui.search.SearchResult
import com.example.m.ui.search.SearchResultItem
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistSongsScreen(
    onBack: () -> Unit,
    viewModel: ArtistSongsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val title = if (uiState.searchType == "music") "Popular" else "Videos"
    val allPlaylists by viewModel.allPlaylists.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var resultToDownload by remember { mutableStateOf<SearchResult?>(null) }

    val showCreatePlaylistDialog by remember { derivedStateOf { viewModel.showCreatePlaylistDialog } }
    val itemToAddToPlaylist by remember { derivedStateOf { viewModel.itemToAddToPlaylist } }

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
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.errorMessage != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text(uiState.errorMessage!!)
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                    itemsIndexed(uiState.songs, key = { index, item -> item.streamInfo.url + index }) { index, item ->
                        SearchResultItem(
                            result = item,
                            isSong = uiState.searchType == "music",
                            imageLoader = viewModel.imageLoader,
                            onPlay = { viewModel.onSongSelected(index) },
                            onDownload = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    resultToDownload = item
                                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.downloadSong(item)
                                }
                            },
                            onAddToLibrary = { viewModel.addSongToLibrary(item) },
                            onAddToPlaylistClick = { viewModel.selectItemForPlaylist(item) }
                        )
                    }
                }
            }
        }
    }
}