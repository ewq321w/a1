// file: com/example/m/ui/library/details/ArtistSongGroupDetailViewModel.kt
package com.example.m.ui.library.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.database.*
import com.example.m.data.repository.LibraryRepository
import com.example.m.managers.LibraryActionsManager
import com.example.m.managers.PlaylistActionState
import com.example.m.managers.PlaylistActionsManager
import com.example.m.playback.MusicServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistSongGroupDetailUiState(
    val groupWithSongs: ArtistSongGroupWithSongs? = null,
    val songs: List<Song> = emptyList(),
    val songPendingRemoval: Song? = null,
    val groupPendingDeletion: ArtistSongGroup? = null,
    val nowPlayingMediaId: String? = null
)

sealed interface ArtistSongGroupDetailEvent {
    data class SongSelected(val index: Int) : ArtistSongGroupDetailEvent
    object ShuffleGroup : ArtistSongGroupDetailEvent
    data class PrepareToRemoveSong(val song: Song) : ArtistSongGroupDetailEvent
    object ConfirmRemoveSong : ArtistSongGroupDetailEvent
    object CancelRemoveSong : ArtistSongGroupDetailEvent
    object PrepareToDeleteGroup : ArtistSongGroupDetailEvent
    object ConfirmDeleteGroupOnly : ArtistSongGroupDetailEvent
    object ConfirmDeleteGroupAndSongs : ArtistSongGroupDetailEvent
    object CancelDeleteGroup : ArtistSongGroupDetailEvent
    data class PlayNext(val song: Song) : ArtistSongGroupDetailEvent
    data class AddToQueue(val song: Song) : ArtistSongGroupDetailEvent
    data class ShuffleSong(val song: Song) : ArtistSongGroupDetailEvent
    data class GoToArtist(val song: Song) : ArtistSongGroupDetailEvent
    data class AddToPlaylist(val item: Any) : ArtistSongGroupDetailEvent
}

@HiltViewModel
class ArtistSongGroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicServiceConnection: MusicServiceConnection,
    private val artistDao: ArtistDao,
    private val libraryRepository: LibraryRepository,
    private val playlistActionsManager: PlaylistActionsManager,
    private val libraryActionsManager: LibraryActionsManager
) : ViewModel() {
    private val groupId: Long = checkNotNull(savedStateHandle["groupId"])

    private val _uiState = MutableStateFlow(ArtistSongGroupDetailUiState())
    val uiState: StateFlow<ArtistSongGroupDetailUiState> = _uiState.asStateFlow()

    val playlistActionState: StateFlow<PlaylistActionState> = playlistActionsManager.state

    private val _navigateToArtist = MutableSharedFlow<Long>()
    val navigateToArtist: SharedFlow<Long> = _navigateToArtist.asSharedFlow()

    private val _navigateBack = MutableSharedFlow<Unit>()
    val navigateBack: SharedFlow<Unit> = _navigateBack.asSharedFlow()

    init {
        val groupWithSongsFlow = artistDao.getArtistSongGroupWithSongsOrdered(groupId)
            .map { map ->
                map.entries.firstOrNull()?.let { entry ->
                    ArtistSongGroupWithSongs(
                        group = entry.key,
                        songs = entry.value.filter { it.songId != 0L }
                    )
                }
            }

        viewModelScope.launch {
            groupWithSongsFlow.collect { group ->
                _uiState.update { it.copy(
                    groupWithSongs = group,
                    songs = group?.songs ?: emptyList()
                ) }
            }
        }

        viewModelScope.launch {
            musicServiceConnection.currentMediaId.collect { mediaId ->
                _uiState.update { it.copy(nowPlayingMediaId = mediaId) }
            }
        }
    }

    fun onEvent(event: ArtistSongGroupDetailEvent) {
        when (event) {
            is ArtistSongGroupDetailEvent.SongSelected -> onSongSelected(event.index)
            is ArtistSongGroupDetailEvent.ShuffleGroup -> shuffleGroup()
            is ArtistSongGroupDetailEvent.PrepareToRemoveSong -> _uiState.update { it.copy(songPendingRemoval = event.song) }
            is ArtistSongGroupDetailEvent.ConfirmRemoveSong -> confirmRemoveSongFromGroup()
            is ArtistSongGroupDetailEvent.CancelRemoveSong -> _uiState.update { it.copy(songPendingRemoval = null) }
            is ArtistSongGroupDetailEvent.PlayNext -> musicServiceConnection.playNext(event.song)
            is ArtistSongGroupDetailEvent.AddToQueue -> musicServiceConnection.addToQueue(event.song)
            is ArtistSongGroupDetailEvent.ShuffleSong -> onShuffleSong(event.song)
            is ArtistSongGroupDetailEvent.GoToArtist -> onGoToArtist(event.song)
            is ArtistSongGroupDetailEvent.AddToPlaylist -> playlistActionsManager.selectItem(event.item)
            is ArtistSongGroupDetailEvent.PrepareToDeleteGroup -> _uiState.update { it.copy(groupPendingDeletion = it.groupWithSongs?.group) }
            is ArtistSongGroupDetailEvent.CancelDeleteGroup -> _uiState.update { it.copy(groupPendingDeletion = null) }
            is ArtistSongGroupDetailEvent.ConfirmDeleteGroupOnly -> confirmDeleteGroupOnly()
            is ArtistSongGroupDetailEvent.ConfirmDeleteGroupAndSongs -> confirmDeleteGroupAndSongs()
        }
    }

    fun onPlaylistCreateConfirm(name: String) = playlistActionsManager.onCreatePlaylist(name)
    fun onPlaylistSelected(playlistId: Long) = playlistActionsManager.onPlaylistSelected(playlistId)
    fun onPlaylistActionDismiss() = playlistActionsManager.dismiss()
    fun onPrepareToCreatePlaylist() = playlistActionsManager.prepareToCreatePlaylist()
    fun onGroupSelectedForNewPlaylist(groupId: Long) = playlistActionsManager.onGroupSelectedForNewPlaylist(groupId)
    fun onDialogRequestCreateGroup() = libraryActionsManager.requestCreateGroup()


    private fun onSongSelected(selectedIndex: Int) {
        val currentSongs = _uiState.value.songs
        if (currentSongs.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(currentSongs, selectedIndex)
            }
        }
    }

    private fun shuffleGroup() {
        val currentSongs = _uiState.value.songs
        if (currentSongs.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(currentSongs.shuffled(), 0)
            }
        }
    }

    private fun confirmRemoveSongFromGroup() {
        _uiState.value.songPendingRemoval?.let { song ->
            viewModelScope.launch {
                libraryRepository.removeSongFromArtistGroup(groupId, song.songId)
            }
        }
        _uiState.update { it.copy(songPendingRemoval = null) }
    }

    private fun confirmDeleteGroupOnly() {
        _uiState.value.groupPendingDeletion?.let { group ->
            viewModelScope.launch {
                libraryRepository.deleteArtistSongGroup(group.groupId)
                _uiState.update { it.copy(groupPendingDeletion = null) }
                _navigateBack.emit(Unit)
            }
        }
    }

    private fun confirmDeleteGroupAndSongs() {
        _uiState.value.groupPendingDeletion?.let { group ->
            viewModelScope.launch {
                libraryRepository.deleteArtistSongGroupAndSongs(group.groupId)
                _uiState.update { it.copy(groupPendingDeletion = null) }
                _navigateBack.emit(Unit)
            }
        }
    }

    private fun onShuffleSong(song: Song) {
        val currentSongs = _uiState.value.songs
        val index = currentSongs.indexOf(song)
        if (index != -1) {
            viewModelScope.launch {
                musicServiceConnection.shuffleSongList(currentSongs, index)
            }
        }
    }

    private fun onGoToArtist(song: Song) {
        viewModelScope.launch {
            val artist = artistDao.getArtistByName(song.artist)
            artist?.let {
                _navigateToArtist.emit(it.artistId)
            }
        }
    }
}