// file: com/example/m/ui/library/LibraryScreen.kt
package com.example.m.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.data.database.LibraryGroup
import com.example.m.data.database.Song
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.library.components.*
import com.example.m.ui.library.tabs.ArtistsTabContent
import com.example.m.ui.library.tabs.PlaylistTabContent
import com.example.m.ui.library.tabs.SongsTabContent
import com.example.m.ui.main.MainViewModel
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
    val activity = LocalContext.current as ComponentActivity
    val mainViewModel: MainViewModel = hiltViewModel(activity)

    val playlists by viewModel.playlists.collectAsState()
    val libraryArtistItems by viewModel.libraryArtistItems.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()
    val downloadFilter by viewModel.downloadFilter.collectAsState()
    val groupingFilter by viewModel.groupingFilter.collectAsState()
    val selectedView by viewModel.selectedView.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val libraryViews = listOf("Playlists", "Artists", "Songs")

    val libraryGroups by viewModel.libraryGroups.collectAsState()
    val activeLibraryGroupId by viewModel.activeLibraryGroupId.collectAsState()

    val showCreatePlaylistDialog by remember { derivedStateOf { viewModel.showCreatePlaylistDialog } }
    val showCreateArtistGroupDialog by remember { derivedStateOf { viewModel.showCreateArtistGroupDialog } }
    val groupToRename by remember { derivedStateOf { viewModel.groupToRename } }
    val itemToAddToPlaylist by remember { derivedStateOf { viewModel.itemToAddToPlaylist } }
    val itemToDelete by viewModel.itemPendingDeletion
    val artistToMove by remember { derivedStateOf { viewModel.artistToMove } }
    val allArtistGroups by viewModel.allArtistGroups.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    val conflictDialogState by remember { derivedStateOf { viewModel.conflictDialogState } }
    val showManageGroupsDialog by remember { derivedStateOf { viewModel.showManageGroupsDialog } }
    val showCreateGroupDialog by remember { derivedStateOf { viewModel.showCreateGroupDialog } }
    val showSelectGroupDialog by remember { derivedStateOf { viewModel.showSelectGroupDialog } }


    val isDoingMaintenance by mainViewModel.isDoingMaintenance
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.navigateToArtist.collect { artistId ->
            onArtistClick(artistId)
        }
    }

    if (showManageGroupsDialog) {
        ManageLibraryGroupsDialog(
            groups = libraryGroups,
            onDismiss = { viewModel.onManageGroupsDismissed() },
            onAddGroup = { name -> coroutineScope.launch { viewModel.addLibraryGroup(name) } },
            onRenameGroup = { group, newName -> coroutineScope.launch { viewModel.renameLibraryGroup(group, newName) } },
            onDeleteGroup = { group -> coroutineScope.launch { viewModel.deleteLibraryGroup(group) } }
        )
    }

    if (showCreateGroupDialog) {
        CreateLibraryGroupDialog(
            onDismiss = { viewModel.dismissCreateGroupDialog() },
            onCreate = { name -> viewModel.createGroupAndProceed(name) },
            isFirstGroup = libraryGroups.isEmpty()
        )
    }

    if (showSelectGroupDialog) {
        SelectLibraryGroupDialog(
            groups = libraryGroups,
            onDismiss = { viewModel.dismissSelectGroupDialog() },
            onGroupSelected = { groupId -> viewModel.onGroupSelectedForAddition(groupId) },
            onCreateNewGroup = viewModel::prepareToCreateGroup
        )
    }

    conflictDialogState?.let { state ->
        ArtistGroupConflictDialog(
            artistName = state.song.artist,
            conflictingGroupName = state.conflict.conflictingGroupName,
            targetGroupName = state.targetGroupName,
            onDismiss = { viewModel.dismissConflictDialog() },
            onMoveArtistToTargetGroup = { viewModel.resolveConflictByMoving() }
        )
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
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    LibraryGroupSelectorMenu(
                        groups = libraryGroups,
                        activeGroupId = activeLibraryGroupId,
                        onGroupSelected = { viewModel.setActiveLibraryGroup(it) },
                        onManageGroups = { viewModel.onManageGroupsClicked() }
                    )

                    IconButton(onClick = onGoToHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }

                    if (selectedView == "Songs") {
                        SongSortMenu(
                            currentSortOrder = sortOrder,
                            onSortOrderSelected = { viewModel.setSortOrder(it) }
                        )
                    }

                    OptionsOverflowMenu(
                        selectedView = selectedView,
                        mainViewModel = mainViewModel,
                        libraryViewModel = viewModel,
                        downloadFilter = downloadFilter,
                        groupingFilter = groupingFilter,
                        isDoingMaintenance = isDoingMaintenance,
                        onGoToHiddenArtists = onGoToHiddenArtists
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                windowInsets = TopAppBarDefaults.windowInsets
            )
        },
        floatingActionButton = {
            when (selectedView) {
                "Playlists" -> FloatingActionButton(
                    onClick = { viewModel.prepareToCreateEmptyPlaylist() },
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Playlist")
                }
                "Artists" -> FloatingActionButton(
                    onClick = { viewModel.showCreateArtistGroupDialog = true },
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "Create Artist Group")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
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
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f)
                )
                "Artists" -> ArtistsTabContent(
                    artists = libraryArtistItems,
                    onArtistClick = onArtistClick,
                    onGoToArtistGroup = onGoToArtistGroup,
                    onEditArtistSongs = onEditArtistSongs,
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f)
                )
                "Songs" -> SongsTabContent(
                    songs = songs,
                    sortOrder = sortOrder,
                    onSongSelected = viewModel::onSongSelected,
                    onAddToPlaylistClick = { song -> viewModel.prepareToShowPlaylistSheet(song) },
                    onDeleteSongClick = { song -> viewModel.itemPendingDeletion.value = DeletableItem.DeletableSong(song) },
                    onPlayNextClick = { song -> viewModel.onPlaySongNext(song) },
                    onAddToQueueClick = { song -> viewModel.onAddSongToQueue(song) },
                    onShuffleClick = { song -> viewModel.onShuffleSong(song) },
                    onGoToArtistClick = { song -> viewModel.onGoToArtist(song) },
                    onDownloadClick = viewModel::downloadSong,
                    onDeleteDownloadClick = viewModel::deleteSongDownload,
                    onAddToLibraryClick = { song -> viewModel.addSongToLibrary(song) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LibraryGroupSelectorMenu(
    groups: List<LibraryGroup>,
    activeGroupId: Long,
    onGroupSelected: (Long) -> Unit,
    onManageGroups: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val activeGroupName =
        if (activeGroupId == 0L) "All Music"
        else groups.find { it.groupId == activeGroupId }?.name ?: "All Music"

    Box {
        TextButton(onClick = { showMenu = true }) {
            Text(
                activeGroupName,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "Select Library Group",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("All Music") },
                onClick = {
                    onGroupSelected(0L)
                    showMenu = false
                },
                trailingIcon = { if (activeGroupId == 0L) Icon(Icons.Default.Check, "Selected") }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            groups.forEach { group ->
                DropdownMenuItem(
                    text = { Text(group.name) },
                    onClick = {
                        onGroupSelected(group.groupId)
                        showMenu = false
                    },
                    trailingIcon = { if (activeGroupId == group.groupId) Icon(Icons.Default.Check, "Selected") }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text("Manage groups") },
                onClick = {
                    onManageGroups()
                    showMenu = false
                },
                enabled = true
            )
        }
    }
}

@Composable
private fun OptionsOverflowMenu(
    selectedView: String,
    mainViewModel: MainViewModel,
    libraryViewModel: LibraryViewModel,
    downloadFilter: DownloadFilter,
    groupingFilter: GroupingFilter,
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
                DropdownMenuItem(
                    text = { Text("Shuffle") },
                    onClick = {
                        libraryViewModel.shuffleFilteredSongs()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Shuffle, contentDescription = "Shuffle")
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Show Only", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                DropdownMenuItem(
                    text = { Text("Downloaded") },
                    onClick = {
                        val newFilter = if (downloadFilter == DownloadFilter.DOWNLOADED) DownloadFilter.ALL else DownloadFilter.DOWNLOADED
                        libraryViewModel.setDownloadFilter(newFilter)
                        showMenu = false
                    },
                    leadingIcon = {
                        if (downloadFilter == DownloadFilter.DOWNLOADED) Icon(Icons.Default.Check, contentDescription = "Selected")
                        else Spacer(Modifier.width(24.dp))
                    }
                )
                DropdownMenuItem(
                    text = { Text("Ungrouped") },
                    onClick = {
                        val newFilter = if (groupingFilter == GroupingFilter.UNGROUPED) GroupingFilter.ALL else GroupingFilter.UNGROUPED
                        libraryViewModel.setGroupingFilter(newFilter)
                        showMenu = false
                    },
                    leadingIcon = {
                        if (groupingFilter == GroupingFilter.UNGROUPED) Icon(Icons.Default.Check, contentDescription = "Selected")
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
                onClick = { mainViewModel.runLibraryMaintenance(); showMenu = false },
                leadingIcon = {
                    if (isDoingMaintenance) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Sync, contentDescription = "Run Library Health Check")
                }
            )
        }
    }
}