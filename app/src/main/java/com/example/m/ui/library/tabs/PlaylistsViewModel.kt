// file: com/example/m/ui/library/tabs/PlaylistsViewModel.kt
package com.example.m.ui.library.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.PreferencesManager
import com.example.m.data.database.*
import com.example.m.data.repository.LibraryRepository
import com.example.m.managers.PlaylistActionState
import com.example.m.managers.PlaylistActionsManager
import com.example.m.managers.PlaylistManager
import com.example.m.managers.ThumbnailProcessor
import com.example.m.playback.MusicServiceConnection
import com.example.m.ui.library.DeletableItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistsUiState(
    val playlists: List<PlaylistWithSongs> = emptyList(),
    val itemPendingDeletion: DeletableItem.DeletablePlaylist? = null,
    val playlistPendingDisableAutoDownload: PlaylistWithSongs? = null
)

sealed interface PlaylistTabEvent {
    data class PlayPlaylist(val playlist: PlaylistWithSongs) : PlaylistTabEvent
    data class ShufflePlaylist(val playlist: PlaylistWithSongs) : PlaylistTabEvent
    data class PrepareToToggleAutoDownloadPlaylist(val playlist: PlaylistWithSongs) : PlaylistTabEvent
    object DismissDisableAutoDownloadDialog : PlaylistTabEvent
    data class DisableAutoDownloadForPlaylist(val removeFiles: Boolean) : PlaylistTabEvent
    data class SetItemForDeletion(val playlist: PlaylistWithSongs) : PlaylistTabEvent
    object ClearItemForDeletion : PlaylistTabEvent
    object ConfirmDeletion : PlaylistTabEvent
    object CreateEmptyPlaylist: PlaylistTabEvent
}

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val musicServiceConnection: MusicServiceConnection,
    private val playlistDao: PlaylistDao,
    private val playlistManager: PlaylistManager,
    private val playlistActionsManager: PlaylistActionsManager,
    private val preferencesManager: PreferencesManager,
    val thumbnailProcessor: ThumbnailProcessor
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistsUiState())
    val uiState: StateFlow<PlaylistsUiState> = _uiState.asStateFlow()

    val playlistActionState: StateFlow<PlaylistActionState> = playlistActionsManager.state

    init {
        preferencesManager.getActiveLibraryGroupIdFlow()
            .flatMapLatest { groupId ->
                if (groupId == 0L) libraryRepository.getPlaylistsWithSongs()
                else libraryRepository.getPlaylistsWithSongs(groupId)
            }
            .onEach { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: PlaylistTabEvent) {
        when (event) {
            is PlaylistTabEvent.PlayPlaylist -> playPlaylist(event.playlist)
            is PlaylistTabEvent.ShufflePlaylist -> shufflePlaylist(event.playlist)
            is PlaylistTabEvent.PrepareToToggleAutoDownloadPlaylist -> prepareToToggleAutoDownload(event.playlist)
            is PlaylistTabEvent.DismissDisableAutoDownloadDialog -> _uiState.update { it.copy(playlistPendingDisableAutoDownload = null) }
            is PlaylistTabEvent.DisableAutoDownloadForPlaylist -> disableAutoDownload(event.removeFiles)
            is PlaylistTabEvent.SetItemForDeletion -> _uiState.update { it.copy(itemPendingDeletion = DeletableItem.DeletablePlaylist(event.playlist)) }
            is PlaylistTabEvent.ClearItemForDeletion -> _uiState.update { it.copy(itemPendingDeletion = null) }
            is PlaylistTabEvent.ConfirmDeletion -> confirmDeletion()
            is PlaylistTabEvent.CreateEmptyPlaylist -> playlistActionsManager.prepareToCreatePlaylist()
        }
    }

    private fun playPlaylist(playlist: PlaylistWithSongs) {
        viewModelScope.launch {
            musicServiceConnection.playSongList(playlist.songs, 0)
        }
    }

    private fun shufflePlaylist(playlist: PlaylistWithSongs) {
        val songsToPlay = playlist.songs
        if (songsToPlay.isNotEmpty()) {
            val finalShuffledList = songsToPlay.shuffled()
            viewModelScope.launch {
                musicServiceConnection.playSongList(finalShuffledList, 0)
            }
        }
    }

    private fun prepareToToggleAutoDownload(playlistWithSongs: PlaylistWithSongs) {
        val playlist = playlistWithSongs.playlist
        if (playlist.downloadAutomatically) {
            _uiState.update { it.copy(playlistPendingDisableAutoDownload = playlistWithSongs) }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                playlistDao.updatePlaylist(playlist.copy(downloadAutomatically = true))
                playlistWithSongs.songs.forEach { song ->
                    libraryRepository.startDownload(song)
                }
            }
        }
    }

    private fun disableAutoDownload(removeFiles: Boolean) {
        val playlistWithSongs = _uiState.value.playlistPendingDisableAutoDownload ?: return
        viewModelScope.launch(Dispatchers.IO) {
            // Always disable the flag
            playlistDao.updatePlaylist(playlistWithSongs.playlist.copy(downloadAutomatically = false))

            // Conditionally remove the files
            if (removeFiles) {
                libraryRepository.removeDownloadsForPlaylist(playlistWithSongs)
            }

            // Hide the dialog
            _uiState.update { it.copy(playlistPendingDisableAutoDownload = null) }
        }
    }

    private fun confirmDeletion() {
        _uiState.value.itemPendingDeletion?.let { item ->
            viewModelScope.launch {
                libraryRepository.deletePlaylist(item.playlist.playlist.playlistId)
                _uiState.update { it.copy(itemPendingDeletion = null) }
            }
        }
    }
}