package com.example.m.ui.search.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import com.example.m.R
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.library.components.AddToPlaylistSheet
import com.example.m.ui.library.components.CreatePlaylistDialog
import com.example.m.ui.search.SearchResultItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import com.example.m.ui.common.getAvatar
import com.example.m.ui.common.getBanner
import com.example.m.ui.common.getThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchedArtistDetailScreen(
    onBack: () -> Unit,
    onAlbumClick: (searchType: String, albumUrl: String) -> Unit,
    onGoToSongs: (searchType: String, channelUrl: String) -> Unit,
    onGoToAlbums: (searchType: String, channelUrl: String) -> Unit,
    viewModel: SearchedArtistDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    val showCreatePlaylistDialog by remember { derivedStateOf { viewModel.showCreatePlaylistDialog } }
    val itemToAddToPlaylist by remember { derivedStateOf { viewModel.itemToAddToPlaylist } }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.dismissCreatePlaylistDialog() },
            onCreate = { name -> viewModel.createPlaylistAndAddPendingItem(name) }
        )
    }

    val currentItemToAdd = itemToAddToPlaylist
    if (currentItemToAdd != null && currentItemToAdd is StreamInfoItem) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissAddToPlaylistSheet() },
            sheetState = sheetState
        ) {
            AddToPlaylistSheet(
                songTitle = currentItemToAdd.name ?: "Unknown",
                songArtist = currentItemToAdd.uploaderName ?: "Unknown",
                songThumbnailUrl = currentItemToAdd.getHighQualityThumbnailUrl(),
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
                title = { Text(uiState.channelInfo?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
            else -> {
                LazyColumn(modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()) {
                    item {
                        ArtistHeader(
                            bannerUrl = uiState.channelInfo?.getBanner(),
                            avatarUrl = uiState.channelInfo?.getAvatar(),
                            artistName = uiState.channelInfo?.name ?: "Unknown Artist"
                        )
                    }

                    if (uiState.songs.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val headerText = if (uiState.searchType == "music") "Popular" else "Videos"
                                Text(
                                    text = headerText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = {
                                    uiState.channelInfo?.url?.let {
                                        onGoToSongs(uiState.searchType, it)
                                    }
                                }) {
                                    Text("More")
                                }
                            }
                        }
                        items(uiState.songs.take(4)) { item ->
                            val index = uiState.songs.indexOf(item)
                            SearchResultItem(
                                result = item.result,
                                downloadStatus = item.downloadStatus,
                                isSong = uiState.searchType == "music",
                                imageLoader = viewModel.imageLoader,
                                onPlay = { viewModel.onSongSelected(index) },
                                onDownload = { viewModel.downloadSong(item.result.streamInfo) },
                                onAddToLibrary = { viewModel.addSongToLibrary(item.result.streamInfo) },
                                onAddToPlaylistClick = { viewModel.selectItemForPlaylist(item.result.streamInfo) }
                            )
                        }
                    }

                    if (uiState.albums.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val headerText = if (uiState.searchType == "music") "Albums" else "Playlists"
                                Text(
                                    text = headerText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = {
                                    uiState.channelInfo?.url?.let {
                                        onGoToAlbums(uiState.searchType, it)
                                    }
                                }) {
                                    Text("More")
                                }
                            }

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(uiState.albums.take(10)) { album ->
                                    AlbumItem(
                                        album = album,
                                        imageLoader = viewModel.imageLoader,
                                        onClick = { album.url?.let { onAlbumClick(uiState.searchType, it) } }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistHeader(bannerUrl: String?, avatarUrl: String?, artistName: String) {
    Box(modifier = Modifier
        .fillMaxWidth()
        .height(200.dp)) {
        AsyncImage(
            model = bannerUrl,
            contentDescription = "Artist Banner",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            error = painterResource(id = R.drawable.placeholder_gray),
            placeholder = painterResource(id = R.drawable.placeholder_gray)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.Start
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Artist Avatar",
                modifier = Modifier.size(80.dp),
                placeholder = painterResource(id = R.drawable.placeholder_gray),
                error = painterResource(id = R.drawable.placeholder_gray)
            )
            Text(
                text = artistName,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AlbumItem(album: PlaylistInfoItem, imageLoader: ImageLoader, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        AsyncImage(
            model = album.getThumbnail(),
            imageLoader = imageLoader,
            contentDescription = album.name,
            modifier = Modifier.size(140.dp),
            contentScale = ContentScale.Crop,
            error = painterResource(id = R.drawable.placeholder_gray),
            placeholder = painterResource(id = R.drawable.placeholder_gray)
        )
        Text(
            text = album.name ?: "Unknown Album",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = album.uploaderName ?: "Unknown Artist",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}