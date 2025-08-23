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

/**
 * A stateful class to handle the logic for adding items to a playlist.
 * This can be instantiated in any ViewModel that needs this functionality.
 */
class PlaylistActionHandler(private val playlistManager: PlaylistManager) {
    var itemToAddToPlaylist by mutableStateOf<Any?>(null)
        private set
    var showCreatePlaylistDialog by mutableStateOf(false)
        private set
    private var pendingItem: Any? = null

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

    fun createPlaylistAndAddPendingItem(name: String) {
        pendingItem?.let { item ->
            playlistManager.createPlaylistAndAddItem(name, item)
            pendingItem = null
        }
        dismissCreatePlaylistDialog()
    }

    fun dismissCreatePlaylistDialog() {
        showCreatePlaylistDialog = false
    }
}

/**
 * A shared Composable that renders the necessary UI (bottom sheets, dialogs)
 * for the playlist actions, driven by the state in a PlaylistActionHandler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistActionUI(
    handler: PlaylistActionHandler,
    allPlaylists: List<Playlist>
) {
    val sheetState = rememberModalBottomSheetState()

    if (handler.showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { handler.dismissCreatePlaylistDialog() },
            onCreate = { name -> handler.createPlaylistAndAddPendingItem(name) }
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