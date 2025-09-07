// file: com/example/m/ui/library/edit/EditArtistSongGroupViewModel.kt
package com.example.m.ui.library.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.database.*
import com.example.m.managers.ThumbnailProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditArtistSongGroupUiState(
    val groupWithSongs: ArtistSongGroupWithSongs? = null,
    val songPendingRemoval: Song? = null
)

sealed interface EditArtistSongGroupEvent {
    data class SongMoved(val from: Int, val to: Int) : EditArtistSongGroupEvent
    data class SaveChanges(val newName: String) : EditArtistSongGroupEvent
    data class RemoveSongClicked(val song: Song) : EditArtistSongGroupEvent
    object ConfirmSongRemoval : EditArtistSongGroupEvent
    object CancelSongRemoval : EditArtistSongGroupEvent
}

@HiltViewModel
class EditArtistSongGroupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val artistDao: ArtistDao,
    val thumbnailProcessor: ThumbnailProcessor
) : ViewModel() {

    private val groupId: Long = checkNotNull(savedStateHandle["groupId"])

    private val _uiState = MutableStateFlow(EditArtistSongGroupUiState())
    val uiState: StateFlow<EditArtistSongGroupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val group = artistDao.getArtistSongGroupWithSongsOrdered(groupId)
                .map { map ->
                    map.entries.firstOrNull()?.let { entry ->
                        ArtistSongGroupWithSongs(
                            group = entry.key,
                            songs = entry.value.filter { it.songId != 0L }
                        )
                    }
                }
                .first()
            _uiState.update { it.copy(groupWithSongs = group) }
        }
    }

    fun onEvent(event: EditArtistSongGroupEvent) {
        when (event) {
            is EditArtistSongGroupEvent.SongMoved -> onSongMoved(event.from, event.to)
            is EditArtistSongGroupEvent.SaveChanges -> saveChanges(event.newName)
            is EditArtistSongGroupEvent.RemoveSongClicked -> _uiState.update { it.copy(songPendingRemoval = event.song) }
            is EditArtistSongGroupEvent.ConfirmSongRemoval -> confirmSongRemoval()
            is EditArtistSongGroupEvent.CancelSongRemoval -> _uiState.update { it.copy(songPendingRemoval = null) }
        }
    }

    private fun onSongMoved(from: Int, to: Int) {
        _uiState.update { currentState ->
            currentState.groupWithSongs?.let { groupWithSongs ->
                val mutableSongs = groupWithSongs.songs.toMutableList()
                if (from in mutableSongs.indices && to in mutableSongs.indices) {
                    val movedSong = mutableSongs.removeAt(from)
                    mutableSongs.add(to, movedSong)
                    currentState.copy(groupWithSongs = groupWithSongs.copy(songs = mutableSongs))
                } else {
                    currentState
                }
            } ?: currentState
        }
    }

    private fun saveChanges(newName: String) {
        val currentGroupWithSongs = _uiState.value.groupWithSongs ?: return
        viewModelScope.launch {
            if (currentGroupWithSongs.group.name != newName) {
                artistDao.updateArtistSongGroup(currentGroupWithSongs.group.copy(name = newName))
            }
            artistDao.updateGroupSongOrder(groupId, currentGroupWithSongs.songs)
        }
    }

    private fun confirmSongRemoval() {
        _uiState.value.songPendingRemoval?.let { songToRemove ->
            viewModelScope.launch {
                artistDao.deleteSongFromArtistSongGroup(groupId, songToRemove.songId)
                _uiState.update {
                    val updatedSongs = it.groupWithSongs?.songs?.filterNot { s -> s.songId == songToRemove.songId }
                    it.copy(
                        songPendingRemoval = null,
                        groupWithSongs = it.groupWithSongs?.copy(songs = updatedSongs ?: emptyList())
                    )
                }
            }
        }
    }
}