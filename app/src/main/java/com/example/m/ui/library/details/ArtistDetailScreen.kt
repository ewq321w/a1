package com.example.m.ui.library.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.data.database.Song
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.library.components.*
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    onBack: () -> Unit,
    onEditArtistSongs: (Long) -> Unit,
    onArtistClick: (Long) -> Unit
) {
    val viewModel: ArtistDetailViewModel = hiltViewModel()
    val artistWithSongs by viewModel.artistWithSongs.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val artist = artistWithSongs?.artist
    val artistName = artist?.name ?: "Artist"
    var showMenu by remember { mutableStateOf(false) }

    val itemToAddToPlaylist by remember { derivedStateOf { viewModel.itemToAddToPlaylist } }
    val showCreatePlaylistDialog by remember { derivedStateOf { viewModel.showCreatePlaylistDialog } }
    val itemToDelete by viewModel.itemPendingDeletion
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(Unit) {
        viewModel.navigateToArtist.collect { artistId ->
            onArtistClick(artistId)
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.dismissCreatePlaylistDialog() },
            onCreate = { name -> viewModel.createPlaylistAndAddPendingItem(name) }
        )
    }

    val currentItem = itemToAddToPlaylist
    if (currentItem != null) {
        val songTitle = if (currentItem is Song) currentItem.title else (currentItem as StreamInfoItem).name ?: "Unknown"
        val songArtist = if (currentItem is Song) currentItem.artist else (currentItem as StreamInfoItem).uploaderName ?: "Unknown"
        val thumbnailUrl = if (currentItem is Song) currentItem.thumbnailUrl else (currentItem as StreamInfoItem).getHighQualityThumbnailUrl()

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

    (itemToDelete as? Song)?.let { song ->
        ConfirmDeleteDialog(
            itemType = "song",
            itemName = song.title,
            onDismiss = { viewModel.itemPendingDeletion.value = null },
            onConfirm = {
                viewModel.deleteSong(song)
                viewModel.itemPendingDeletion.value = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(artistName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ArtistSortMenu(
                        currentSortOrder = sortOrder,
                        onSortOrderSelected = { viewModel.setSortOrder(it) }
                    )
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Play") },
                                onClick = { viewModel.playArtist(); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Shuffle") },
                                onClick = { viewModel.shuffleArtist(); showMenu = false }
                            )
                            val toggleText = if (artist?.downloadAutomatically == true) "Disable auto-download" else "Enable auto-download"
                            DropdownMenuItem(
                                text = { Text(toggleText) },
                                onClick = { viewModel.toggleAutoDownload(); showMenu = false }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Edit song order") },
                                onClick = {
                                    artist?.artistId?.let(onEditArtistSongs)
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (songs.isEmpty()) {
            EmptyStateMessage(message = "No songs found for this artist.")
        } else {
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                itemsIndexed(songs, key = { _, item -> item.song.songId }) { index, item ->
                    SongItem(
                        song = item.song,
                        downloadStatus = item.downloadStatus,
                        onClick = { viewModel.onSongSelected(index) },
                        onAddToPlaylistClick = { viewModel.selectItemForPlaylist(item.song) },
                        onDeleteClick = { viewModel.itemPendingDeletion.value = item.song },
                        onPlayNextClick = { viewModel.onPlaySongNext(item.song) },
                        onAddToQueueClick = { viewModel.onAddSongToQueue(item.song) },
                        onShuffleClick = { viewModel.onShuffleSong(item.song) },
                        onGoToArtistClick = { viewModel.onGoToArtist(item.song) },
                        onDownloadClick = { viewModel.downloadSong(item.song) }
                    )
                }
            }
        }
    }
}