// file: com/example/m/managers/PlaylistActionsManager.kt
package com.example.m.managers

import com.example.m.data.PreferencesManager
import com.example.m.data.database.LibraryGroup
import com.example.m.data.database.LibraryGroupDao
import com.example.m.data.database.Playlist
import com.example.m.data.repository.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PlaylistActionState {
    object Hidden : PlaylistActionState
    data class AddToPlaylist(val item: Any, val playlists: List<Playlist>) : PlaylistActionState
    data class CreatePlaylist(val pendingItem: Any?, val targetGroupId: Long) : PlaylistActionState
    data class SelectGroupForNewPlaylist(val pendingItem: Any?, val groups: List<LibraryGroup>) : PlaylistActionState
}

@Singleton
class PlaylistActionsManager @Inject constructor(
    private val playlistManager: PlaylistManager,
    private val libraryRepository: LibraryRepository,
    private val libraryGroupDao: LibraryGroupDao,
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
        scope.launch {
            val activeGroupId = preferencesManager.activeLibraryGroupId
            if (activeGroupId == 0L) {
                val groups = libraryGroupDao.getAllGroups().first()
                _state.value = PlaylistActionState.SelectGroupForNewPlaylist(pendingItem, groups)
            } else {
                _state.value = PlaylistActionState.CreatePlaylist(pendingItem, activeGroupId)
            }
        }
    }

    fun onGroupSelectedForNewPlaylist(groupId: Long) {
        val pendingItem = (_state.value as? PlaylistActionState.SelectGroupForNewPlaylist)?.pendingItem
        _state.value = PlaylistActionState.CreatePlaylist(pendingItem, groupId)
    }

    fun onCreatePlaylist(name: String) {
        val currentState = _state.value
        if (currentState is PlaylistActionState.CreatePlaylist) {
            currentState.pendingItem?.let { item ->
                playlistManager.createPlaylistAndAddItem(name, item, currentState.targetGroupId)
            } ?: run {
                playlistManager.createEmptyPlaylist(name, currentState.targetGroupId)
            }
        }
        dismiss()
    }
}