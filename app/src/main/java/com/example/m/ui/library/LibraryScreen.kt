// file: com/example/m/ui/library/LibraryScreen.kt
package com.example.m.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.data.database.LibraryGroup
import com.example.m.data.database.Song
import com.example.m.managers.DialogState
import com.example.m.managers.PlaylistActionState
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.library.components.*
import com.example.m.ui.library.tabs.*
import com.example.m.ui.main.MainViewModel
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
    val activity = LocalContext.current as ComponentActivity
    val mainViewModel: MainViewModel = hiltViewModel(activity)
    val libraryViewModel: LibraryViewModel = hiltViewModel()

    val playlistsViewModel: PlaylistsViewModel = hiltViewModel()
    val artistsViewModel: ArtistsViewModel = hiltViewModel()
    val songsViewModel: SongsViewModel = hiltViewModel()

    val uiState by libraryViewModel.uiState.collectAsState()
    val isDoingMaintenance by mainViewModel.isDoingMaintenance

    val songsDialogState by songsViewModel.dialogState.collectAsState()
    val artistsPlaylistActionState by artistsViewModel.playlistActionState.collectAsState()
    val songsPlaylistActionState by songsViewModel.playlistActionState.collectAsState()
    val playlistsPlaylistActionState by playlistsViewModel.playlistActionState.collectAsState()

    val finalDialogState = songsDialogState
    val finalPlaylistActionState = when {
        artistsPlaylistActionState !is PlaylistActionState.Hidden -> artistsPlaylistActionState
        songsPlaylistActionState !is PlaylistActionState.Hidden -> songsPlaylistActionState
        else -> playlistsPlaylistActionState
    }

    LaunchedEffect(Unit) {
        songsViewModel.navigateToArtist.collect { artistId ->
            onArtistClick(artistId)
        }
    }
    LaunchedEffect(Unit) {
        artistsViewModel.navigateToArtist.collect { artistId ->
            onArtistClick(artistId)
        }
    }

    if (uiState.showManageGroupsDialog) {
        ManageLibraryGroupsDialog(
            groups = uiState.libraryGroups,
            onDismiss = { libraryViewModel.onEvent(LibraryEvent.ManageGroupsDismissed) },
            onAddGroup = { name -> libraryViewModel.onEvent(LibraryEvent.AddLibraryGroup(name)) },
            onRenameGroup = { group, newName -> libraryViewModel.onEvent(LibraryEvent.RenameLibraryGroup(group, newName)) },
            onDeleteGroup = { group -> libraryViewModel.onEvent(LibraryEvent.DeleteLibraryGroup(group)) }
        )
    }

    when (val state = finalDialogState) {
        is DialogState.CreateGroup -> {
            TextFieldDialog(
                title = "New Library Group",
                label = "Group name",
                confirmButtonText = "Create",
                onDismiss = { songsViewModel.onDialogDismiss() },
                onConfirm = { name -> songsViewModel.onDialogCreateGroup(name) },
                content = {
                    if (state.isFirstGroup) {
                        Text(
                            "To start your library, please create a group to add songs to.",
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            )
        }
        is DialogState.SelectGroup -> {
            SelectLibraryGroupDialog(
                groups = state.groups,
                onDismiss = { songsViewModel.onDialogDismiss() },
                onGroupSelected = { groupId -> songsViewModel.onDialogGroupSelected(groupId) },
                onCreateNewGroup = { songsViewModel.onDialogRequestCreateGroup() }
            )
        }
        is DialogState.Conflict -> {
            ArtistGroupConflictDialog(
                artistName = state.song.artist,
                conflictingGroupName = state.conflict.conflictingGroupName,
                targetGroupName = state.targetGroupName,
                onDismiss = { songsViewModel.onDialogDismiss() },
                onMoveArtistToTargetGroup = { songsViewModel.onDialogResolveConflict() }
            )
        }
        is DialogState.Hidden -> {}
    }

    when (val state = finalPlaylistActionState) {
        is PlaylistActionState.AddToPlaylist -> {
            val sheetState = rememberModalBottomSheetState()
            val item = state.item
            val songTitle = (item as? Song)?.title ?: (item as? StreamInfoItem)?.name ?: "Unknown"
            val songArtist = (item as? Song)?.artist ?: (item as? StreamInfoItem)?.uploaderName ?: "Unknown"
            val thumbnailUrl = (item as? Song)?.thumbnailUrl ?: (item as? StreamInfoItem)?.getHighQualityThumbnailUrl() ?: ""

            ModalBottomSheet(onDismissRequest = { artistsViewModel.onPlaylistActionDismiss() }, sheetState = sheetState) {
                AddToPlaylistSheet(
                    songTitle = songTitle,
                    songArtist = songArtist,
                    songThumbnailUrl = thumbnailUrl,
                    playlists = state.playlists,
                    onPlaylistSelected = { playlistId -> artistsViewModel.onPlaylistSelected(playlistId) },
                    onCreateNewPlaylist = { artistsViewModel.onPrepareToCreatePlaylist() }
                )
            }
        }
        is PlaylistActionState.CreatePlaylist -> {
            TextFieldDialog(
                title = "New Playlist",
                label = "Playlist name",
                confirmButtonText = "Create",
                onDismiss = { artistsViewModel.onPlaylistActionDismiss() },
                onConfirm = { name -> artistsViewModel.onCreatePlaylist(name) }
            )
        }
        is PlaylistActionState.SelectGroupForNewPlaylist -> {
            SelectLibraryGroupDialog(
                groups = state.groups,
                onDismiss = { artistsViewModel.onPlaylistActionDismiss() },
                onGroupSelected = { groupId -> artistsViewModel.onGroupSelectedForNewPlaylist(groupId) },
                onCreateNewGroup = {
                    artistsViewModel.onPlaylistActionDismiss()
                    songsViewModel.onDialogRequestCreateGroup()
                }
            )
        }
        is PlaylistActionState.Hidden -> {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    LibraryGroupSelectorMenu(
                        groups = uiState.libraryGroups,
                        activeGroupId = uiState.activeLibraryGroupId,
                        onGroupSelected = { libraryViewModel.onEvent(LibraryEvent.SetActiveLibraryGroup(it)) },
                        onManageGroups = { libraryViewModel.onEvent(LibraryEvent.ManageGroupsClicked) }
                    )
                    IconButton(onClick = onGoToHistory) { Icon(Icons.Default.History, contentDescription = "History") }
                    if (uiState.selectedView == "Songs") {
                        val songsUiState by songsViewModel.uiState.collectAsState()
                        SongSortMenu(
                            currentSortOrder = songsUiState.sortOrder,
                            onSortOrderSelected = { songsViewModel.onEvent(SongsTabEvent.SetSortOrder(it)) }
                        )
                    }
                    OptionsOverflowMenu(
                        selectedView = uiState.selectedView,
                        mainViewModel = mainViewModel,
                        songsViewModel = songsViewModel,
                        isDoingMaintenance = isDoingMaintenance,
                        onGoToHiddenArtists = onGoToHiddenArtists
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface),
                windowInsets = TopAppBarDefaults.windowInsets
            )
        },
        floatingActionButton = {
            when (uiState.selectedView) {
                "Playlists" -> FloatingActionButton(onClick = { playlistsViewModel.onEvent(PlaylistTabEvent.CreateEmptyPlaylist) }, modifier = Modifier.navigationBarsPadding(), containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Add, contentDescription = "Create Playlist") }
                "Artists" -> FloatingActionButton(onClick = { artistsViewModel.onEvent(ArtistTabEvent.ShowCreateArtistGroupDialog) }, modifier = Modifier.navigationBarsPadding(), containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.CreateNewFolder, contentDescription = "Create Artist Group") }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                listOf("Playlists", "Artists", "Songs").forEachIndexed { index, label ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                        onClick = { libraryViewModel.onEvent(LibraryEvent.SetSelectedView(label)) },
                        selected = uiState.selectedView == label
                    ) { Text(label) }
                }
            }
            when (uiState.selectedView) {
                "Playlists" -> PlaylistTabContent(
                    viewModel = playlistsViewModel,
                    onPlaylistClick = onPlaylistClick,
                    onEditPlaylist = onEditPlaylist,
                    modifier = Modifier.weight(1f)
                )
                "Artists" -> ArtistsTabContent(
                    viewModel = artistsViewModel,
                    onArtistClick = onArtistClick,
                    onGoToArtistGroup = onGoToArtistGroup,
                    onEditArtistSongs = onEditArtistSongs,
                    modifier = Modifier.weight(1f)
                )
                "Songs" -> SongsTabContent(
                    viewModel = songsViewModel,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LibraryGroupSelectorMenu(groups: List<LibraryGroup>, activeGroupId: Long, onGroupSelected: (Long) -> Unit, onManageGroups: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val activeGroupName = if (activeGroupId == 0L) "All Music" else groups.find { it.groupId == activeGroupId }?.name ?: "All Music"
    Box {
        TextButton(onClick = { showMenu = true }) {
            Text(activeGroupName, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Library Group", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("All Music") }, onClick = { onGroupSelected(0L); showMenu = false }, trailingIcon = { if (activeGroupId == 0L) Icon(Icons.Default.Check, "Selected") })
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            groups.forEach { group ->
                DropdownMenuItem(text = { Text(group.name) }, onClick = { onGroupSelected(group.groupId); showMenu = false }, trailingIcon = { if (activeGroupId == group.groupId) Icon(Icons.Default.Check, "Selected") })
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(text = { Text("Manage groups") }, onClick = { onManageGroups(); showMenu = false }, enabled = true)
        }
    }
}

@Composable
private fun OptionsOverflowMenu(
    selectedView: String,
    mainViewModel: MainViewModel,
    songsViewModel: SongsViewModel,
    isDoingMaintenance: Boolean,
    onGoToHiddenArtists: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val songsUiState by songsViewModel.uiState.collectAsState()

    Box {
        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            if (selectedView == "Songs") {
                DropdownMenuItem(text = { Text("Shuffle") }, onClick = { songsViewModel.onEvent(SongsTabEvent.ShuffleFilteredSongs); showMenu = false }, leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = "Shuffle") })
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Show Only", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                DropdownMenuItem(
                    text = { Text("Downloaded") },
                    onClick = { songsViewModel.onEvent(SongsTabEvent.SetDownloadFilter(if (songsUiState.downloadFilter == DownloadFilter.DOWNLOADED) DownloadFilter.ALL else DownloadFilter.DOWNLOADED)); showMenu = false },
                    leadingIcon = { if (songsUiState.downloadFilter == DownloadFilter.DOWNLOADED) Icon(Icons.Default.Check, contentDescription = "Selected") else Spacer(Modifier.width(24.dp)) }
                )
                DropdownMenuItem(
                    text = { Text("Ungrouped") },
                    onClick = { songsViewModel.onEvent(SongsTabEvent.SetGroupingFilter(if (songsUiState.groupingFilter == GroupingFilter.UNGROUPED) GroupingFilter.ALL else GroupingFilter.UNGROUPED)); showMenu = false },
                    leadingIcon = { if (songsUiState.groupingFilter == GroupingFilter.UNGROUPED) Icon(Icons.Default.Check, contentDescription = "Selected") else Spacer(Modifier.width(24.dp)) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
            if (selectedView == "Artists") {
                DropdownMenuItem(text = { Text("Hidden artists") }, onClick = { onGoToHiddenArtists(); showMenu = false }, leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = "Hidden artists") })
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
            DropdownMenuItem(
                text = { Text("Library Health Check") },
                enabled = !isDoingMaintenance,
                onClick = { mainViewModel.runLibraryMaintenance(); showMenu = false },
                leadingIcon = { if (isDoingMaintenance) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Sync, contentDescription = "Run Library Health Check") }
            )
        }
    }
}