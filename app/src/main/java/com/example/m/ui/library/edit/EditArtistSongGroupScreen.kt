package com.example.m.ui.library.edit

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditArtistSongGroupScreen(
    onBack: () -> Unit,
    viewModel: EditArtistSongGroupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var groupName by remember(uiState.groupWithSongs) {
        mutableStateOf(uiState.groupWithSongs?.group?.name ?: "")
    }
    val songs = uiState.groupWithSongs?.songs ?: emptyList()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    val activity = LocalContext.current as ComponentActivity
    val mainViewModel: MainViewModel = hiltViewModel(activity)
    val (gradientColor1, gradientColor2) = mainViewModel.randomGradientColors.value

    val state = rememberReorderableLazyListState(onMove = { from, to ->
        val adjustedFrom = from.index - 1
        val adjustedTo = to.index - 1

        if (adjustedFrom >= 0 && adjustedTo >= 0) {
            viewModel.onEvent(EditArtistSongGroupEvent.SongMoved(adjustedFrom, adjustedTo))
        }
    })

    uiState.songPendingRemoval?.let { songToRemove ->
        ConfirmRemoveDialog(
            itemType = "song",
            itemName = songToRemove.title,
            containerType = "group",
            onDismiss = { viewModel.onEvent(EditArtistSongGroupEvent.CancelSongRemoval) },
            onConfirm = { viewModel.onEvent(EditArtistSongGroupEvent.ConfirmSongRemoval) }
        )
    }
    GradientBackground(gradientColor1 = gradientColor1, gradientColor2 = gradientColor2) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                            viewModel.onEvent(EditArtistSongGroupEvent.SaveChanges(groupName))
                            onBack()
                        }) {
                            Text("Save", color = MaterialTheme.colorScheme.onSurface)
                        }
                    },
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
            if (uiState.groupWithSongs == null) {
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
                                processUrls = { urls -> viewModel.thumbnailProcessor.process(urls) },
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

                    items(items = songs, key = { song: Song -> song.songId }) { song ->
                        ReorderableItem(state, key = song.songId) { isDragging ->
                            EditSongItem(
                                song = song,
                                onRemoveClick = { viewModel.onEvent(EditArtistSongGroupEvent.RemoveSongClicked(song)) },
                                state = state,
                                modifier = Modifier.shadow(if (isDragging) 4.dp else 0.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}