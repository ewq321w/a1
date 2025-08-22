package com.example.m.ui.library.details

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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.data.database.Playlist
import com.example.m.data.database.Song
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.library.components.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onEditPlaylist: (Long) -> Unit,
    onArtistClick: (Long) -> Unit
) {
    val viewModel: PlaylistDetailViewModel = hiltViewModel()
    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val showCreatePlaylistDialog by remember { derivedStateOf { viewModel.showCreatePlaylistDialog } }
    val itemToAddToPlaylist by remember { derivedStateOf { viewModel.itemToAddToPlaylist } }
    val sheetState = rememberModalBottomSheetState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var songToDownload by remember { mutableStateOf<Song?>(null) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            songToDownload?.let { viewModel.downloadSong(it) }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Notification permission is required to see download progress.")
            }
        }
        songToDownload = null
    }

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

    playlist?.let { pl ->
        var playlistToRemoveDownloads by remember { mutableStateOf<Playlist?>(null) }

        playlistToRemoveDownloads?.let {
            ConfirmDeleteDialog(
                itemType = "downloads for",
                itemName = it.name,
                onDismiss = { playlistToRemoveDownloads = null },
                onConfirm = {
                    viewModel.removeDownloadsForPlaylist()
                    playlistToRemoveDownloads = null
                }
            )
        }

        if (showDeleteConfirmation) {
            ConfirmDeleteDialog(
                itemType = "playlist",
                itemName = pl.name,
                onDismiss = { showDeleteConfirmation = false },
                onConfirm = {
                    onBack()
                    viewModel.deletePlaylist()
                }
            )
        }

        PlaylistDetailContent(
            playlist = pl,
            songs = songs,
            viewModel = viewModel,
            sortOrder = sortOrder,
            onBack = onBack,
            onEdit = { onEditPlaylist(pl.playlistId) },
            onShowDeleteDialog = { showDeleteConfirmation = true },
            onRemoveDownloads = { playlistToRemoveDownloads = pl },
            onDownloadSong = { song ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    songToDownload = song
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.downloadSong(song)
                }
            }
        )
    } ?: run {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Loading...") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistDetailContent(
    playlist: Playlist,
    songs: List<Song>,
    viewModel: PlaylistDetailViewModel,
    sortOrder: PlaylistSortOrder,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onShowDeleteDialog: () -> Unit,
    onRemoveDownloads: () -> Unit,
    onDownloadSong: (Song) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    PlaylistSortMenu(
                        currentSortOrder = sortOrder,
                        onSortOrderSelected = { viewModel.setSortOrder(it) }
                    )
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Play") }, onClick = { viewModel.onSongSelected(0); showMenu = false })
                            DropdownMenuItem(text = { Text("Shuffle") }, onClick = { viewModel.shufflePlaylist(); showMenu = false })
                            DropdownMenuItem(text = { Text("Edit playlist") }, onClick = { onEdit(); showMenu = false })
                            val toggleText = if (playlist.downloadAutomatically) "Disable auto-download" else "Enable auto-download"
                            DropdownMenuItem(text = { Text(toggleText) }, onClick = { viewModel.onAutoDownloadToggled(!playlist.downloadAutomatically); showMenu = false })
                            DropdownMenuItem(text = { Text("Remove all downloads") }, onClick = { onRemoveDownloads(); showMenu = false })
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { onShowDeleteDialog(); showMenu = false })
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (songs.isEmpty()) {
            EmptyStateMessage(message = "This playlist is empty.")
        } else {
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                itemsIndexed(songs, key = { _, song -> song.songId }) { index, song ->
                    SongItem(
                        song = song,
                        onClick = { viewModel.onSongSelected(index) },
                        onAddToPlaylistClick = { viewModel.selectItemForPlaylist(song) },
                        onRemoveFromPlaylistClick = { viewModel.removeSongFromPlaylist(song.songId) },
                        onPlayNextClick = { viewModel.onPlaySongNext(song) },
                        onAddToQueueClick = { viewModel.onAddSongToQueue(song) },
                        onShuffleClick = { viewModel.onShuffleSong(song) },
                        onGoToArtistClick = { viewModel.onGoToArtist(song) },
                        onDownloadClick = { onDownloadSong(song) }
                    )
                }
            }
        }
    }
}