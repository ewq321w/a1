package com.example.m.ui.library.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.m.ui.library.components.EmptyStateMessage
import com.example.m.ui.library.tabs.ArtistItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistGroupDetailScreen(
    onBack: () -> Unit,
    onArtistClick: (Long) -> Unit,
    onEditArtistSongs: (Long) -> Unit,
    viewModel: ArtistGroupDetailViewModel = hiltViewModel()
) {
    val groupWithArtists by viewModel.groupWithArtists.collectAsState()
    val artistsInGroup by viewModel.artistsInGroup.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()

    val showCreatePlaylistDialog by remember { derivedStateOf { viewModel.showCreatePlaylistDialog } }
    val itemToAddToPlaylist by remember { derivedStateOf { viewModel.itemToAddToPlaylist } }
    val sheetState = rememberModalBottomSheetState()

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.dismissCreatePlaylistDialog() },
            onCreate = { name -> viewModel.createPlaylistAndAddPendingItem(name) }
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
                title = { Text(groupWithArtists?.group?.name ?: "Group", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (artistsInGroup.isEmpty()) {
            val message = if (groupWithArtists == null) "Loading..." else "This group is empty."
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                if (groupWithArtists == null) {
                    CircularProgressIndicator()
                } else {
                    EmptyStateMessage(message = message)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                items(artistsInGroup, key = { it.artist.artistId }) { artistWithSongs ->
                    val artist = artistWithSongs.artist
                    ArtistItem(
                        artistWithSongs = artistWithSongs,
                        onClick = { onArtistClick(artist.artistId) },
                        onPlay = { viewModel.playArtist(artist) },
                        onShuffle = { viewModel.shuffleArtist(artist) },
                        onEdit = { onEditArtistSongs(artist.artistId) },
                        onToggleAutoDownload = { viewModel.toggleAutoDownloadForArtist(artist) },
                        groupAction = "Remove from group" to { viewModel.removeArtistFromGroup(artist) },
                        onHideArtist = { viewModel.hideArtist(artist) },
                        processUrls = viewModel::processThumbnails
                    )
                }
            }
        }
    }
}