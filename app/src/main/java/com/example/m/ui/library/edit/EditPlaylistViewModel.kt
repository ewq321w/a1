// file: com/example/m/ui/library/edit/EditPlaylistViewModel.kt
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
import com.example.m.managers.ThumbnailProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditPlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao,
    private val thumbnailProcessor: ThumbnailProcessor
) : ViewModel() {

    private val playlistId: Long = checkNotNull(savedStateHandle["playlistId"])

    private val _playlistWithSongs = MutableStateFlow<PlaylistWithSongs?>(null)
    val playlistWithSongs: StateFlow<PlaylistWithSongs?> = _playlistWithSongs

    var songPendingRemoval by mutableStateOf<Song?>(null)
        private set

    suspend fun processThumbnails(urls: List<String>) = thumbnailProcessor.process(urls)

    init {
        viewModelScope.launch {
            _playlistWithSongs.value = playlistDao.getPlaylistWithSongsById(playlistId).first()
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
        val currentPlaylistWithSongs = _playlistWithSongs.value ?: return
        val currentPlaylist = currentPlaylistWithSongs.playlist
        val currentSongs = currentPlaylistWithSongs.songs

        viewModelScope.launch {
            if (currentPlaylist.name != newName) {
                playlistDao.updatePlaylist(currentPlaylist.copy(name = newName))
            }
            playlistDao.updateSongOrder(playlistId, currentSongs)
        }
    }

    fun onRemoveSongClicked(song: Song) {
        songPendingRemoval = song
    }

    fun confirmSongRemoval() {
        songPendingRemoval?.let { songToRemove ->
            viewModelScope.launch {
                playlistDao.deleteSongFromPlaylist(playlistId, songToRemove.songId)
                songPendingRemoval = null
            }
        }
    }

    fun cancelSongRemoval() {
        songPendingRemoval = null
    }
}