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
import com.example.m.ui.library.components.*
import com.example.m.ui.library.tabs.ArtistsTabContent
import com.example.m.ui.library.tabs.PlaylistTabContent
import com.example.m.ui.library.tabs.SongsTabContent
import com.example.m.ui.main.MainViewModel
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import com.example.m.ui.common.getHighQualityThumbnailUrl

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
    val uiState by viewModel.uiState.collectAsState()
    val dialogState by viewModel.dialogState.collectAsState()
    val isDoingMaintenance by mainViewModel.isDoingMaintenance
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(Unit) {
        viewModel.navigateToArtist.collect { artistId ->
            onArtistClick(artistId)
        }
    }

    if (uiState.showManageGroupsDialog) {
        ManageLibraryGroupsDialog(
            groups = uiState.libraryGroups,
            onDismiss = { viewModel.onEvent(LibraryEvent.ManageGroupsDismissed) },
            onAddGroup = { name -> viewModel.onEvent(LibraryEvent.AddLibraryGroup(name)) },
            onRenameGroup = { group, newName -> viewModel.onEvent(LibraryEvent.RenameLibraryGroup(group, newName)) },
            onDeleteGroup = { group -> viewModel.onEvent(LibraryEvent.DeleteLibraryGroup(group)) }
        )
    }

    when (val state = dialogState) {
        is DialogState.CreateGroup -> {
            CreateLibraryGroupDialog(
                onDismiss = { viewModel.onDialogDismiss() },
                onCreate = { name -> viewModel.onDialogCreateGroup(name) },
                isFirstGroup = state.isFirstGroup
            )
        }
        is DialogState.SelectGroup -> {
            SelectLibraryGroupDialog(
                groups = state.groups,
                onDismiss = { viewModel.onDialogDismiss() },
                onGroupSelected = { groupId -> viewModel.onDialogGroupSelected(groupId) },
                onCreateNewGroup = { viewModel.onEvent(LibraryEvent.RequestCreateGroup) }
            )
        }
        is DialogState.Conflict -> {
            ArtistGroupConflictDialog(
                artistName = state.song.artist,
                conflictingGroupName = state.conflict.conflictingGroupName,
                targetGroupName = state.targetGroupName,
                onDismiss = { viewModel.onDialogDismiss() },
                onMoveArtistToTargetGroup = { viewModel.onDialogResolveConflict() }
            )
        }
        is DialogState.Hidden -> {}
    }

    if (uiState.showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.onEvent(LibraryEvent.DismissCreatePlaylistDialog) },
            onCreate = { name -> viewModel.onEvent(LibraryEvent.CreatePlaylist(name)) }
        )
    }

    if (uiState.showCreateArtistGroupDialog) {
        CreateArtistGroupDialog(
            onDismiss = { viewModel.onEvent(LibraryEvent.DismissCreateArtistGroupDialog) },
            onCreate = { name -> viewModel.onEvent(LibraryEvent.CreateArtistGroup(name)) }
        )
    }

    uiState.groupToRename?.let { group ->
        RenameArtistGroupDialog(
            initialName = group.name,
            onDismiss = { viewModel.onEvent(LibraryEvent.CancelRenameGroup) },
            onConfirm = { newName -> viewModel.onEvent(LibraryEvent.ConfirmRenameGroup(newName)) }
        )
    }

    uiState.artistToMove?.let { artist ->
        ModalBottomSheet(onDismissRequest = { viewModel.onEvent(LibraryEvent.DismissMoveArtistSheet) }) {
            MoveToGroupSheet(
                artistName = artist.name,
                groups = uiState.allArtistGroups,
                onGroupSelected = { groupId -> viewModel.onEvent(LibraryEvent.MoveArtistToGroup(groupId)) }
            )
        }
    }

    uiState.itemToAddToPlaylist?.let { item ->
        val songTitle: String
        val songArtist: String
        val thumbnailUrl: String

        when (item) {
            is Song -> {
                songTitle = item.title
                songArtist = item.artist
                thumbnailUrl = item.thumbnailUrl
            }
            is StreamInfoItem -> {
                songTitle = item.name ?: "Unknown"
                songArtist = item.uploaderName ?: "Unknown"
                thumbnailUrl = item.getHighQualityThumbnailUrl()
            }
            else -> {
                songTitle = "Unknown"
                songArtist = "Unknown"
                thumbnailUrl = ""
            }
        }

        ModalBottomSheet(onDismissRequest = { viewModel.onEvent(LibraryEvent.DismissAddToPlaylistSheet) }, sheetState = sheetState) {
            AddToPlaylistSheet(
                songTitle = songTitle,
                songArtist = songArtist,
                songThumbnailUrl = thumbnailUrl,
                playlists = uiState.allPlaylists,
                onPlaylistSelected = { playlistId -> viewModel.onEvent(LibraryEvent.PlaylistSelectedForAddition(playlistId)) },
                onCreateNewPlaylist = { viewModel.onEvent(LibraryEvent.PrepareToCreatePlaylistWithSong) }
            )
        }
    }

    uiState.itemPendingDeletion?.let { item ->
        when (item) {
            is DeletableItem.DeletableSong -> ConfirmDeleteDialog(itemType = "song", itemName = item.song.title, onDismiss = { viewModel.onEvent(LibraryEvent.ClearItemForDeletion) }, onConfirm = { viewModel.onEvent(LibraryEvent.ConfirmDeletion) })
            is DeletableItem.DeletablePlaylist -> ConfirmDeleteDialog(itemType = "playlist", itemName = item.playlist.playlist.name, onDismiss = { viewModel.onEvent(LibraryEvent.ClearItemForDeletion) }, onConfirm = { viewModel.onEvent(LibraryEvent.ConfirmDeletion) })
            is DeletableItem.DeletableArtistGroup -> ConfirmDeleteDialog(itemType = "artist group", itemName = item.group.name, onDismiss = { viewModel.onEvent(LibraryEvent.ClearItemForDeletion) }, onConfirm = { viewModel.onEvent(LibraryEvent.ConfirmDeletion) })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    LibraryGroupSelectorMenu(
                        groups = uiState.libraryGroups,
                        activeGroupId = uiState.activeLibraryGroupId,
                        onGroupSelected = { viewModel.onEvent(LibraryEvent.SetActiveLibraryGroup(it)) },
                        onManageGroups = { viewModel.onEvent(LibraryEvent.ManageGroupsClicked) }
                    )
                    IconButton(onClick = onGoToHistory) { Icon(Icons.Default.History, contentDescription = "History") }
                    if (uiState.selectedView == "Songs") {
                        SongSortMenu(currentSortOrder = uiState.sortOrder, onSortOrderSelected = { viewModel.onEvent(LibraryEvent.SetSortOrder(it)) })
                    }
                    OptionsOverflowMenu(
                        selectedView = uiState.selectedView,
                        mainViewModel = mainViewModel,
                        onEvent = viewModel::onEvent,
                        downloadFilter = uiState.downloadFilter,
                        groupingFilter = uiState.groupingFilter,
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
                "Playlists" -> FloatingActionButton(onClick = { viewModel.onEvent(LibraryEvent.PrepareToCreateEmptyPlaylist) }, modifier = Modifier.navigationBarsPadding(), containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Add, contentDescription = "Create Playlist") }
                "Artists" -> FloatingActionButton(onClick = { viewModel.onEvent(LibraryEvent.ShowCreateArtistGroupDialog) }, modifier = Modifier.navigationBarsPadding(), containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.CreateNewFolder, contentDescription = "Create Artist Group") }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                listOf("Playlists", "Artists", "Songs").forEachIndexed { index, label ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                        onClick = { viewModel.onEvent(LibraryEvent.SetSelectedView(label)) },
                        selected = uiState.selectedView == label
                    ) { Text(label) }
                }
            }
            when (uiState.selectedView) {
                "Playlists" -> PlaylistTabContent(playlists = uiState.playlists, onPlaylistClick = onPlaylistClick, onEditPlaylist = onEditPlaylist, onEvent = viewModel::onEvent, modifier = Modifier.weight(1f), thumbnailProcessor = viewModel.thumbnailProcessor)
                "Artists" -> ArtistsTabContent(artists = uiState.libraryArtistItems, onArtistClick = onArtistClick, onGoToArtistGroup = onGoToArtistGroup, onEditArtistSongs = onEditArtistSongs, onEvent = viewModel::onEvent, modifier = Modifier.weight(1f), thumbnailProcessor = viewModel.thumbnailProcessor)
                "Songs" -> SongsTabContent(songs = uiState.songs, onEvent = viewModel::onEvent, modifier = Modifier.weight(1f))
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
    onEvent: (LibraryEvent) -> Unit,
    downloadFilter: DownloadFilter,
    groupingFilter: GroupingFilter,
    isDoingMaintenance: Boolean,
    onGoToHiddenArtists: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            if (selectedView == "Songs") {
                DropdownMenuItem(text = { Text("Shuffle") }, onClick = { onEvent(LibraryEvent.ShuffleFilteredSongs); showMenu = false }, leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = "Shuffle") })
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Show Only", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                DropdownMenuItem(
                    text = { Text("Downloaded") },
                    onClick = { onEvent(LibraryEvent.SetDownloadFilter(if (downloadFilter == DownloadFilter.DOWNLOADED) DownloadFilter.ALL else DownloadFilter.DOWNLOADED)); showMenu = false },
                    leadingIcon = { if (downloadFilter == DownloadFilter.DOWNLOADED) Icon(Icons.Default.Check, contentDescription = "Selected") else Spacer(Modifier.width(24.dp)) }
                )
                DropdownMenuItem(
                    text = { Text("Ungrouped") },
                    onClick = { onEvent(LibraryEvent.SetGroupingFilter(if (groupingFilter == GroupingFilter.UNGROUPED) GroupingFilter.ALL else GroupingFilter.UNGROUPED)); showMenu = false },
                    leadingIcon = { if (groupingFilter == GroupingFilter.UNGROUPED) Icon(Icons.Default.Check, contentDescription = "Selected") else Spacer(Modifier.width(24.dp)) }
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