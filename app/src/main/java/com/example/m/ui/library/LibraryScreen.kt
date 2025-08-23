package com.example.m.ui.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.data.database.Artist
import com.example.m.data.database.Song
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.library.components.*
import com.example.m.ui.library.tabs.ArtistsTabContent
import com.example.m.ui.library.tabs.PlaylistTabContent
import com.example.m.ui.library.tabs.SongsTabContent
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onPlaylistClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit,
    onEditPlaylist: (Long) -> Unit,
    onEditArtistSongs: (Long) -> Unit,
    onGoToHiddenArtists: () -> Unit,
    onGoToArtistGroup: (Long) -> Unit,
    onGoToHistory: () -> Unit
) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val playlists by viewModel.playlists.collectAsState()
    val libraryArtistItems by viewModel.libraryArtistItems.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()
    val downloadFilter by viewModel.downloadFilter.collectAsState()
    val selectedView by viewModel.selectedView.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val libraryViews = listOf("Playlists", "Artists", "Songs")

    val showCreatePlaylistDialog by remember { derivedStateOf { viewModel.showCreatePlaylistDialog } }
    val showCreateArtistGroupDialog by remember { derivedStateOf { viewModel.showCreateArtistGroupDialog } }
    val groupToRename by remember { derivedStateOf { viewModel.groupToRename } }
    val itemToAddToPlaylist by remember { derivedStateOf { viewModel.itemToAddToPlaylist } }
    val itemToDelete by viewModel.itemPendingDeletion
    val artistToMove by remember { derivedStateOf { viewModel.artistToMove } }
    val allArtistGroups by viewModel.allArtistGroups.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    val isDoingMaintenance by remember { derivedStateOf { viewModel.isDoingMaintenance } }
    val maintenanceResult by remember { derivedStateOf { viewModel.maintenanceResult } }
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

    LaunchedEffect(maintenanceResult) {
        maintenanceResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMaintenanceResult()
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.dismissCreatePlaylistDialog() },
            onCreate = { name -> viewModel.handlePlaylistCreation(name) }
        )
    }

    if (showCreateArtistGroupDialog) {
        CreateArtistGroupDialog(
            onDismiss = { viewModel.dismissCreateArtistGroupDialog() },
            onCreate = { name ->
                viewModel.createArtistGroup(name)
                viewModel.dismissCreateArtistGroupDialog()
            }
        )
    }

    groupToRename?.let { group ->
        RenameArtistGroupDialog(
            initialName = group.name,
            onDismiss = { viewModel.cancelRenameGroup() },
            onConfirm = { newName -> viewModel.confirmRenameGroup(newName) }
        )
    }

    val artistToMoveState = artistToMove
    if (artistToMoveState != null) {
        ModalBottomSheet(onDismissRequest = { viewModel.dismissMoveArtistSheet() }) {
            MoveToGroupSheet(
                artistName = artistToMoveState.name,
                groups = allArtistGroups,
                onGroupSelected = { groupId ->
                    viewModel.moveArtistToGroup(groupId)
                }
            )
        }
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
                    viewModel.prepareToCreatePlaylistWithSong()
                }
            )
        }
    }

    val currentItemToDelete = itemToDelete
    if (currentItemToDelete != null) {
        when (currentItemToDelete) {
            is DeletableItem.DeletableSong -> ConfirmDeleteDialog(
                itemType = "song",
                itemName = currentItemToDelete.song.title,
                onDismiss = { viewModel.itemPendingDeletion.value = null },
                onConfirm = {
                    viewModel.deleteSong(currentItemToDelete.song)
                    viewModel.itemPendingDeletion.value = null
                }
            )
            is DeletableItem.DeletablePlaylist -> ConfirmDeleteDialog(
                itemType = "playlist",
                itemName = currentItemToDelete.playlist.playlist.name,
                onDismiss = { viewModel.itemPendingDeletion.value = null },
                onConfirm = {
                    viewModel.deletePlaylist(currentItemToDelete.playlist.playlist.playlistId)
                    viewModel.itemPendingDeletion.value = null
                }
            )
            is DeletableItem.DeletableArtistGroup -> ConfirmDeleteDialog(
                itemType = "artist group",
                itemName = currentItemToDelete.group.name,
                onDismiss = { viewModel.itemPendingDeletion.value = null },
                onConfirm = {
                    viewModel.deleteArtistGroup(currentItemToDelete.group.groupId)
                    viewModel.itemPendingDeletion.value = null
                }
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = onGoToHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }

                    if (selectedView == "Songs") {
                        IconButton(onClick = { viewModel.shuffleAllSongs() }) {
                            Icon(Icons.Default.Shuffle, contentDescription = "Shuffle All")
                        }
                        SongSortMenu(
                            currentSortOrder = sortOrder,
                            onSortOrderSelected = { viewModel.setSortOrder(it) }
                        )
                    }

                    OptionsOverflowMenu(
                        selectedView = selectedView,
                        viewModel = viewModel,
                        downloadFilter = downloadFilter,
                        isDoingMaintenance = isDoingMaintenance,
                        onGoToHiddenArtists = onGoToHiddenArtists
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            when (selectedView) {
                "Playlists" -> FloatingActionButton(
                    onClick = { viewModel.prepareToCreateEmptyPlaylist() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Playlist")
                }
                "Artists" -> FloatingActionButton(
                    onClick = { viewModel.showCreateArtistGroupDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "Create Artist Group")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                libraryViews.forEachIndexed { index, label ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = libraryViews.size),
                        onClick = { viewModel.setSelectedView(label) },
                        selected = selectedView == label
                    ) {
                        Text(label)
                    }
                }
            }

            when (selectedView) {
                "Playlists" -> PlaylistTabContent(
                    playlists = playlists,
                    onPlaylistClick = onPlaylistClick,
                    onEditPlaylist = onEditPlaylist,
                    viewModel = viewModel
                )
                "Artists" -> ArtistsTabContent(
                    artists = libraryArtistItems,
                    onArtistClick = onArtistClick,
                    onGoToArtistGroup = onGoToArtistGroup,
                    onEditArtistSongs = onEditArtistSongs,
                    viewModel = viewModel
                )
                "Songs" -> SongsTabContent(
                    songs = songs,
                    sortOrder = sortOrder,
                    onSongSelected = viewModel::onSongSelected,
                    onAddToPlaylistClick = { song -> viewModel.selectItemForPlaylist(song) },
                    onDeleteSongClick = { song -> viewModel.itemPendingDeletion.value = DeletableItem.DeletableSong(song) },
                    onPlayNextClick = viewModel::onPlaySongNext,
                    onAddToQueueClick = viewModel::onAddSongToQueue,
                    onShuffleClick = viewModel::onShuffleSong,
                    onGoToArtistClick = viewModel::onGoToArtist,
                    onDownloadClick = { song ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            songToDownload = song
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.downloadSong(song)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun OptionsOverflowMenu(
    selectedView: String,
    viewModel: LibraryViewModel,
    downloadFilter: DownloadFilter,
    isDoingMaintenance: Boolean,
    onGoToHiddenArtists: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            if (selectedView == "Songs") {
                Text("Show Only", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                DropdownMenuItem(
                    text = { Text("Downloaded") },
                    onClick = {
                        val newFilter = if (downloadFilter == DownloadFilter.DOWNLOADED) DownloadFilter.ALL else DownloadFilter.DOWNLOADED
                        viewModel.setDownloadFilter(newFilter)
                        showMenu = false
                    },
                    leadingIcon = {
                        if (downloadFilter == DownloadFilter.DOWNLOADED) Icon(Icons.Default.Check, contentDescription = "Selected")
                        else Spacer(Modifier.width(24.dp))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            if (selectedView == "Artists") {
                DropdownMenuItem(
                    text = { Text("Hidden artists") },
                    onClick = { onGoToHiddenArtists(); showMenu = false },
                    leadingIcon = {
                        Icon(Icons.Default.Visibility, contentDescription = "Hidden artists")
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            DropdownMenuItem(
                text = { Text("Library Health Check") },
                enabled = !isDoingMaintenance,
                onClick = { viewModel.runLibraryMaintenance(); showMenu = false },
                leadingIcon = {
                    if (isDoingMaintenance) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Sync, contentDescription = "Run Library Health Check")
                }
            )
        }
    }
}