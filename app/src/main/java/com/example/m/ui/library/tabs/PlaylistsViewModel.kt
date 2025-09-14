// file: com/example/m/ui/library/tabs/PlaylistsViewModel.kt
package com.example.m.ui.library.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.PreferencesManager
import com.example.m.data.database.*
import com.example.m.data.repository.LibraryRepository
import com.example.m.managers.PlaylistActionsManager
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
    val playlistToRemoveDownloads: PlaylistWithSongs? = null
)

sealed interface PlaylistTabEvent {
    data class PlayPlaylist(val playlist: PlaylistWithSongs) : PlaylistTabEvent
    data class ShufflePlaylist(val playlist: PlaylistWithSongs) : PlaylistTabEvent
    data class ToggleAutoDownloadPlaylist(val playlist: PlaylistWithSongs) : PlaylistTabEvent
    data class PrepareToRemoveDownloads(val playlist: PlaylistWithSongs) : PlaylistTabEvent
    object CancelRemoveDownloads : PlaylistTabEvent
    object ConfirmRemoveDownloads : PlaylistTabEvent
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
    private val playlistActionsManager: PlaylistActionsManager,
    private val preferencesManager: PreferencesManager,
    val thumbnailProcessor: ThumbnailProcessor
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistsUiState())
    val uiState: StateFlow<PlaylistsUiState> = _uiState.asStateFlow()

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
            is PlaylistTabEvent.ToggleAutoDownloadPlaylist -> toggleAutoDownload(event.playlist)
            is PlaylistTabEvent.PrepareToRemoveDownloads -> _uiState.update { it.copy(playlistToRemoveDownloads = event.playlist) }
            is PlaylistTabEvent.CancelRemoveDownloads -> _uiState.update { it.copy(playlistToRemoveDownloads = null) }
            is PlaylistTabEvent.ConfirmRemoveDownloads -> removeDownloadsForPlaylist()
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

    private fun toggleAutoDownload(playlistWithSongs: PlaylistWithSongs) {
        val playlist = playlistWithSongs.playlist
        val isEnabling = !playlist.downloadAutomatically
        viewModelScope.launch(Dispatchers.IO) {
            playlistDao.updatePlaylist(playlist.copy(downloadAutomatically = isEnabling))
            if (isEnabling) {
                playlistWithSongs.songs.forEach { song ->
                    libraryRepository.startDownload(song)
                }
            }
        }
    }

    private fun removeDownloadsForPlaylist() {
        _uiState.value.playlistToRemoveDownloads?.let { playlist ->
            viewModelScope.launch(Dispatchers.IO) {
                libraryRepository.removeDownloadsForPlaylist(playlist)
                _uiState.update { it.copy(playlistToRemoveDownloads = null) }
            }
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