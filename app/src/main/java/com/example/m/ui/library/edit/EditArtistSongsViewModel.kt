// file: com/example/m/ui/library/edit/EditArtistSongsViewModel.kt
package com.example.m.ui.library.edit

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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditArtistSongsUiState(
    val artistWithSongs: ArtistWithSongs? = null,
    val itemPendingDeletion: Song? = null
)

sealed interface EditArtistSongsEvent {
    data class SongMoved(val from: Int, val to: Int) : EditArtistSongsEvent
    object SaveChanges : EditArtistSongsEvent
    data class SongRemoveClicked(val song: Song) : EditArtistSongsEvent
    object ConfirmSongDeletion : EditArtistSongsEvent
    object CancelSongDeletion : EditArtistSongsEvent
}

@HiltViewModel
class EditArtistSongsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val artistDao: ArtistDao,
    private val libraryRepository: LibraryRepository,
    val thumbnailProcessor: ThumbnailProcessor
) : ViewModel() {

    private val artistId: Long = checkNotNull(savedStateHandle["artistId"])

    private val _uiState = MutableStateFlow(EditArtistSongsUiState())
    val uiState: StateFlow<EditArtistSongsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val artistSongs = artistDao.getArtistWithLibrarySongs(artistId).first()
            _uiState.update { it.copy(artistWithSongs = artistSongs) }
        }
    }

    fun onEvent(event: EditArtistSongsEvent) {
        when(event) {
            is EditArtistSongsEvent.SongMoved -> onSongMoved(event.from, event.to)
            is EditArtistSongsEvent.SaveChanges -> saveChanges()
            is EditArtistSongsEvent.SongRemoveClicked -> _uiState.update { it.copy(itemPendingDeletion = event.song) }
            is EditArtistSongsEvent.ConfirmSongDeletion -> confirmSongDeletion()
            is EditArtistSongsEvent.CancelSongDeletion -> _uiState.update { it.copy(itemPendingDeletion = null) }
        }
    }

    private fun onSongMoved(from: Int, to: Int) {
        _uiState.update { currentState ->
            currentState.artistWithSongs?.let { artistWithSongs ->
                val mutableSongs = artistWithSongs.songs.toMutableList()
                if (from in mutableSongs.indices && to in mutableSongs.indices) {
                    val movedSong = mutableSongs.removeAt(from)
                    mutableSongs.add(to, movedSong)
                    currentState.copy(artistWithSongs = artistWithSongs.copy(songs = mutableSongs))
                } else {
                    currentState
                }
            } ?: currentState
        }
    }

    private fun saveChanges() {
        _uiState.value.artistWithSongs?.songs?.let { currentSongs ->
            viewModelScope.launch {
                artistDao.updateSongOrder(artistId, currentSongs)
            }
        }
    }

    private fun confirmSongDeletion() {
        _uiState.value.itemPendingDeletion?.let { songToDelete ->
            viewModelScope.launch {
                libraryRepository.deleteSongFromDeviceAndDb(songToDelete)
                _uiState.update {
                    val updatedSongs = it.artistWithSongs?.songs?.filterNot { s -> s.songId == songToDelete.songId }
                    it.copy(
                        itemPendingDeletion = null,
                        artistWithSongs = it.artistWithSongs?.copy(songs = updatedSongs ?: emptyList())
                    )
                }
            }
        }
    }
}