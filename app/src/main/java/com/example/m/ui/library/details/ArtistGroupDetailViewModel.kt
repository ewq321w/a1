// file: com/example/m/ui/library/details/ArtistGroupDetailViewModel.kt
package com.example.m.ui.library.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.database.*
import com.example.m.data.repository.LibraryRepository
import com.example.m.managers.PlaylistManager
import com.example.m.managers.ThumbnailProcessor
import com.example.m.playback.MusicServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistGroupDetailUiState(
    val groupWithArtists: ArtistGroupWithArtists? = null,
    val artistsInGroup: List<ArtistWithSongs> = emptyList(),
    val allPlaylists: List<Playlist> = emptyList(),
    val itemToAddToPlaylist: Any? = null,
    val showCreatePlaylistDialog: Boolean = false,
    val pendingItemForPlaylist: Any? = null
)

sealed interface ArtistGroupDetailEvent {
    data class PlayArtist(val artist: Artist) : ArtistGroupDetailEvent
    data class ShuffleArtist(val artist: Artist) : ArtistGroupDetailEvent
    data class ShuffleUngroupedSongs(val artist: Artist) : ArtistGroupDetailEvent
    data class RemoveArtistFromGroup(val artist: Artist) : ArtistGroupDetailEvent
    data class HideArtist(val artist: Artist) : ArtistGroupDetailEvent
    data class ToggleAutoDownload(val artist: Artist) : ArtistGroupDetailEvent
    data class SelectItemForPlaylist(val item: Any) : ArtistGroupDetailEvent
    object DismissAddToPlaylistSheet : ArtistGroupDetailEvent
    data class PlaylistSelectedForAddition(val playlistId: Long) : ArtistGroupDetailEvent
    object PrepareToCreatePlaylist : ArtistGroupDetailEvent
    data class CreatePlaylist(val name: String) : ArtistGroupDetailEvent
    object DismissCreatePlaylistDialog : ArtistGroupDetailEvent
}

@HiltViewModel
class ArtistGroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val artistGroupDao: ArtistGroupDao,
    private val artistDao: ArtistDao,
    private val musicServiceConnection: MusicServiceConnection,
    val playlistManager: PlaylistManager,
    private val libraryRepository: LibraryRepository,
    val thumbnailProcessor: ThumbnailProcessor,
    private val songDao: SongDao
) : ViewModel() {
    private val groupId: Long = checkNotNull(savedStateHandle["groupId"])

    private val _uiState = MutableStateFlow(ArtistGroupDetailUiState())
    val uiState: StateFlow<ArtistGroupDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            artistGroupDao.getGroupWithArtists(groupId).collect { group ->
                _uiState.update { it.copy(groupWithArtists = group) }
            }
        }

        viewModelScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            val artistsInGroupFlow = _uiState.map { it.groupWithArtists }
                .distinctUntilChanged()
                .flatMapLatest { group ->
                    val artists = group?.artists ?: emptyList()
                    if (artists.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        val artistFlows = artists.map { artistDao.getArtistWithLibrarySongs(it.artistId) }
                        combine(artistFlows) { results -> results.filterNotNull() }
                    }
                }
            artistsInGroupFlow.collect { artists ->
                _uiState.update { it.copy(artistsInGroup = artists) }
            }
        }

        viewModelScope.launch {
            libraryRepository.getAllPlaylists().collect { playlists ->
                _uiState.update { it.copy(allPlaylists = playlists) }
            }
        }
    }

    fun onEvent(event: ArtistGroupDetailEvent) {
        when (event) {
            is ArtistGroupDetailEvent.PlayArtist -> playArtist(event.artist)
            is ArtistGroupDetailEvent.ShuffleArtist -> shuffleArtist(event.artist)
            is ArtistGroupDetailEvent.ShuffleUngroupedSongs -> shuffleUngroupedSongsForArtist(event.artist)
            is ArtistGroupDetailEvent.RemoveArtistFromGroup -> removeArtistFromGroup(event.artist)
            is ArtistGroupDetailEvent.HideArtist -> hideArtist(event.artist)
            is ArtistGroupDetailEvent.ToggleAutoDownload -> toggleAutoDownloadForArtist(event.artist)
            is ArtistGroupDetailEvent.SelectItemForPlaylist -> _uiState.update { it.copy(itemToAddToPlaylist = event.item) }
            is ArtistGroupDetailEvent.DismissAddToPlaylistSheet -> _uiState.update { it.copy(itemToAddToPlaylist = null) }
            is ArtistGroupDetailEvent.PlaylistSelectedForAddition -> onPlaylistSelectedForAddition(event.playlistId)
            is ArtistGroupDetailEvent.PrepareToCreatePlaylist -> _uiState.update { it.copy(showCreatePlaylistDialog = true, pendingItemForPlaylist = it.itemToAddToPlaylist, itemToAddToPlaylist = null) }
            is ArtistGroupDetailEvent.CreatePlaylist -> createPlaylistAndAddPendingItem(event.name)
            is ArtistGroupDetailEvent.DismissCreatePlaylistDialog -> _uiState.update { it.copy(showCreatePlaylistDialog = false, pendingItemForPlaylist = null) }
        }
    }

    private fun playArtist(artist: Artist) {
        viewModelScope.launch {
            val songs = artistDao.getSongsForArtistSortedByCustom(artist.artistId)
            if (songs.isNotEmpty()) musicServiceConnection.playSongList(songs, 0)
        }
    }

    private fun shuffleArtist(artist: Artist) {
        viewModelScope.launch {
            val songs = artistDao.getSongsForArtistSortedByCustom(artist.artistId).shuffled()
            if (songs.isNotEmpty()) musicServiceConnection.playSongList(songs, 0)
        }
    }

    private fun shuffleUngroupedSongsForArtist(artist: Artist) {
        viewModelScope.launch {
            val groupsMap = artistDao.getAllArtistSongGroupsWithSongsOrdered(artist.artistId).first()
            val groupedSongIds = groupsMap.values.flatten().map { it.songId }.toSet()
            val artistWithSongs = artistDao.getArtistWithLibrarySongs(artist.artistId).first()
            val allArtistSongs = artistWithSongs?.songs ?: emptyList()
            val ungroupedSongs = allArtistSongs.filter { it.songId !in groupedSongIds }
            if (ungroupedSongs.isNotEmpty()) musicServiceConnection.playSongList(ungroupedSongs.shuffled(), 0)
        }
    }

    private fun removeArtistFromGroup(artist: Artist) {
        viewModelScope.launch(Dispatchers.IO) {
            artistDao.removeArtistFromGroup(artist.artistId)
        }
    }

    private fun hideArtist(artist: Artist) {
        viewModelScope.launch(Dispatchers.IO) {
            artistDao.hideArtist(artist.artistId)
        }
    }

    private fun toggleAutoDownloadForArtist(artist: Artist) {
        viewModelScope.launch(Dispatchers.IO) {
            val isEnabling = !artist.downloadAutomatically
            artistDao.updateArtist(artist.copy(downloadAutomatically = isEnabling))
            if (isEnabling) {
                artistDao.getSongsForArtistSortedByCustom(artist.artistId).forEach { song ->
                    libraryRepository.startDownload(song)
                }
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
        viewModelScope.launch(Dispatchers.IO) {
            val artistName = _uiState.value.groupWithArtists?.artists?.firstOrNull()?.name
            if (artistName != null) {
                val activeGroupId = songDao.getArtistLibrarySong(artistName)?.libraryGroupId
                if (activeGroupId != null) {
                    playlistManager.createPlaylistAndAddItem(name, item, activeGroupId)
                }
            }
        }
        _uiState.update { it.copy(showCreatePlaylistDialog = false, pendingItemForPlaylist = null) }
    }
}