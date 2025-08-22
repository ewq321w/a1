package com.example.m.ui.library.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.database.PlaylistDao
import com.example.m.data.database.PlaylistWithSongs
import com.example.m.data.database.Song
import com.example.m.data.database.SongDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditPlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao
) : ViewModel() {

    private val playlistId: Long = checkNotNull(savedStateHandle["playlistId"])

    private val _playlistWithSongs = MutableStateFlow<PlaylistWithSongs?>(null)
    val playlistWithSongs: StateFlow<PlaylistWithSongs?> = _playlistWithSongs

    // +++ ADD STATE TO TRACK THE SONG PENDING REMOVAL +++
    var songPendingRemoval by mutableStateOf<Song?>(null)
        private set

    init {
        viewModelScope.launch {
            playlistDao.getPlaylistWithSongsById(playlistId).collect {
                _playlistWithSongs.value = it
            }
        }
    }

    fun onSongMoved(from: Int, to: Int) {
        _playlistWithSongs.update { currentPlaylistWithSongs ->
            currentPlaylistWithSongs?.let { playlistWithSongs ->
                val mutableSongs = playlistWithSongs.songs.toMutableList()

                if (from >= 0 && from < mutableSongs.size &&
                    to >= 0 && to < mutableSongs.size &&
                    from != to
                ) {
                    val movedSong = mutableSongs.removeAt(from)
                    mutableSongs.add(to, movedSong)
                    playlistWithSongs.copy(songs = mutableSongs)
                } else {
                    playlistWithSongs
                }
            }
        }
    }

    fun saveChanges(newName: String) {
        val currentPlaylist = _playlistWithSongs.value?.playlist ?: return
        val currentSongs = _playlistWithSongs.value?.songs ?: return

        viewModelScope.launch {
            if (currentPlaylist.name != newName) {
                playlistDao.updatePlaylist(currentPlaylist.copy(name = newName))
            }

            currentSongs.forEachIndexed { index, song ->
                playlistDao.updateSongPositionInPlaylist(playlistId, song.songId, index)
            }
        }
    }

    // --- NEW FUNCTIONS TO HANDLE THE CONFIRMATION FLOW ---

    /**
     * Called when the user clicks the remove icon on a song.
     * This sets the state to show the confirmation dialog.
     */
    fun onRemoveSongClicked(song: Song) {
        songPendingRemoval = song
    }

    /**
     * Called when the user confirms the removal in the dialog.
     */
    fun confirmSongRemoval() {
        songPendingRemoval?.let { songToRemove ->
            viewModelScope.launch {
                playlistDao.deleteSongFromPlaylist(playlistId, songToRemove.songId)
                // Flow will automatically update the list, so we just hide the dialog
                songPendingRemoval = null
            }
        }
    }

    /**
     * Called when the user dismisses the confirmation dialog.
     */
    fun cancelSongRemoval() {
        songPendingRemoval = null
    }
}