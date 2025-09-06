// file: com/example/m/ui/search/details/AlbumDetailScreen.kt
package com.example.m.ui.search.details

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.m.R
import com.example.m.data.database.LibraryGroup
import com.example.m.data.database.Song
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.library.components.*
import com.example.m.ui.search.SearchResultItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val libraryGroups by viewModel.libraryGroups.collectAsState()
    val listState = rememberLazyListState()

    val showConfirmDialog by remember { derivedStateOf { viewModel.showConfirmAddAllDialog } }
    val conflictDialogState by remember { derivedStateOf { viewModel.conflictDialogState } }
    val showCreateGroupDialog by remember { derivedStateOf { viewModel.showCreateGroupDialog } }
    val showSelectGroupDialog by remember { derivedStateOf { viewModel.showSelectGroupDialog } }


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

    if (showConfirmDialog) {
        val albumName = uiState.albumInfo?.name ?: "this album"
        ConfirmAddAllToLibraryDialog(
            itemName = albumName,
            onDismiss = { viewModel.dismissConfirmAddAllToLibraryDialog() },
            onConfirm = { viewModel.confirmAddAllToLibrary() }
        )
    }

    Scaffold(
        topBar = {
            var showMenu by remember { mutableStateOf(false) }
            TopAppBar(
                title = {
                    Text(
                        uiState.albumInfo?.name ?: "",
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                                    viewModel.shuffle()
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add all to library") },
                                onClick = {
                                    viewModel.onAddAllToLibraryClicked()
                                    showMenu = false
                                }
                            )
                        }
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.errorMessage != null -> {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text(uiState.errorMessage!!)
                }
            }
            uiState.albumInfo != null -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize(),
                ) {
                    item {
                        AlbumHeader(
                            thumbnailUrl = uiState.albumInfo?.thumbnails?.maxByOrNull { it.width }?.url,
                            albumName = uiState.albumInfo?.name ?: "Unknown Album",
                            artistName = uiState.albumInfo?.uploaderName ?: "Unknown Artist"
                        )
                    }

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
                    val lastVisibleItemIndex = layoutInfo.value.visibleItemsInfo.lastOrNull()?.index ?: 0
                    if (uiState.songs.isNotEmpty() &&
                        lastVisibleItemIndex >= uiState.songs.size - 5 &&
                        !uiState.isLoadingMore &&
                        uiState.nextPage != null
                    ) {
                        viewModel.loadMoreSongs()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumHeader(thumbnailUrl: String?, albumName: String, artistName: String) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = "Album Thumbnail",
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(3.dp)),
            contentScale = ContentScale.Crop,
            error = painterResource(id = R.drawable.placeholder_gray),
            placeholder = painterResource(id = R.drawable.placeholder_gray)
        )
        Column(
            modifier = Modifier.padding(start = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = albumName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = artistName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}