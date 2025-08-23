package com.example.m.ui.library.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.m.data.database.PlaylistWithSongs
import com.example.m.ui.library.DeletableItem
import com.example.m.ui.library.LibraryViewModel
import com.example.m.ui.library.components.CompositeThumbnailImage
import com.example.m.ui.library.components.ConfirmDeleteDialog
import com.example.m.ui.library.components.EmptyStateMessage

@Composable
fun PlaylistTabContent(
    playlists: List<PlaylistWithSongs>,
    onPlaylistClick: (Long) -> Unit,
    onEditPlaylist: (Long) -> Unit,
    viewModel: LibraryViewModel
) {
    var playlistToRemoveDownloads by remember { mutableStateOf<PlaylistWithSongs?>(null) }

    playlistToRemoveDownloads?.let { playlist ->
        ConfirmDeleteDialog(
            itemType = "downloads for",
            itemName = playlist.playlist.name,
            onDismiss = { playlistToRemoveDownloads = null },
            onConfirm = {
                viewModel.removeDownloadsForPlaylist(playlist)
                playlistToRemoveDownloads = null
            }
        )
    }

    if (playlists.isEmpty()) {
        EmptyStateMessage(message = "Create your first playlist using the '+' button.")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(
                items = playlists,
                key = { it.playlist.playlistId },
                contentType = { "Playlist" }
            ) { p ->
                val rememberedOnClick = remember { { onPlaylistClick(p.playlist.playlistId) } }
                val rememberedOnPlay = remember { { viewModel.playPlaylist(p) } }
                val rememberedOnShuffle = remember { { viewModel.shufflePlaylist(p) } }
                val rememberedOnToggleAutoDownload = remember { { viewModel.toggleAutoDownload(p) } }
                val rememberedOnRemoveDownloads = remember { { playlistToRemoveDownloads = p } }
                val rememberedOnEdit = remember { { onEditPlaylist(p.playlist.playlistId) } }
                val rememberedOnDelete = remember { { viewModel.itemPendingDeletion.value = DeletableItem.DeletablePlaylist(p) } }

                PlaylistItem(
                    playlistWithSongs = p,
                    onClick = rememberedOnClick,
                    onPlay = rememberedOnPlay,
                    onShuffle = rememberedOnShuffle,
                    onToggleAutoDownload = rememberedOnToggleAutoDownload,
                    onRemoveDownloads = rememberedOnRemoveDownloads,
                    onEdit = rememberedOnEdit,
                    onDelete = rememberedOnDelete,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun PlaylistItem(
    playlistWithSongs: PlaylistWithSongs,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onToggleAutoDownload: () -> Unit,
    onRemoveDownloads: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    viewModel: LibraryViewModel
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(playlistWithSongs.playlist.name, fontWeight = FontWeight.Bold) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playlistWithSongs.playlist.downloadAutomatically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Downloads Automatically",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp)
                    )
                }
                Text("${playlistWithSongs.songs.size} songs")
            }
        },
        leadingContent = {
            CompositeThumbnailImage(
                urls = playlistWithSongs.songs.map { it.thumbnailUrl },
                contentDescription = "Playlist thumbnail for ${playlistWithSongs.playlist.name}",
                processUrls = viewModel::processThumbnails,
                modifier = Modifier.size(50.dp)
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Play") }, onClick = { onPlay(); showMenu = false })
                    DropdownMenuItem(text = { Text("Shuffle") }, onClick = { onShuffle(); showMenu = false })
                    DropdownMenuItem(text = { Text("Edit playlist") }, onClick = { onEdit(); showMenu = false })
                    val toggleText = if (playlistWithSongs.playlist.downloadAutomatically) "Disable auto-download" else "Enable auto-download"
                    DropdownMenuItem(text = { Text(toggleText) }, onClick = { onToggleAutoDownload(); showMenu = false })
                    DropdownMenuItem(text = { Text("Remove all downloads") }, onClick = { onRemoveDownloads(); showMenu = false })
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { onDelete(); showMenu = false })
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = MaterialTheme.colorScheme.onSurface,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
            trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.clickable(onClick = onClick)
    )
}