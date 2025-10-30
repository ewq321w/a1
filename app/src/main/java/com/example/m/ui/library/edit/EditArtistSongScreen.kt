// file: com/example/m/ui/library/edit/EditArtistSongScreen.kt
package com.example.m.ui.library.edit

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.ui.common.GradientBackground
import com.example.m.ui.library.components.CompositeThumbnailImage
import com.example.m.ui.library.components.ConfirmDeleteDialog
import com.example.m.ui.main.MainViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditArtistSongsScreen(
    onBack: () -> Unit,
    viewModel: EditArtistSongsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val artistWithSongs = uiState.artistWithSongs
    val songs = artistWithSongs?.songs ?: emptyList()
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
                viewModel.onEvent(EditArtistSongsEvent.SongMoved(adjustedFrom, adjustedTo))
            }
        }
    )

    uiState.itemPendingDeletion?.let { song ->
        ConfirmDeleteDialog(
            itemType = "song",
            itemName = song.title,
            onDismiss = { viewModel.onEvent(EditArtistSongsEvent.CancelSongDeletion) },
            onConfirm = { viewModel.onEvent(EditArtistSongsEvent.ConfirmSongDeletion) }
        )
    }

    GradientBackground(gradientColor1 = gradientColor1, gradientColor2 = gradientColor2) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = { Text("Edit " + (artistWithSongs?.artist?.name ?: "Artist")) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                    actions = { TextButton(onClick = { viewModel.onEvent(EditArtistSongsEvent.SaveChanges); onBack() }) { Text("Save", color = MaterialTheme.colorScheme.onSurface) } },
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
            if (artistWithSongs == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                ) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CompositeThumbnailImage(
                                urls = songs.map { it.thumbnailUrl },
                                contentDescription = "Artist thumbnail collage",
                                processUrls = { urls -> viewModel.thumbnailProcessor.process(urls) },
                                modifier = Modifier.size(150.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(text = artistWithSongs.artist.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, modifier = Modifier.basicMarquee())
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                    items(songs, { it.songId }) { song ->
                        ReorderableItem(state = state, key = song.songId) {
                            val isDragging = it
                            EditSongItem(
                                song = song,
                                onRemoveClick = { viewModel.onEvent(EditArtistSongsEvent.SongRemoveClicked(song)) },
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