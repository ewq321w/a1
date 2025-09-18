// file: com/example/m/ui/search/details/ArtistSongsScreen.kt
package com.example.m.ui.search.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.data.database.Song
import com.example.m.managers.DialogState
import com.example.m.managers.PlaylistActionState
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.library.components.AddToPlaylistSheet
import com.example.m.ui.library.components.ArtistGroupConflictDialog
import com.example.m.ui.library.components.SelectLibraryGroupDialog
import com.example.m.ui.library.components.TextFieldDialog
import com.example.m.ui.search.SearchResultItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistSongsScreen(
    onBack: () -> Unit,
    viewModel: ArtistSongsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val title = if (uiState.searchType == "music") "Popular Songs" else "Videos"
    val listState = rememberLazyListState()
    val dialogState by viewModel.dialogState.collectAsState()
    val playlistActionState by viewModel.playlistActionState.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    when (val state = dialogState) {
        is DialogState.CreateGroup -> {
            TextFieldDialog(
                title = "New Library Group",
                label = "Group name",
                confirmButtonText = "Create",
                onDismiss = { viewModel.onEvent(ArtistSongsEvent.DismissDialog) },
                onConfirm = { name -> viewModel.onEvent(ArtistSongsEvent.RequestCreateGroup(name)) },
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
                onDismiss = { viewModel.onEvent(ArtistSongsEvent.DismissDialog) },
                onGroupSelected = { groupId -> viewModel.onEvent(ArtistSongsEvent.SelectGroup(groupId)) },
                onCreateNewGroup = { viewModel.onEvent(ArtistSongsEvent.DismissDialog) }
            )
        }
        is DialogState.Conflict -> {
            ArtistGroupConflictDialog(
                artistName = state.song.artist,
                conflictingGroupName = state.conflict.conflictingGroupName,
                targetGroupName = state.targetGroupName,
                onDismiss = { viewModel.onEvent(ArtistSongsEvent.DismissDialog) },
                onMoveArtistToTargetGroup = { viewModel.onEvent(ArtistSongsEvent.ResolveConflict) }
            )
        }
        is DialogState.Hidden -> {}
    }

    when(val state = playlistActionState) {
        is PlaylistActionState.AddToPlaylist -> {
            val item = state.item
            val songTitle = (item as? Song)?.title ?: (item as? StreamInfoItem)?.name ?: "Unknown"
            val songArtist = (item as? Song)?.artist ?: (item as? StreamInfoItem)?.uploaderName ?: "Unknown"
            val thumbnailUrl = (item as? Song)?.thumbnailUrl ?: (item as? StreamInfoItem)?.getHighQualityThumbnailUrl() ?: ""

            ModalBottomSheet(onDismissRequest = { viewModel.onPlaylistActionDismiss() }, sheetState = sheetState) {
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


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.errorMessage != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text(uiState.errorMessage!!)
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(paddingValues).fillMaxSize()
                ) {
                    itemsIndexed(uiState.songs, key = { index, item -> (item.result.streamInfo.url ?: "") + index }) { index, item ->
                        val normalizedUrl = item.result.streamInfo.url?.replace("music.youtube.com", "www.youtube.com")
                        val isPlaying = normalizedUrl == uiState.nowPlayingMediaId || item.localSong?.localFilePath == uiState.nowPlayingMediaId
                        SearchResultItem(
                            result = item.result,
                            localSong = item.localSong,
                            isPlaying = isPlaying,
                            isSong = uiState.searchType == "music",
                            imageLoader = viewModel.imageLoader,
                            onPlay = { viewModel.onEvent(ArtistSongsEvent.SongSelected(index)) },
                            onAddToLibrary = { viewModel.onEvent(ArtistSongsEvent.AddToLibrary(item.result)) },
                            onAddToPlaylist = { viewModel.onEvent(ArtistSongsEvent.AddToPlaylist(item.result)) },
                            onPlayNext = { viewModel.onEvent(ArtistSongsEvent.PlayNext(item.result)) },
                            onAddToQueue = { viewModel.onEvent(ArtistSongsEvent.AddToQueue(item.result)) }
                        )
                    }

                    if (uiState.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }

                val layoutInfo = remember { derivedStateOf { listState.layoutInfo } }
                LaunchedEffect(layoutInfo.value.visibleItemsInfo) {
                    val totalItems = layoutInfo.value.totalItemsCount
                    val lastVisibleItemIndex = layoutInfo.value.visibleItemsInfo.lastOrNull()?.index ?: 0
                    if (totalItems > 0 && lastVisibleItemIndex >= totalItems - 5) {
                        viewModel.onEvent(ArtistSongsEvent.LoadMore)
                    }
                }
            }
        }
    }
}