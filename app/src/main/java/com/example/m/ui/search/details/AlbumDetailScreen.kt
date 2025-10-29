// file: com/example/m/ui/search/details/AlbumDetailScreen.kt
package com.example.m.ui.search.details

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.m.R
import com.example.m.data.database.Song
import com.example.m.managers.DialogState
import com.example.m.managers.PlaylistActionState
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
    val listState = rememberLazyListState()
    val dialogState by viewModel.dialogState.collectAsState()

    when (val state = dialogState) {
        is DialogState.CreateGroup -> {
            TextFieldDialog(
                title = "New Library Group",
                label = "Group name",
                confirmButtonText = "Create",
                onDismiss = { viewModel.onEvent(AlbumDetailEvent.DismissDialog) },
                onConfirm = { name -> viewModel.onEvent(AlbumDetailEvent.RequestCreateGroup(name)) },
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
        contentWindowInsets = WindowInsets(0,0,0,0) // Allow content to draw behind system bars
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
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    // This logic selects a medium-quality thumbnail instead of the largest one.
                    val mediumQualityThumbnailUrl = uiState.albumInfo?.thumbnails
                        ?.sortedBy { it.width }
                        ?.let { sortedList ->
                            if (sortedList.isNotEmpty()) {
                                sortedList.getOrNull(sortedList.size / 2)?.url
                            } else {
                                null
                            }
                        }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item {
                            AlbumHeader(
                                thumbnailUrl = mediumQualityThumbnailUrl,
                                albumName = uiState.albumInfo?.name ?: "Unknown Album",
                                artistName = uiState.albumInfo?.uploaderName ?: "Unknown Artist"
                            )
                        }

                        itemsIndexed(uiState.songs, key = { index, item -> (item.result.streamInfo.url ?: "") + index }) { index, item ->
                            val normalizedUrl = item.result.streamInfo.url?.replace("music.youtube.com", "www.youtube.com")
                            val isPlaying = normalizedUrl == uiState.nowPlayingMediaId || item.localSong?.localFilePath == uiState.nowPlayingMediaId
                            SearchResultItem(
                                result = item.result,
                                localSong = item.localSong,
                                isPlaying = isPlaying,
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

                    // Floating Back Button
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    // Floating More Options Button
                    var showMenu by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = Color.White
                            )
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumHeader(thumbnailUrl: String?, albumName: String, artistName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 30.dp, bottom = 10.dp), // Adjust top padding to clear back button, add bottom padding
        horizontalAlignment = Alignment.CenterHorizontally // Center content horizontally
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = "Album Thumbnail",
            modifier = Modifier
                .size(190.dp) // Bigger size
                .clip(RoundedCornerShape(3.dp)),
            contentScale = ContentScale.Crop,
            error = painterResource(id = R.drawable.placeholder_gray),
            placeholder = painterResource(id = R.drawable.placeholder_gray)
        )
        Spacer(modifier = Modifier.height(10.dp)) // Space between image and text
        Text(
            text = albumName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold, // Changed from Bold to SemiBold
            maxLines = 2, // Max lines 2
            overflow = TextOverflow.Ellipsis, // Ellipsis overflow
            textAlign = TextAlign.Center, // Center text
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = artistName,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, // Center text
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
        )
    }
}