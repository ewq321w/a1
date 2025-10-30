package com.example.m.ui.library.edit

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.data.database.Song
import com.example.m.ui.common.GradientBackground
import com.example.m.ui.library.components.CompositeThumbnailImage
import com.example.m.ui.library.components.ConfirmRemoveDialog
import com.example.m.ui.main.MainViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlaylistScreen(
    onBack: () -> Unit,
    viewModel: EditPlaylistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var playlistName by remember(uiState.playlistWithSongs) {
        mutableStateOf(uiState.playlistWithSongs?.playlist?.name ?: "")
    }
    val songs = uiState.playlistWithSongs?.songs ?: emptyList()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    val activity = LocalContext.current as ComponentActivity
    val mainViewModel: MainViewModel = hiltViewModel(activity)
    val (gradientColor1, gradientColor2) = mainViewModel.randomGradientColors.value

    val lazyListState = rememberLazyListState()
    val state = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            val adjustedFrom = from.index - 1
            val adjustedTo = to.index - 1
            if (adjustedFrom >= 0 && adjustedTo >= 0) {
                viewModel.onEvent(EditPlaylistEvent.SongMoved(adjustedFrom, adjustedTo))
            }
        }
    )

    uiState.songPendingRemoval?.let { songToRemove ->
        ConfirmRemoveDialog(
            itemType = "song",
            itemName = songToRemove.title,
            containerType = "playlist",
            onDismiss = { viewModel.onEvent(EditPlaylistEvent.CancelSongRemoval) },
            onConfirm = { viewModel.onEvent(EditPlaylistEvent.ConfirmSongRemoval) }
        )
    }
    GradientBackground(gradientColor1 = gradientColor1, gradientColor2 = gradientColor2) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = { Text("Edit Playlist") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                    actions = { TextButton(onClick = { viewModel.onEvent(EditPlaylistEvent.SaveChanges(playlistName)); onBack() }) { Text("Save", color = MaterialTheme.colorScheme.onSurface) } },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.1f),
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            if (uiState.playlistWithSongs == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                ) {
                    item {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CompositeThumbnailImage(
                                urls = songs.map { it.thumbnailUrl },
                                contentDescription = "Playlist thumbnail",
                                processUrls = { urls -> viewModel.thumbnailProcessor.process(urls) },
                                modifier = Modifier.size(150.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, label = { Text("Playlist Name") }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                    items(items = songs, key = { song: Song -> song.songId }) { song ->
                        ReorderableItem(state = state, key = song.songId) {
                            val isDragging = it
                            EditSongItem(
                                song = song,
                                onRemoveClick = { viewModel.onEvent(EditPlaylistEvent.RemoveSongClicked(song)) },
                                modifier = Modifier.shadow(if (isDragging) 4.dp else 0.dp),
                                dragHandleModifier = Modifier.draggableHandle()
                            )
                        }
                    }
                }
            }
        }
    }
}