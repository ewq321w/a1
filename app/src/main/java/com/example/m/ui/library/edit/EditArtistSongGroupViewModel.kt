// file: com/example/m/ui/library/edit/EditArtistSongGroupViewModel.kt
package com.example.m.ui.library.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.database.*
import com.example.m.managers.ThumbnailProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditArtistSongGroupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val artistDao: ArtistDao,
    private val thumbnailProcessor: ThumbnailProcessor
) : ViewModel() {

    private val groupId: Long = checkNotNull(savedStateHandle["groupId"])

    private val _groupWithSongs = MutableStateFlow<ArtistSongGroupWithSongs?>(null)
    val groupWithSongs: StateFlow<ArtistSongGroupWithSongs?> = _groupWithSongs

    var songPendingRemoval by mutableStateOf<Song?>(null)
        private set

    suspend fun processThumbnails(urls: List<String>) = thumbnailProcessor.process(urls)

    init {
        viewModelScope.launch {
            _groupWithSongs.value = artistDao.getArtistSongGroupWithSongsOrdered(groupId)
                .map { map ->
                    map.entries.firstOrNull()?.let { entry ->
                        ArtistSongGroupWithSongs(
                            group = entry.key,
                            songs = entry.value.filter { it.songId != 0L }
                        )
                    }
                }
                .first()
        }
    }

    fun onSongMoved(from: Int, to: Int) {
        _groupWithSongs.update { currentGroupWithSongs ->
            currentGroupWithSongs?.let { groupWithSongs ->
                val mutableSongs = groupWithSongs.songs.toMutableList()

                if (from >= 0 && from < mutableSongs.size &&
                    to >= 0 && to < mutableSongs.size &&
                    from != to
                ) {
                    val movedSong = mutableSongs.removeAt(from)
                    mutableSongs.add(to, movedSong)
                    groupWithSongs.copy(songs = mutableSongs)
                } else {
                    groupWithSongs
                }
            }
        }
    }

    fun saveChanges(newName: String) {
        val currentGroupWithSongs = _groupWithSongs.value ?: return
        val currentGroup = currentGroupWithSongs.group
        val currentSongs = currentGroupWithSongs.songs

        viewModelScope.launch {
            if (currentGroup.name != newName) {
                artistDao.updateArtistSongGroup(currentGroup.copy(name = newName))
            }
            artistDao.updateGroupSongOrder(groupId, currentSongs)
        }
    }

    fun onRemoveSongClicked(song: Song) {
        songPendingRemoval = song
    }

    fun confirmSongRemoval() {
        songPendingRemoval?.let { songToRemove ->
            viewModelScope.launch {
                artistDao.deleteSongFromArtistSongGroup(groupId, songToRemove.songId)
                songPendingRemoval = null
            }
        }
    }

    fun cancelSongRemoval() {
        songPendingRemoval = null
    }
}