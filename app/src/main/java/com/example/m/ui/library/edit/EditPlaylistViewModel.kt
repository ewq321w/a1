// file: com/example/m/ui/library/edit/EditPlaylistViewModel.kt
package com.example.m.ui.library.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.database.PlaylistDao
import com.example.m.data.database.PlaylistWithSongs
import com.example.m.data.database.Song
import com.example.m.managers.ThumbnailProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditPlaylistUiState(
    val playlistWithSongs: PlaylistWithSongs? = null,
    val songPendingRemoval: Song? = null
)

sealed interface EditPlaylistEvent {
    data class SongMoved(val from: Int, val to: Int) : EditPlaylistEvent
    data class SaveChanges(val newName: String) : EditPlaylistEvent
    data class RemoveSongClicked(val song: Song) : EditPlaylistEvent
    object ConfirmSongRemoval : EditPlaylistEvent
    object CancelSongRemoval : EditPlaylistEvent
}

@HiltViewModel
class EditPlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistDao: PlaylistDao,
    val thumbnailProcessor: ThumbnailProcessor
) : ViewModel() {

    private val playlistId: Long = checkNotNull(savedStateHandle["playlistId"])

    private val _uiState = MutableStateFlow(EditPlaylistUiState())
    val uiState: StateFlow<EditPlaylistUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val playlist = playlistDao.getPlaylistWithSongsById(playlistId).first()
            _uiState.update { it.copy(playlistWithSongs = playlist) }
        }
    }

    fun onEvent(event: EditPlaylistEvent) {
        when (event) {
            is EditPlaylistEvent.SongMoved -> onSongMoved(event.from, event.to)
            is EditPlaylistEvent.SaveChanges -> saveChanges(event.newName)
            is EditPlaylistEvent.RemoveSongClicked -> _uiState.update { it.copy(songPendingRemoval = event.song) }
            is EditPlaylistEvent.ConfirmSongRemoval -> confirmSongRemoval()
            is EditPlaylistEvent.CancelSongRemoval -> _uiState.update { it.copy(songPendingRemoval = null) }
        }
    }

    private fun onSongMoved(from: Int, to: Int) {
        _uiState.update { currentState ->
            currentState.playlistWithSongs?.let { playlistWithSongs ->
                val mutableSongs = playlistWithSongs.songs.toMutableList()
                if (from in mutableSongs.indices && to in mutableSongs.indices) {
                    val movedSong = mutableSongs.removeAt(from)
                    mutableSongs.add(to, movedSong)
                    currentState.copy(playlistWithSongs = playlistWithSongs.copy(songs = mutableSongs))
                } else {
                    currentState
                }
            } ?: currentState
        }
    }

    private fun saveChanges(newName: String) {
        val currentPlaylistWithSongs = _uiState.value.playlistWithSongs ?: return
        viewModelScope.launch {
            if (currentPlaylistWithSongs.playlist.name != newName) {
                playlistDao.updatePlaylist(currentPlaylistWithSongs.playlist.copy(name = newName))
            }
            playlistDao.updateSongOrder(playlistId, currentPlaylistWithSongs.songs)
        }
    }

    private fun confirmSongRemoval() {
        _uiState.value.songPendingRemoval?.let { songToRemove ->
            viewModelScope.launch {
                playlistDao.deleteSongFromPlaylist(playlistId, songToRemove.songId)
                _uiState.update {
                    val updatedSongs = it.playlistWithSongs?.songs?.filterNot { s -> s.songId == songToRemove.songId }
                    it.copy(
                        songPendingRemoval = null,
                        playlistWithSongs = it.playlistWithSongs?.copy(songs = updatedSongs ?: emptyList())
                    )
                }
            }
        }
    }
}