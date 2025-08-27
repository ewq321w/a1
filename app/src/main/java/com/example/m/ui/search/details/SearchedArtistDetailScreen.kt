package com.example.m.ui.search.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchedArtistDetailScreen(
    onBack: () -> Unit,
    onAlbumClick: (searchType: String, albumUrl: String) -> Unit,
    onGoToSongs: (searchType: String, channelUrl: String, artistName: String) -> Unit,
    onGoToReleases: (searchType: String, channelUrl: String, artistName: String) -> Unit,
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

    Scaffold { paddingValues ->
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
                            onBack = onBack,
                            bannerUrl = uiState.channelInfo?.getBanner(),
                            avatarUrl = uiState.channelInfo?.getAvatar(),
                            artistName = uiState.channelInfo?.name ?: "Unknown Artist",
                            subscriberCount = uiState.channelInfo?.subscriberCount ?: -1
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
                                val headerText = if (uiState.searchType == "music") "Popular Songs" else "Videos"
                                Text(
                                    text = headerText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = {
                                    val info = uiState.channelInfo
                                    if (info?.url != null && info.name != null) {
                                        onGoToSongs(uiState.searchType, info.url, info.name)
                                    }
                                }) {
                                    Text("More")
                                }
                            }
                        }
                        items(uiState.songs.take(5)) { item ->
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

                    if (uiState.releases.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val headerText = if (uiState.searchType == "music") "Releases" else "Playlists"
                                Text(
                                    text = headerText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = {
                                    val info = uiState.channelInfo
                                    if (info?.url != null && info.name != null) {
                                        onGoToReleases(uiState.searchType, info.url, info.name)
                                    }
                                }) {
                                    Text("More")
                                }
                            }

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(uiState.releases.take(10)) { release ->
                                    AlbumItem(
                                        album = release,
                                        imageLoader = viewModel.imageLoader,
                                        onClick = { release.url?.let { onAlbumClick(uiState.searchType, it) } }
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
private fun ArtistHeader(
    onBack: () -> Unit,
    bannerUrl: String?,
    avatarUrl: String?,
    artistName: String,
    subscriberCount: Long
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box {
            AsyncImage(
                model = bannerUrl,
                contentDescription = "Artist Banner",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1060f / 175f),
                contentScale = ContentScale.Crop,
                error = painterResource(id = R.drawable.placeholder_gray),
                placeholder = painterResource(id = R.drawable.placeholder_gray)
            )
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Artist Avatar",
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape),
                placeholder = painterResource(id = R.drawable.placeholder_gray),
                error = painterResource(id = R.drawable.placeholder_gray)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subscriberCount >= 0) {
                    Text(
                        text = formatSubscriberCount(subscriberCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumItem(album: PlaylistInfoItem, imageLoader: ImageLoader, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        AsyncImage(
            model = album.getThumbnail(),
            imageLoader = imageLoader,
            contentDescription = album.name,
            modifier = Modifier.size(130.dp),
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
    }
}

private fun formatSubscriberCount(count: Long): String {
    if (count < 0) return ""
    if (count < 1000) return "$count subscribers"
    val thousands = count / 1000.0
    if (count < 1_000_000) {
        return "${DecimalFormat("0.#").format(thousands)}K subscribers"
    }
    val millions = count / 1_000_000.0
    return "${DecimalFormat("0.##").format(millions)}M subscribers"
}