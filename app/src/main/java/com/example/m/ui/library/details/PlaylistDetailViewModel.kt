// file: com/example/m/ui/library/details/PlaylistDetailViewModel.kt
package com.example.m.ui.library.details

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.PreferencesManager
import com.example.m.data.database.*
import com.example.m.data.repository.AutoDownloadConflict
import com.example.m.data.repository.LibraryRepository
import com.example.m.managers.PlaylistManager
import com.example.m.playback.MusicServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject


enum class PlaylistSortOrder {
    CUSTOM, TITLE, ARTIST, DATE_ADDED, PLAY_COUNT
}

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val songs: List<Song> = emptyList(),
    val allPlaylists: List<Playlist> = emptyList(),
    val sortOrder: PlaylistSortOrder = PlaylistSortOrder.CUSTOM,
    val itemToAddToPlaylist: Any? = null,
    val showCreatePlaylistDialog: Boolean = false,
    val pendingItemForPlaylist: Any? = null
)

sealed interface PlaylistDetailEvent {
    data class SongSelected(val index: Int) : PlaylistDetailEvent
    data class RemoveSong(val songId: Long) : PlaylistDetailEvent
    data class SetSortOrder(val order: PlaylistSortOrder) : PlaylistDetailEvent
    object ShufflePlaylist : PlaylistDetailEvent
    object DeletePlaylist : PlaylistDetailEvent
    data class AutoDownloadToggled(val enable: Boolean) : PlaylistDetailEvent
    object RemoveAllDownloads : PlaylistDetailEvent
    data class DownloadSong(val song: Song) : PlaylistDetailEvent
    data class DeleteDownload(val song: Song) : PlaylistDetailEvent
    data class SelectItemForPlaylist(val item: Any) : PlaylistDetailEvent
    object DismissAddToPlaylistSheet : PlaylistDetailEvent
    data class PlaylistSelectedForAddition(val playlistId: Long) : PlaylistDetailEvent
    object PrepareToCreatePlaylist : PlaylistDetailEvent
    data class CreatePlaylist(val name: String) : PlaylistDetailEvent
    object DismissCreatePlaylistDialog : PlaylistDetailEvent
    data class PlayNext(val song: Song) : PlaylistDetailEvent
    data class AddToQueue(val song: Song) : PlaylistDetailEvent
    data class ShuffleSong(val song: Song) : PlaylistDetailEvent
    data class GoToArtist(val song: Song) : PlaylistDetailEvent
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val musicServiceConnection: MusicServiceConnection,
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao,
    private val artistDao: ArtistDao,
    @ApplicationContext private val context: Context,
    private val playlistManager: PlaylistManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    private val playlistId: Long = checkNotNull(savedStateHandle["playlistId"])

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    private val _navigateToArtist = MutableSharedFlow<Long>()
    val navigateToArtist: SharedFlow<Long> = _navigateToArtist.asSharedFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    init {
        _uiState.update { it.copy(sortOrder = preferencesManager.playlistSortOrder) }

        val basePlaylistWithSongs = playlistDao.getPlaylistWithSongsById(playlistId)

        viewModelScope.launch {
            basePlaylistWithSongs.map { it?.playlist }.collect { playlist ->
                _uiState.update { it.copy(playlist = playlist) }
            }
        }

        viewModelScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            val songsFlow = combine(
                basePlaylistWithSongs,
                _uiState.map { it.sortOrder }.distinctUntilChanged()
            ) { playlistWithSongs, order ->
                val songList = playlistWithSongs?.songs ?: emptyList()
                when (order) {
                    PlaylistSortOrder.CUSTOM -> songList
                    PlaylistSortOrder.TITLE -> songList.sortedBy { it.title }
                    PlaylistSortOrder.ARTIST -> songList.sortedBy { it.artist }
                    PlaylistSortOrder.DATE_ADDED -> songList.sortedBy { it.dateAddedTimestamp }
                    PlaylistSortOrder.PLAY_COUNT -> songList.sortedByDescending { it.playCount }
                }
            }
            songsFlow.collect { songs ->
                _uiState.update { it.copy(songs = songs) }
            }
        }

        viewModelScope.launch {
            val activeLibraryGroupId = flow { emit(preferencesManager.activeLibraryGroupId) }

            @OptIn(ExperimentalCoroutinesApi::class)
            val allPlaylistsFlow = activeLibraryGroupId.flatMapLatest { groupId ->
                if (groupId == 0L) libraryRepository.getAllPlaylists() else libraryRepository.getPlaylistsByGroupId(groupId)
            }
            allPlaylistsFlow.collect { playlists ->
                _uiState.update { it.copy(allPlaylists = playlists) }
            }
        }
    }

    fun onEvent(event: PlaylistDetailEvent) {
        when (event) {
            is PlaylistDetailEvent.SongSelected -> onSongSelected(event.index)
            is PlaylistDetailEvent.RemoveSong -> removeSongFromPlaylist(event.songId)
            is PlaylistDetailEvent.SetSortOrder -> setSortOrder(event.order)
            is PlaylistDetailEvent.ShufflePlaylist -> shufflePlaylist()
            is PlaylistDetailEvent.DeletePlaylist -> deletePlaylist()
            is PlaylistDetailEvent.AutoDownloadToggled -> onAutoDownloadToggled(event.enable)
            is PlaylistDetailEvent.RemoveAllDownloads -> removeDownloadsForPlaylist()
            is PlaylistDetailEvent.DownloadSong -> playlistManager.startDownload(event.song)
            is PlaylistDetailEvent.DeleteDownload -> deleteSongDownload(event.song)
            is PlaylistDetailEvent.SelectItemForPlaylist -> _uiState.update { it.copy(itemToAddToPlaylist = event.item) }
            is PlaylistDetailEvent.DismissAddToPlaylistSheet -> _uiState.update { it.copy(itemToAddToPlaylist = null) }
            is PlaylistDetailEvent.PlaylistSelectedForAddition -> onPlaylistSelectedForAddition(event.playlistId)
            is PlaylistDetailEvent.PrepareToCreatePlaylist -> _uiState.update { it.copy(showCreatePlaylistDialog = true, pendingItemForPlaylist = it.itemToAddToPlaylist, itemToAddToPlaylist = null) }
            is PlaylistDetailEvent.CreatePlaylist -> createPlaylistAndAddPendingItem(event.name)
            is PlaylistDetailEvent.DismissCreatePlaylistDialog -> _uiState.update { it.copy(showCreatePlaylistDialog = false, pendingItemForPlaylist = null) }
            is PlaylistDetailEvent.PlayNext -> musicServiceConnection.playNext(event.song)
            is PlaylistDetailEvent.AddToQueue -> musicServiceConnection.addToQueue(event.song)
            is PlaylistDetailEvent.ShuffleSong -> onShuffleSong(event.song)
            is PlaylistDetailEvent.GoToArtist -> onGoToArtist(event.song)
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

    private fun deleteSongDownload(song: Song) {
        viewModelScope.launch {
            val conflict = libraryRepository.checkForAutoDownloadConflict(song)
            if (conflict != null) {
                val message = when (conflict) {
                    is AutoDownloadConflict.Artist -> "Cannot delete download. Auto-download is enabled for artist '${conflict.name}'."
                    is AutoDownloadConflict.Playlist -> "Cannot delete download. Song is in auto-downloading playlist '${conflict.name}'."
                }
                _userMessage.emit(message)
            } else {
                libraryRepository.deleteDownloadedFileForSong(song)
            }
        }
    }

    private fun setSortOrder(order: PlaylistSortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
        preferencesManager.playlistSortOrder = order
    }

    private fun shufflePlaylist() {
        val currentSongs = _uiState.value.songs
        if (currentSongs.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(currentSongs.shuffled(), 0)
            }
        }
    }

    private fun removeSongFromPlaylist(songId: Long) {
        viewModelScope.launch {
            libraryRepository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    private fun deletePlaylist() {
        viewModelScope.launch {
            libraryRepository.deletePlaylist(playlistId)
        }
    }

    private fun onAutoDownloadToggled(isToggledOn: Boolean) {
        val currentPlaylist = _uiState.value.playlist ?: return
        viewModelScope.launch(Dispatchers.IO) {
            playlistDao.updatePlaylist(currentPlaylist.copy(downloadAutomatically = isToggledOn))
            if (isToggledOn) {
                _uiState.value.songs
                    .filter { it.localFilePath == null || !File(it.localFilePath!!).exists() }
                    .forEach { song -> playlistManager.startDownload(song) }
            }
        }
    }

    private fun removeDownloadsForPlaylist() {
        val currentPlaylist = _uiState.value.playlist ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (currentPlaylist.downloadAutomatically) {
                playlistDao.updatePlaylist(currentPlaylist.copy(downloadAutomatically = false))
            }
            _uiState.value.songs.forEach { song ->
                val artist = artistDao.getArtistByName(song.artist)
                if (artist?.downloadAutomatically == true) return@forEach
                if (song.localFilePath != null) {
                    try {
                        val uri = song.localFilePath!!.toUri()
                        if (uri.scheme == "content") context.contentResolver.delete(uri, null, null)
                        else File(song.localFilePath!!).delete()
                        songDao.updateSong(song.copy(localFilePath = null))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
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
        val groupId = _uiState.value.playlist?.libraryGroupId
        if (groupId != null) {
            playlistManager.createPlaylistAndAddItem(name, item, groupId)
        }
        _uiState.update { it.copy(showCreatePlaylistDialog = false, pendingItemForPlaylist = null) }
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
            artist?.let { _navigateToArtist.emit(it.artistId) }
        }
    }
}