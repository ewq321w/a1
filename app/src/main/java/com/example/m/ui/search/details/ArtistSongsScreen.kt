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
import com.example.m.data.database.LibraryGroup
import com.example.m.data.database.Song
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.library.components.*
import com.example.m.ui.search.SearchResultItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistSongsScreen(
    onBack: () -> Unit,
    viewModel: ArtistSongsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val title = if (uiState.searchType == "music") "Popular" else "Videos"
    val listState = rememberLazyListState()

    val conflictDialogState by remember { derivedStateOf { viewModel.conflictDialogState } }
    val showCreateGroupDialog by remember { derivedStateOf { viewModel.showCreateGroupDialog } }
    val showSelectGroupDialog by remember { derivedStateOf { viewModel.showSelectGroupDialog } }
    val libraryGroups by viewModel.libraryGroups.collectAsState()

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
                    itemsIndexed(uiState.songs, key = { index, item -> item.result.streamInfo.url + index }) { index, item ->
                        SearchResultItem(
                            result = item.result,
                            downloadStatus = item.downloadStatus,
                            isSong = uiState.searchType == "music",
                            imageLoader = viewModel.imageLoader,
                            onPlay = { viewModel.onSongSelected(index) },
                            onAddToLibrary = { viewModel.addSongToLibrary(item.result) },
                            onPlayNext = { viewModel.onPlayNext(item.result) },
                            onAddToQueue = { viewModel.onAddToQueue(item.result) }
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
                        viewModel.loadMoreSongs()
                    }
                }
            }
        }
    }
}