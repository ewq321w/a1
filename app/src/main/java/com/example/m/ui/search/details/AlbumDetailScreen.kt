package com.example.m.ui.search.details

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.m.R
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
fun AlbumDetailScreen(
    onBack: () -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
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
                title = { Text(uiState.albumInfo?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.errorMessage != null -> {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text(uiState.errorMessage!!)
                }
            }
            uiState.albumInfo != null -> {
                LazyColumn(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize(),
                ) {
                    item {
                        AlbumHeader(
                            thumbnailUrl = uiState.albumInfo?.thumbnails?.maxByOrNull { it.width }?.url,
                            albumName = uiState.albumInfo?.name ?: "Unknown Album",
                            artistName = uiState.albumInfo?.uploaderName ?: "Unknown Artist"
                        )
                    }

                    itemsIndexed(uiState.songs, key = { index, item -> item.streamInfo.url + index }) { index, searchResult ->
                        SearchResultItem(
                            result = searchResult,
                            isSong = uiState.searchType == "music",
                            imageLoader = viewModel.imageLoader,
                            onPlay = { viewModel.onSongSelected(index) },
                            onDownload = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    resultToDownload = searchResult
                                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.downloadSong(searchResult)
                                }
                            },
                            onAddToLibrary = { viewModel.addSongToLibrary(searchResult) },
                            onAddToPlaylistClick = { viewModel.selectItemForPlaylist(searchResult) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumHeader(thumbnailUrl: String?, albumName: String, artistName: String) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = "Album Thumbnail",
            modifier = Modifier.size(120.dp),
            contentScale = ContentScale.Crop,
            error = painterResource(id = R.drawable.placeholder_gray),
            placeholder = painterResource(id = R.drawable.placeholder_gray)
        )
        Column(
            modifier = Modifier.padding(start = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = albumName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = artistName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}