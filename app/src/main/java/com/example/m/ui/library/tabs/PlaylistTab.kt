// file: com/example/m/ui/library/tabs/PlaylistTab.kt
package com.example.m.ui.library.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.data.database.PlaylistWithSongs
import com.example.m.managers.ThumbnailProcessor
import com.example.m.ui.library.components.CompositeThumbnailImage
import com.example.m.ui.library.components.ConfirmDeleteDialog
import com.example.m.ui.library.components.DisableAutoDownloadConfirmationDialog
import com.example.m.ui.library.components.EmptyStateMessage
import com.example.m.ui.library.components.TranslucentDropdownMenu

@Composable
fun PlaylistTabContent(
    onPlaylistClick: (Long) -> Unit,
    onEditPlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    uiState.playlistPendingDisableAutoDownload?.let { playlist ->
        DisableAutoDownloadConfirmationDialog(
            itemType = "playlist",
            onDismiss = { viewModel.onEvent(PlaylistTabEvent.DismissDisableAutoDownloadDialog) },
            onConfirmDisableOnly = { viewModel.onEvent(PlaylistTabEvent.DisableAutoDownloadForPlaylist(removeFiles = false)) },
            onConfirmAndRemove = { viewModel.onEvent(PlaylistTabEvent.DisableAutoDownloadForPlaylist(removeFiles = true)) }
        )
    }

    uiState.itemPendingDeletion?.let { playlist ->
        ConfirmDeleteDialog(
            itemType = "playlist",
            itemName = playlist.playlist.playlist.name,
            onDismiss = { viewModel.onEvent(PlaylistTabEvent.ClearItemForDeletion) },
            onConfirm = { viewModel.onEvent(PlaylistTabEvent.ConfirmDeletion) }
        )
    }

    if (uiState.playlists.isEmpty()) {
        EmptyStateMessage(message = "Create your first playlist using the '+' button.")
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            items(items = uiState.playlists, key = { it.playlist.playlistId }) { p ->
                PlaylistItem(
                    playlistWithSongs = p,
                    onClick = { onPlaylistClick(p.playlist.playlistId) },
                    onPlay = { viewModel.onEvent(PlaylistTabEvent.PlayPlaylist(p)) },
                    onShuffle = { viewModel.onEvent(PlaylistTabEvent.ShufflePlaylist(p)) },
                    onToggleAutoDownload = { viewModel.onEvent(PlaylistTabEvent.PrepareToToggleAutoDownloadPlaylist(p)) },
                    onEdit = { onEditPlaylist(p.playlist.playlistId) },
                    onDelete = { viewModel.onEvent(PlaylistTabEvent.SetItemForDeletion(p)) },
                    thumbnailProcessor = viewModel.thumbnailProcessor
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistItem(
    playlistWithSongs: PlaylistWithSongs,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onToggleAutoDownload: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    thumbnailProcessor: ThumbnailProcessor,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(playlistWithSongs.playlist.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playlistWithSongs.playlist.downloadAutomatically) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Downloads Automatically", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(text = "${playlistWithSongs.songs.size} songs", style = MaterialTheme.typography.bodySmall)
            }
        },
        leadingContent = {
            CompositeThumbnailImage(
                urls = playlistWithSongs.songs.map { it.thumbnailUrl },
                contentDescription = "Playlist thumbnail for ${playlistWithSongs.playlist.name}",
                processUrls = { urls -> thumbnailProcessor.process(urls) },
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(3.dp))
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                TranslucentDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Play") }, onClick = { onPlay(); showMenu = false })
                    DropdownMenuItem(text = { Text("Shuffle") }, onClick = { onShuffle(); showMenu = false })
                    DropdownMenuItem(text = { Text("Edit playlist") }, onClick = { onEdit(); showMenu = false })
                    val toggleText = if (playlistWithSongs.playlist.downloadAutomatically) "Disable auto-download" else "Enable auto-download"
                    DropdownMenuItem(text = { Text(toggleText) }, onClick = { onToggleAutoDownload(); showMenu = false })
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { onDelete(); showMenu = false })
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = MaterialTheme.colorScheme.onSurface, supportingColor = MaterialTheme.colorScheme.onSurfaceVariant, trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant),
        modifier = modifier.clickable(onClick = onClick).height(72.dp)
    )
}