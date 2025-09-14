// file: com/example/m/managers/PlaylistActionsManager.kt
package com.example.m.managers

import com.example.m.data.PreferencesManager
import com.example.m.data.database.Playlist
import com.example.m.data.repository.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PlaylistActionState {
    object Hidden : PlaylistActionState
    data class AddToPlaylist(val item: Any, val playlists: List<Playlist>) : PlaylistActionState
    data class CreatePlaylist(val pendingItem: Any?) : PlaylistActionState
}

@Singleton
class PlaylistActionsManager @Inject constructor(
    private val playlistManager: PlaylistManager,
    private val libraryRepository: LibraryRepository,
    private val preferencesManager: PreferencesManager,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow<PlaylistActionState>(PlaylistActionState.Hidden)
    val state = _state.asStateFlow()

    fun selectItem(item: Any) {
        scope.launch {
            val playlists = libraryRepository.getAllPlaylists().first()
            _state.value = PlaylistActionState.AddToPlaylist(item, playlists)
        }
    }

    fun dismiss() {
        _state.value = PlaylistActionState.Hidden
    }

    fun onPlaylistSelected(playlistId: Long) {
        val currentState = _state.value
        if (currentState is PlaylistActionState.AddToPlaylist) {
            playlistManager.addItemToPlaylist(playlistId, currentState.item)
        }
        dismiss()
    }

    fun prepareToCreatePlaylist() {
        val pendingItem = (_state.value as? PlaylistActionState.AddToPlaylist)?.item
        _state.value = PlaylistActionState.CreatePlaylist(pendingItem)
    }

    fun onCreatePlaylist(name: String) {
        val currentState = _state.value
        if (currentState is PlaylistActionState.CreatePlaylist) {
            val activeGroupId = preferencesManager.activeLibraryGroupId
            // We can only create playlists inside a specific library group, not "All Music".
            // A more robust solution could prompt the user to select a group if one isn't active.
            if (activeGroupId != 0L) {
                currentState.pendingItem?.let { item ->
                    playlistManager.createPlaylistAndAddItem(name, item, activeGroupId)
                }
            }
        }
        dismiss()
    }
}