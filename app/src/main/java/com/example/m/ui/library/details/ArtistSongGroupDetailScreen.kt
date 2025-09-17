// file: com/example/m/ui/library/details/ArtistSongGroupDetailScreen.kt
package com.example.m.ui.library.details

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.data.database.Song
import com.example.m.managers.PlaylistActionState
import com.example.m.ui.common.GradientBackground
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.library.components.*
import com.example.m.ui.main.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArtistSongGroupDetailScreen(
    onBack: () -> Unit,
    onArtistClick: (Long) -> Unit,
    onEditGroup: (Long) -> Unit,
    viewModel: ArtistSongGroupDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playlistActionState by viewModel.playlistActionState.collectAsState()
    val groupName = uiState.groupWithSongs?.group?.name ?: "Group"
    var showMenu by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val activity = LocalContext.current as ComponentActivity
    val mainViewModel: MainViewModel = hiltViewModel(activity)
    val (gradientColor1, gradientColor2) = mainViewModel.randomGradientColors.value

    LaunchedEffect(Unit) {
        viewModel.navigateToArtist.collect { artistId ->
            onArtistClick(artistId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateBack.collectLatest {
            onBack()
        }
    }

    when(val state = playlistActionState) {
        is PlaylistActionState.AddToPlaylist -> {
            val item = state.item
            val songTitle = (item as? Song)?.title ?: (item as? StreamInfoItem)?.name ?: "Unknown"
            val songArtist = (item as? Song)?.artist ?: (item as? StreamInfoItem)?.uploaderName ?: "Unknown"
            val thumbnailUrl = (item as? Song)?.thumbnailUrl ?: (item as? StreamInfoItem)?.getHighQualityThumbnailUrl() ?: ""

            ModalBottomSheet(
                onDismissRequest = { viewModel.onPlaylistActionDismiss() },
                sheetState = sheetState
            ) {
                AddToPlaylistSheet(
                    songTitle = songTitle,
                    songArtist = songArtist,
                    songThumbnailUrl = thumbnailUrl,
                    playlists = state.playlists,
                    onPlaylistSelected = { playlistId -> viewModel.onPlaylistSelected(playlistId) },
                    onCreateNewPlaylist = { viewModel.onPrepareToCreatePlaylist() }
                )
            }
        }
        is PlaylistActionState.CreatePlaylist -> {
            TextFieldDialog(
                title = "New Playlist",
                label = "Playlist name",
                confirmButtonText = "Create",
                onDismiss = { viewModel.onPlaylistActionDismiss() },
                onConfirm = { name -> viewModel.onPlaylistCreateConfirm(name) }
            )
        }
        is PlaylistActionState.SelectGroupForNewPlaylist -> {
            SelectLibraryGroupDialog(
                groups = state.groups,
                onDismiss = { viewModel.onPlaylistActionDismiss() },
                onGroupSelected = { groupId -> viewModel.onGroupSelectedForNewPlaylist(groupId) },
                onCreateNewGroup = { viewModel.onDialogRequestCreateGroup() }
            )
        }
        is PlaylistActionState.Hidden -> {}
    }


    uiState.groupPendingDeletion?.let { group ->
        DeleteGroupWithOptionsDialog(
            groupName = group.name,
            onDismiss = { viewModel.onEvent(ArtistSongGroupDetailEvent.CancelDeleteGroup) },
            onConfirmDeleteOnly = { viewModel.onEvent(ArtistSongGroupDetailEvent.ConfirmDeleteGroupOnly) },
            onConfirmDeleteAll = { viewModel.onEvent(ArtistSongGroupDetailEvent.ConfirmDeleteGroupAndSongs) }
        )
    }

    uiState.songPendingRemoval?.let { song ->
        ConfirmRemoveDialog(
            itemType = "song",
            itemName = song.title,
            containerType = "group",
            onDismiss = { viewModel.onEvent(ArtistSongGroupDetailEvent.CancelRemoveSong) },
            onConfirm = { viewModel.onEvent(ArtistSongGroupDetailEvent.ConfirmRemoveSong) }
        )
    }
    GradientBackground(gradientColor1 = gradientColor1, gradientColor2 = gradientColor2) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = { Text(groupName, maxLines = 1, modifier = Modifier.basicMarquee()) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Shuffle") },
                                    onClick = {
                                        viewModel.onEvent(ArtistSongGroupDetailEvent.ShuffleGroup)
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit group") },
                                    onClick = {
                                        uiState.groupWithSongs?.group?.groupId?.let { onEditGroup(it) }
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete group") },
                                    onClick = {
                                        viewModel.onEvent(ArtistSongGroupDetailEvent.PrepareToDeleteGroup)
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.1f),
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            if (uiState.songs.isEmpty()) {
                EmptyStateMessage(message = "This group is empty.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                ) {
                    itemsIndexed(uiState.songs, key = { _, item -> item.songId }) { index, item ->
                        SongItem(
                            song = item,
                            onClick = { viewModel.onEvent(ArtistSongGroupDetailEvent.SongSelected(index)) },
                            onAddToPlaylistClick = { viewModel.onEvent(ArtistSongGroupDetailEvent.AddToPlaylist(item)) },
                            onPlayNextClick = { viewModel.onEvent(ArtistSongGroupDetailEvent.PlayNext(item)) },
                            onAddToQueueClick = { viewModel.onEvent(ArtistSongGroupDetailEvent.AddToQueue(item)) },
                            onGoToArtistClick = { viewModel.onEvent(ArtistSongGroupDetailEvent.GoToArtist(item)) },
                            onShuffleClick = { viewModel.onEvent(ArtistSongGroupDetailEvent.ShuffleSong(item)) },
                            onRemoveFromGroupClick = { viewModel.onEvent(ArtistSongGroupDetailEvent.PrepareToRemoveSong(item)) }
                        )
                    }
                }
            }
        }
    }
}