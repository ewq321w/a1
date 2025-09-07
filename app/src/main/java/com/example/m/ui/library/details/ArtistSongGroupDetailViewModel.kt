// file: com/example/m/ui/library/details/ArtistSongGroupDetailViewModel.kt
package com.example.m.ui.library.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.database.*
import com.example.m.data.repository.LibraryRepository
import com.example.m.managers.PlaylistManager
import com.example.m.playback.MusicServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistSongGroupDetailUiState(
    val groupWithSongs: ArtistSongGroupWithSongs? = null,
    val songs: List<Song> = emptyList(),
    val allPlaylists: List<Playlist> = emptyList(),
    val itemToAddToPlaylist: Any? = null,
    val showCreatePlaylistDialog: Boolean = false,
    val pendingItemForPlaylist: Any? = null,
    val songPendingRemoval: Song? = null
)

sealed interface ArtistSongGroupDetailEvent {
    data class SongSelected(val index: Int) : ArtistSongGroupDetailEvent
    object ShuffleGroup : ArtistSongGroupDetailEvent
    data class PrepareToRemoveSong(val song: Song) : ArtistSongGroupDetailEvent
    object ConfirmRemoveSong : ArtistSongGroupDetailEvent
    object CancelRemoveSong : ArtistSongGroupDetailEvent
    data class PlayNext(val song: Song) : ArtistSongGroupDetailEvent
    data class AddToQueue(val song: Song) : ArtistSongGroupDetailEvent
    data class ShuffleSong(val song: Song) : ArtistSongGroupDetailEvent
    data class GoToArtist(val song: Song) : ArtistSongGroupDetailEvent
    data class SelectItemForPlaylist(val item: Any) : ArtistSongGroupDetailEvent
    object DismissAddToPlaylistSheet : ArtistSongGroupDetailEvent
    data class PlaylistSelectedForAddition(val playlistId: Long) : ArtistSongGroupDetailEvent
    object PrepareToCreatePlaylist : ArtistSongGroupDetailEvent
    data class CreatePlaylist(val name: String) : ArtistSongGroupDetailEvent
    object DismissCreatePlaylistDialog : ArtistSongGroupDetailEvent
}

@HiltViewModel
class ArtistSongGroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicServiceConnection: MusicServiceConnection,
    private val artistDao: ArtistDao,
    private val libraryRepository: LibraryRepository,
    private val playlistManager: PlaylistManager
) : ViewModel() {
    private val groupId: Long = checkNotNull(savedStateHandle["groupId"])

    private val _uiState = MutableStateFlow(ArtistSongGroupDetailUiState())
    val uiState: StateFlow<ArtistSongGroupDetailUiState> = _uiState.asStateFlow()

    private val _navigateToArtist = MutableSharedFlow<Long>()
    val navigateToArtist: SharedFlow<Long> = _navigateToArtist.asSharedFlow()

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
            libraryRepository.getAllPlaylists().collect { playlists ->
                _uiState.update { it.copy(allPlaylists = playlists) }
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
            is ArtistSongGroupDetailEvent.SelectItemForPlaylist -> _uiState.update { it.copy(itemToAddToPlaylist = event.item) }
            is ArtistSongGroupDetailEvent.DismissAddToPlaylistSheet -> _uiState.update { it.copy(itemToAddToPlaylist = null) }
            is ArtistSongGroupDetailEvent.PlaylistSelectedForAddition -> onPlaylistSelectedForAddition(event.playlistId)
            is ArtistSongGroupDetailEvent.PrepareToCreatePlaylist -> _uiState.update { it.copy(showCreatePlaylistDialog = true, pendingItemForPlaylist = it.itemToAddToPlaylist, itemToAddToPlaylist = null) }
            is ArtistSongGroupDetailEvent.CreatePlaylist -> createPlaylistAndAddPendingItem(event.name)
            is ArtistSongGroupDetailEvent.DismissCreatePlaylistDialog -> _uiState.update { it.copy(showCreatePlaylistDialog = false, pendingItemForPlaylist = null) }
        }
    }

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

    private fun onPlaylistSelectedForAddition(playlistId: Long) {
        _uiState.value.itemToAddToPlaylist?.let { item ->
            playlistManager.addItemToPlaylist(playlistId, item)
        }
        _uiState.update { it.copy(itemToAddToPlaylist = null) }
    }

    private fun createPlaylistAndAddPendingItem(name: String) {
        val item = _uiState.value.pendingItemForPlaylist ?: return
        val activeGroupId = _uiState.value.groupWithSongs?.songs?.firstOrNull()?.libraryGroupId
        if (activeGroupId != null) {
            playlistManager.createPlaylistAndAddItem(name, item, activeGroupId)
        }
        _uiState.update { it.copy(showCreatePlaylistDialog = false, pendingItemForPlaylist = null) }
    }
}