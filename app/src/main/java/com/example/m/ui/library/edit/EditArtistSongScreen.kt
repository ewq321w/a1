package com.example.m.ui.library.edit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.ui.library.components.CompositeThumbnailImage
import com.example.m.ui.library.components.ConfirmDeleteDialog
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditArtistSongsScreen(
    onBack: () -> Unit,
    viewModel: EditArtistSongsViewModel = hiltViewModel()
) {
    val artistWithSongs by viewModel.artistWithSongs.collectAsState()
    val songs = artistWithSongs?.songs ?: emptyList()
    val songPendingDeletion by remember { derivedStateOf { viewModel.itemPendingDeletion } }

    val state = rememberReorderableLazyListState(onMove = { from, to ->
        val adjustedFrom = from.index - 1
        val adjustedTo = to.index - 1
        if (adjustedFrom >= 0 && adjustedTo >= 0) {
            viewModel.onSongMoved(adjustedFrom, adjustedTo)
        }
    })

    songPendingDeletion?.let { song ->
        ConfirmDeleteDialog(
            itemType = "song",
            itemName = song.title,
            onDismiss = { viewModel.cancelSongDeletion() },
            onConfirm = { viewModel.confirmSongDeletion() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit " + (artistWithSongs?.artist?.name ?: "Artist")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.saveChanges()
                        onBack()
                    }) {
                        Text("Save")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (artistWithSongs == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                state = state.listState,
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .reorderable(state)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CompositeThumbnailImage(
                            urls = songs.map { it.thumbnailUrl },
                            contentDescription = "Artist thumbnail collage",
                            processUrls = viewModel::processThumbnails,
                            modifier = Modifier.size(150.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = artistWithSongs?.artist?.name ?: "",
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }

                items(songs, { it.songId }) { song ->
                    ReorderableItem(state, key = song.songId) { isDragging ->
                        val onRemoveClickRemembered = remember(song) { { viewModel.onSongRemoveClicked(song) } }

                        EditSongItem(
                            song = song,
                            onRemoveClick = onRemoveClickRemembered,
                            state = state,
                            modifier = Modifier.shadow(if (isDragging) 4.dp else 0.dp)
                        )
                    }
                }
            }
        }
    }
}