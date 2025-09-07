// file: com/example/m/ui/search/details/AlbumDetailScreen.kt
package com.example.m.ui.search.details

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.m.R
import com.example.m.managers.DialogState
import com.example.m.ui.library.components.ArtistGroupConflictDialog
import com.example.m.ui.library.components.ConfirmAddAllToLibraryDialog
import com.example.m.ui.library.components.CreateLibraryGroupDialog
import com.example.m.ui.library.components.SelectLibraryGroupDialog
import com.example.m.ui.search.SearchResultItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val dialogState by viewModel.dialogState.collectAsState()

    when (val state = dialogState) {
        is DialogState.CreateGroup -> {
            CreateLibraryGroupDialog(
                onDismiss = { viewModel.onEvent(AlbumDetailEvent.DismissDialog) },
                onCreate = { name -> viewModel.onEvent(AlbumDetailEvent.RequestCreateGroup(name)) },
                isFirstGroup = state.isFirstGroup
            )
        }
        is DialogState.SelectGroup -> {
            SelectLibraryGroupDialog(
                groups = state.groups,
                onDismiss = { viewModel.onEvent(AlbumDetailEvent.DismissDialog) },
                onGroupSelected = { groupId -> viewModel.onEvent(AlbumDetailEvent.SelectGroup(groupId)) },
                onCreateNewGroup = {
                    viewModel.onEvent(AlbumDetailEvent.DismissDialog)
                }
            )
        }
        is DialogState.Conflict -> {
            ArtistGroupConflictDialog(
                artistName = state.song.artist,
                conflictingGroupName = state.conflict.conflictingGroupName,
                targetGroupName = state.targetGroupName,
                onDismiss = { viewModel.onEvent(AlbumDetailEvent.DismissDialog) },
                onMoveArtistToTargetGroup = { viewModel.onEvent(AlbumDetailEvent.ResolveConflict) }
            )
        }
        is DialogState.Hidden -> {}
    }


    if (uiState.showConfirmAddAllDialog) {
        ConfirmAddAllToLibraryDialog(
            itemName = uiState.albumInfo?.name ?: "this album",
            onDismiss = { viewModel.onEvent(AlbumDetailEvent.DismissConfirmAddAllToLibraryDialog) },
            onConfirm = { viewModel.onEvent(AlbumDetailEvent.ConfirmAddAllToLibrary) }
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
                                    viewModel.onEvent(AlbumDetailEvent.Shuffle)
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add all to library") },
                                onClick = {
                                    viewModel.onEvent(AlbumDetailEvent.ShowConfirmAddAllToLibraryDialog)
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

                    itemsIndexed(uiState.songs, key = { index, item -> (item.result.streamInfo.url ?: "") + index }) { index, item ->
                        SearchResultItem(
                            result = item.result,
                            localSong = item.localSong,
                            isSong = uiState.searchType == "music",
                            imageLoader = viewModel.imageLoader,
                            onPlay = { viewModel.onEvent(AlbumDetailEvent.SongSelected(index)) },
                            onAddToLibrary = { viewModel.onEvent(AlbumDetailEvent.AddToLibrary(item.result)) },
                            onPlayNext = { viewModel.onEvent(AlbumDetailEvent.PlayNext(item.result)) },
                            onAddToQueue = { viewModel.onEvent(AlbumDetailEvent.AddToQueue(item.result)) }
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
                        viewModel.onEvent(AlbumDetailEvent.LoadMore)
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