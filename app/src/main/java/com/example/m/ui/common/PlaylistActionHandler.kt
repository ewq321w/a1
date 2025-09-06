package com.example.m.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.m.data.database.Playlist
import com.example.m.data.database.Song
import com.example.m.managers.PlaylistManager
import com.example.m.ui.library.components.AddToPlaylistSheet
import com.example.m.ui.library.components.CreatePlaylistDialog
import com.example.m.ui.search.SearchResult
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class PlaylistActionHandler(private val playlistManager: PlaylistManager) {
    var itemToAddToPlaylist by mutableStateOf<Any?>(null)
        private set
    var showCreatePlaylistDialog by mutableStateOf(false)
        private set
    var pendingItem: Any? = null
        private set

    fun selectItemForPlaylist(item: Any) {
        val validItem = when (item) {
            is SearchResult -> item.streamInfo
            is Song, is StreamInfoItem -> item
            else -> null
        }
        validItem?.let { itemToAddToPlaylist = it }
    }

    fun dismissAddToPlaylistSheet() {
        itemToAddToPlaylist = null
    }

    fun onPlaylistSelectedForAddition(playlistId: Long) {
        itemToAddToPlaylist?.let { item ->
            playlistManager.addItemToPlaylist(playlistId, item)
        }
        dismissAddToPlaylistSheet()
    }

    fun prepareToCreatePlaylist() {
        pendingItem = itemToAddToPlaylist
        dismissAddToPlaylistSheet()
        showCreatePlaylistDialog = true
    }

    fun dismissCreatePlaylistDialog() {
        showCreatePlaylistDialog = false
        pendingItem = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistActionUI(
    handler: PlaylistActionHandler,
    allPlaylists: List<Playlist>,
    onCreatePlaylist: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    if (handler.showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { handler.dismissCreatePlaylistDialog() },
            onCreate = { name ->
                handler.dismissCreatePlaylistDialog()
                onCreatePlaylist(name)
            }
        )
    }

    handler.itemToAddToPlaylist?.let { item ->
        val songTitle: String
        val songArtist: String
        val thumbnailUrl: String

        when (item) {
            is Song -> {
                songTitle = item.title
                songArtist = item.artist
                thumbnailUrl = item.getHighQualityThumbnailUrl()
            }
            is StreamInfoItem -> {
                songTitle = item.name ?: "Unknown"
                songArtist = item.uploaderName ?: "Unknown"
                thumbnailUrl = item.getHighQualityThumbnailUrl()
            }
            else -> return@let
        }

        ModalBottomSheet(
            onDismissRequest = { handler.dismissAddToPlaylistSheet() },
            sheetState = sheetState
        ) {
            AddToPlaylistSheet(
                songTitle = songTitle,
                songArtist = songArtist,
                songThumbnailUrl = thumbnailUrl,
                playlists = allPlaylists,
                onPlaylistSelected = { playlistId ->
                    handler.onPlaylistSelectedForAddition(playlistId)
                },
                onCreateNewPlaylist = {
                    handler.prepareToCreatePlaylist()
                }
            )
        }
    }
}