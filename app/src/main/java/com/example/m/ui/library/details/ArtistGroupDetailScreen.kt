// file: com/example/m/ui/library/details/ArtistGroupDetailScreen.kt
package com.example.m.ui.library.details

import androidx.activity.ComponentActivity
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.data.database.Song
import com.example.m.ui.common.GradientBackground
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.library.components.AddToPlaylistSheet
import com.example.m.ui.library.components.EmptyStateMessage
import com.example.m.ui.library.components.TextFieldDialog
import com.example.m.ui.library.tabs.ArtistItem
import com.example.m.ui.main.MainViewModel
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistGroupDetailScreen(
    onBack: () -> Unit,
    onArtistClick: (Long) -> Unit,
    onEditArtistSongs: (Long) -> Unit,
    viewModel: ArtistGroupDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val activity = LocalContext.current as ComponentActivity
    val mainViewModel: MainViewModel = hiltViewModel(activity)
    val (gradientColor1, gradientColor2) = mainViewModel.randomGradientColors.value


    if (uiState.showCreatePlaylistDialog) {
        TextFieldDialog(
            title = "New Playlist",
            label = "Playlist name",
            confirmButtonText = "Create",
            onDismiss = { viewModel.onEvent(ArtistGroupDetailEvent.DismissCreatePlaylistDialog) },
            onConfirm = { name -> viewModel.onEvent(ArtistGroupDetailEvent.CreatePlaylist(name)) }
        )
    }

    uiState.itemToAddToPlaylist?.let { item ->
        val songTitle: String
        val songArtist: String
        val thumbnailUrl: String

        when (item) {
            is Song -> {
                songTitle = item.title
                songArtist = item.artist
                thumbnailUrl = item.getHighQualityThumbnailUrl()
            }
            is StreamInfoItem -> {
                songTitle = item.name ?: "Unknown"
                songArtist = item.uploaderName ?: "Unknown"
                thumbnailUrl = item.getHighQualityThumbnailUrl()
            }
            else -> return@let
        }

        ModalBottomSheet(
            onDismissRequest = { viewModel.onEvent(ArtistGroupDetailEvent.DismissAddToPlaylistSheet) },
            sheetState = sheetState
        ) {
            AddToPlaylistSheet(
                songTitle = songTitle,
                songArtist = songArtist,
                songThumbnailUrl = thumbnailUrl,
                playlists = uiState.allPlaylists,
                onPlaylistSelected = { playlistId ->
                    viewModel.onEvent(ArtistGroupDetailEvent.PlaylistSelectedForAddition(playlistId))
                },
                onCreateNewPlaylist = {
                    viewModel.onEvent(ArtistGroupDetailEvent.PrepareToCreatePlaylist)
                }
            )
        }
    }
    GradientBackground(gradientColor1 = gradientColor1, gradientColor2 = gradientColor2) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = { Text(uiState.groupWithArtists?.group?.name ?: "Group", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.1f),
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            if (uiState.artistsInGroup.isEmpty()) {
                val message = if (uiState.groupWithArtists == null) "Loading..." else "This group is empty."
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    if (uiState.groupWithArtists == null) {
                        CircularProgressIndicator()
                    } else {
                        EmptyStateMessage(message = message)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                ) {
                    items(uiState.artistsInGroup, key = { it.artist.artistId }) { artistWithSongs ->
                        val artist = artistWithSongs.artist
                        ArtistItem(
                            artistWithSongs = artistWithSongs,
                            onClick = { onArtistClick(artist.artistId) },
                            onPlay = { viewModel.onEvent(ArtistGroupDetailEvent.PlayArtist(artist)) },
                            onShuffle = { viewModel.onEvent(ArtistGroupDetailEvent.ShuffleArtist(artist)) },
                            onShuffleUngrouped = { viewModel.onEvent(ArtistGroupDetailEvent.ShuffleUngroupedSongs(artist)) },
                            onEdit = { onEditArtistSongs(artist.artistId) },
                            onToggleAutoDownload = { viewModel.onEvent(ArtistGroupDetailEvent.ToggleAutoDownload(artist)) },
                            groupAction = "Remove from group" to { viewModel.onEvent(ArtistGroupDetailEvent.RemoveArtistFromGroup(artist)) },
                            onHideArtist = { viewModel.onEvent(ArtistGroupDetailEvent.HideArtist(artist)) },
                            processUrls = { urls -> viewModel.thumbnailProcessor.process(urls) }
                        )
                    }
                }
            }
        }
    }
}