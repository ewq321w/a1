package com.example.m.ui.library.edit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.data.database.Song
import com.example.m.ui.library.components.CompositeThumbnailImage
import com.example.m.ui.library.components.ConfirmRemoveDialog
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditArtistSongGroupScreen(
    onBack: () -> Unit,
    viewModel: EditArtistSongGroupViewModel = hiltViewModel()
) {
    val groupWithSongs by viewModel.groupWithSongs.collectAsState()
    var groupName by remember(groupWithSongs) {
        mutableStateOf(groupWithSongs?.group?.name ?: "")
    }
    val songPendingRemoval by remember { derivedStateOf { viewModel.songPendingRemoval } }

    val songs = groupWithSongs?.songs ?: emptyList()

    val state = rememberReorderableLazyListState(onMove = { from, to ->
        val adjustedFrom = from.index - 1
        val adjustedTo = to.index - 1

        if (adjustedFrom >= 0 && adjustedTo >= 0) {
            viewModel.onSongMoved(adjustedFrom, adjustedTo)
        }
    })

    songPendingRemoval?.let { songToRemove ->
        ConfirmRemoveDialog(
            itemType = "song",
            itemName = songToRemove.title,
            containerType = "group",
            onDismiss = { viewModel.cancelSongRemoval() },
            onConfirm = { viewModel.confirmSongRemoval() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Group") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.saveChanges(groupName)
                        onBack()
                    }) {
                        Text("Save")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (groupWithSongs == null) {
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
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CompositeThumbnailImage(
                            urls = songs.map { it.thumbnailUrl },
                            contentDescription = "Group thumbnail",
                            processUrls = viewModel::processThumbnails,
                            modifier = Modifier.size(150.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = groupName,
                            onValueChange = { groupName = it },
                            label = { Text("Group Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }

                items(
                    items = songs,
                    key = { song: Song -> song.songId }
                ) { song ->
                    ReorderableItem(state, key = song.songId) { isDragging ->
                        val onRemoveClickRemembered = remember(song) { { viewModel.onRemoveSongClicked(song) } }

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