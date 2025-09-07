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
import com.example.m.managers.DialogState
import com.example.m.ui.library.components.ArtistGroupConflictDialog
import com.example.m.ui.library.components.CreateLibraryGroupDialog
import com.example.m.ui.library.components.SelectLibraryGroupDialog
import com.example.m.ui.search.SearchResultItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistSongsScreen(
    onBack: () -> Unit,
    viewModel: ArtistSongsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val title = if (uiState.searchType == "music") "Popular" else "Videos"
    val listState = rememberLazyListState()
    val dialogState by viewModel.dialogState.collectAsState()

    when (val state = dialogState) {
        is DialogState.CreateGroup -> {
            CreateLibraryGroupDialog(
                onDismiss = { viewModel.onEvent(ArtistSongsEvent.DismissDialog) },
                onCreate = { name -> viewModel.onEvent(ArtistSongsEvent.RequestCreateGroup(name)) },
                isFirstGroup = state.isFirstGroup
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
                        SearchResultItem(
                            result = item.result,
                            localSong = item.localSong,
                            isSong = uiState.searchType == "music",
                            imageLoader = viewModel.imageLoader,
                            onPlay = { viewModel.onEvent(ArtistSongsEvent.SongSelected(index)) },
                            onAddToLibrary = { viewModel.onEvent(ArtistSongsEvent.AddToLibrary(item.result)) },
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