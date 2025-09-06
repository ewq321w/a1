// file: com/example/m/ui/library/edit/EditArtistSongsViewModel.kt
package com.example.m.ui.library.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.database.ArtistDao
import com.example.m.data.database.ArtistWithSongs
import com.example.m.data.database.Song
import com.example.m.data.repository.LibraryRepository
import com.example.m.managers.ThumbnailProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditArtistSongsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val artistDao: ArtistDao,
    private val libraryRepository: LibraryRepository,
    private val thumbnailProcessor: ThumbnailProcessor
) : ViewModel() {

    private val artistId: Long = checkNotNull(savedStateHandle["artistId"])

    private val _artistWithSongs = MutableStateFlow<ArtistWithSongs?>(null)
    val artistWithSongs: StateFlow<ArtistWithSongs?> = _artistWithSongs

    var itemPendingDeletion by mutableStateOf<Song?>(null)
        private set

    suspend fun processThumbnails(urls: List<String>) = thumbnailProcessor.process(urls)

    init {
        viewModelScope.launch {
            // FIX: Use getArtistWithLibrarySongs to get the correctly ordered list of songs
            // that are actually in the library, preventing cached-only songs from appearing
            // and ensuring the correct custom order is loaded.
            _artistWithSongs.value = artistDao.getArtistWithLibrarySongs(artistId).first()
        }
    }

    fun onSongMoved(from: Int, to: Int) {
        _artistWithSongs.update { currentArtistWithSongs ->
            currentArtistWithSongs?.let { artistWithSongs ->
                val mutableSongs = artistWithSongs.songs.toMutableList()
                if (from >= 0 && from < mutableSongs.size && to >= 0 && to < mutableSongs.size) {
                    val movedSong = mutableSongs.removeAt(from)
                    mutableSongs.add(to, movedSong)
                    artistWithSongs.copy(songs = mutableSongs)
                } else {
                    artistWithSongs
                }
            }
        }
    }

    fun saveChanges() {
        val currentSongs = _artistWithSongs.value?.songs ?: return

        viewModelScope.launch {
            artistDao.updateSongOrder(artistId, currentSongs)
        }
    }

    fun onSongRemoveClicked(song: Song) {
        itemPendingDeletion = song
    }

    fun confirmSongDeletion() {
        itemPendingDeletion?.let { songToDelete ->
            viewModelScope.launch {
                libraryRepository.deleteSongFromDeviceAndDb(songToDelete)
                itemPendingDeletion = null
            }
        }
    }

    fun cancelSongDeletion() {
        itemPendingDeletion = null
    }
}