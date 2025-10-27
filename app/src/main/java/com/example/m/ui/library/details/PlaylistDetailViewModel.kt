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
import com.example.m.managers.PlaylistActionState
import com.example.m.managers.PlaylistActionsManager
import com.example.m.managers.PlaylistManager
import com.example.m.managers.SnackbarManager
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
    val sortOrder: PlaylistSortOrder = PlaylistSortOrder.CUSTOM,
    val showConfirmRemoveDownloadsOnDisableDialog: Boolean = false,
    val nowPlayingMediaId: String? = null
)

sealed interface PlaylistDetailEvent {
    data class SongSelected(val index: Int) : PlaylistDetailEvent
    data class RemoveSong(val songId: Long) : PlaylistDetailEvent
    data class SetSortOrder(val order: PlaylistSortOrder) : PlaylistDetailEvent
    object ShufflePlaylist : PlaylistDetailEvent
    object DeletePlaylist : PlaylistDetailEvent
    object PrepareToToggleAutoDownload : PlaylistDetailEvent
    object DismissDisableAutoDownloadDialog : PlaylistDetailEvent
    data class DisableAutoDownload(val removeFiles: Boolean) : PlaylistDetailEvent
    data class DownloadSong(val song: Song) : PlaylistDetailEvent
    data class DeleteDownload(val song: Song) : PlaylistDetailEvent
    data class AddToPlaylist(val item: Any) : PlaylistDetailEvent
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
    private val playlistActionsManager: PlaylistActionsManager,
    private val preferencesManager: PreferencesManager,
    private val snackbarManager: SnackbarManager
) : ViewModel() {
    private val playlistId: Long = checkNotNull(savedStateHandle["playlistId"])

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    val playlistActionState: StateFlow<PlaylistActionState> = playlistActionsManager.state

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
            musicServiceConnection.currentMediaId.collect { mediaId ->
                _uiState.update { it.copy(nowPlayingMediaId = mediaId) }
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
            is PlaylistDetailEvent.PrepareToToggleAutoDownload -> prepareToToggleAutoDownload()
            is PlaylistDetailEvent.DismissDisableAutoDownloadDialog -> _uiState.update { it.copy(showConfirmRemoveDownloadsOnDisableDialog = false) }
            is PlaylistDetailEvent.DisableAutoDownload -> disableAutoDownload(event.removeFiles)
            is PlaylistDetailEvent.DownloadSong -> viewModelScope.launch { libraryRepository.startDownload(event.song) }
            is PlaylistDetailEvent.DeleteDownload -> deleteSongDownload(event.song)
            is PlaylistDetailEvent.AddToPlaylist -> playlistActionsManager.selectItem(event.item)
            is PlaylistDetailEvent.PlayNext -> musicServiceConnection.playNext(event.song)
            is PlaylistDetailEvent.AddToQueue -> musicServiceConnection.addToQueue(event.song)
            is PlaylistDetailEvent.ShuffleSong -> onShuffleSong(event.song)
            is PlaylistDetailEvent.GoToArtist -> onGoToArtist(event.song)
        }
    }

    fun onPlaylistCreateConfirm(name: String) = playlistActionsManager.onCreatePlaylist(name)
    fun onPlaylistSelected(playlistId: Long) = playlistActionsManager.onPlaylistSelected(playlistId)
    fun onPlaylistActionDismiss() = playlistActionsManager.dismiss()
    fun onPrepareToCreatePlaylist() = playlistActionsManager.prepareToCreatePlaylist()
    fun onGroupSelectedForNewPlaylist(groupId: Long) = playlistActionsManager.onGroupSelectedForNewPlaylist(groupId)

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
                snackbarManager.showMessage(message)
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

    private fun prepareToToggleAutoDownload() {
        val playlist = _uiState.value.playlist ?: return
        if (playlist.downloadAutomatically) {
            _uiState.update { it.copy(showConfirmRemoveDownloadsOnDisableDialog = true) }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                playlistDao.updatePlaylist(playlist.copy(downloadAutomatically = true))
                _uiState.value.songs.forEach { song -> libraryRepository.startDownload(song) }
            }
        }
    }

    private fun disableAutoDownload(removeFiles: Boolean) {
        val playlist = _uiState.value.playlist ?: return
        val playlistWithSongs = PlaylistWithSongs(playlist, _uiState.value.songs)
        viewModelScope.launch(Dispatchers.IO) {
            playlistDao.updatePlaylist(playlist.copy(downloadAutomatically = false))
            if (removeFiles) {
                libraryRepository.removeDownloadsForPlaylist(playlistWithSongs)
            }
        }
        _uiState.update { it.copy(showConfirmRemoveDownloadsOnDisableDialog = false) }
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