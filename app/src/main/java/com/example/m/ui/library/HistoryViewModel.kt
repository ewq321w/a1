// file: com/example/m/ui/library/HistoryViewModel.kt
package com.example.m.ui.library

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.PreferencesManager
import com.example.m.data.database.*
import com.example.m.data.repository.AutoDownloadConflict
import com.example.m.data.repository.LibraryRepository
import com.example.m.managers.DialogState
import com.example.m.managers.LibraryActionsManager
import com.example.m.managers.PlaylistManager
import com.example.m.playback.MusicServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val history: List<HistoryEntry> = emptyList(),
    val allPlaylists: List<Playlist> = emptyList(),
    val itemToAddToPlaylist: Any? = null,
    val showCreatePlaylistDialog: Boolean = false,
    val pendingItemForPlaylist: Any? = null
)

sealed interface HistoryEvent {
    data class SongSelected(val index: Int) : HistoryEvent
    data class DeleteFromHistory(val entry: HistoryEntry) : HistoryEvent
    data class ClearHistory(val keep: Int) : HistoryEvent
    data class AddToLibrary(val song: Song) : HistoryEvent
    data class Download(val song: Song) : HistoryEvent
    data class DeleteDownload(val song: Song) : HistoryEvent
    data class PlayNext(val song: Song) : HistoryEvent
    data class AddToQueue(val song: Song) : HistoryEvent
    data class Shuffle(val song: Song) : HistoryEvent
    data class GoToArtist(val song: Song) : HistoryEvent
    data class SelectItemForPlaylist(val item: Any) : HistoryEvent
    object DismissAddToPlaylistSheet : HistoryEvent
    data class PlaylistSelectedForAddition(val playlistId: Long) : HistoryEvent
    object PrepareToCreatePlaylist : HistoryEvent
    object DismissCreatePlaylistDialog : HistoryEvent
    data class CreatePlaylist(val name: String) : HistoryEvent
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val listeningHistoryDao: ListeningHistoryDao,
    private val musicServiceConnection: MusicServiceConnection,
    private val playlistManager: PlaylistManager,
    private val libraryRepository: LibraryRepository,
    private val artistDao: ArtistDao,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val preferencesManager: PreferencesManager,
    private val libraryActionsManager: LibraryActionsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    val dialogState: StateFlow<DialogState> = libraryActionsManager.dialogState

    private val _navigateToArtist = MutableSharedFlow<Long>()
    val navigateToArtist: SharedFlow<Long> = _navigateToArtist.asSharedFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    init {
        viewModelScope.launch {
            listeningHistoryDao.getListeningHistory().collect { history ->
                _uiState.update { it.copy(history = history) }
            }
        }

        viewModelScope.launch {
            val activeLibraryGroupId = snapshotFlow { preferencesManager.activeLibraryGroupId }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), preferencesManager.activeLibraryGroupId)

            @OptIn(ExperimentalCoroutinesApi::class)
            val playlistsFlow = activeLibraryGroupId.flatMapLatest { groupId ->
                if (groupId == 0L) libraryRepository.getAllPlaylists() else libraryRepository.getPlaylistsByGroupId(groupId)
            }

            playlistsFlow.collect { playlists ->
                _uiState.update { it.copy(allPlaylists = playlists) }
            }
        }
    }


    fun onEvent(event: HistoryEvent) {
        when(event) {
            is HistoryEvent.AddToLibrary -> libraryActionsManager.addToLibrary(event.song)
            is HistoryEvent.AddToQueue -> musicServiceConnection.addToQueue(event.song)
            is HistoryEvent.ClearHistory -> clearHistory(event.keep)
            is HistoryEvent.CreatePlaylist -> createPlaylistAndAddPendingItem(event.name)
            is HistoryEvent.DeleteFromHistory -> deleteFromHistory(event.entry)
            is HistoryEvent.DeleteDownload -> deleteSongDownload(event.song)
            is HistoryEvent.Download -> playlistManager.startDownload(event.song)
            is HistoryEvent.GoToArtist -> onGoToArtist(event.song)
            is HistoryEvent.PlayNext -> musicServiceConnection.playNext(event.song)
            is HistoryEvent.SelectItemForPlaylist -> _uiState.update { it.copy(itemToAddToPlaylist = event.item) }
            is HistoryEvent.SongSelected -> onSongSelected(event.index)
            is HistoryEvent.Shuffle -> onShuffleSong(event.song)
            is HistoryEvent.DismissAddToPlaylistSheet -> _uiState.update { it.copy(itemToAddToPlaylist = null) }
            is HistoryEvent.DismissCreatePlaylistDialog -> _uiState.update { it.copy(showCreatePlaylistDialog = false, pendingItemForPlaylist = null) }
            is HistoryEvent.PlaylistSelectedForAddition -> onPlaylistSelectedForAddition(event.playlistId)
            is HistoryEvent.PrepareToCreatePlaylist -> {
                _uiState.update {
                    it.copy(
                        pendingItemForPlaylist = it.itemToAddToPlaylist,
                        itemToAddToPlaylist = null,
                        showCreatePlaylistDialog = true
                    )
                }
            }
        }
    }

    private fun onSongSelected(selectedIndex: Int) {
        val currentSongs = uiState.value.history.map { it.song }
        if (currentSongs.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(currentSongs, selectedIndex)
            }
        }
    }

    private fun deleteFromHistory(entry: HistoryEntry) {
        viewModelScope.launch {
            listeningHistoryDao.deleteHistoryEntry(entry.logId)

            if (!entry.song.isInLibrary) {
                val historyCount = listeningHistoryDao.getHistoryCountForSong(entry.song.songId)
                val playlistCount = playlistDao.getPlaylistCountForSong(entry.song.songId)
                if (historyCount == 0 && playlistCount == 0) {
                    songDao.deleteSong(entry.song)
                }
            }
        }
    }

    private fun clearHistory(keep: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (keep == 0) {
                listeningHistoryDao.clearAllHistory()
            } else {
                listeningHistoryDao.clearHistoryExceptLast(keep)
            }
        }
    }

    fun onDialogCreateGroup(name: String) = libraryActionsManager.onCreateGroup(name)
    fun onDialogGroupSelected(groupId: Long) = libraryActionsManager.onGroupSelected(groupId)
    fun onDialogResolveConflict() = libraryActionsManager.onResolveConflict()
    fun onDialogDismiss() = libraryActionsManager.dismissDialog()


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

    private fun onShuffleSong(song: Song) {
        val currentSongs = uiState.value.history.map { it.song }
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
        uiState.value.itemToAddToPlaylist?.let { item ->
            playlistManager.addItemToPlaylist(playlistId, item)
        }
        _uiState.update { it.copy(itemToAddToPlaylist = null) }
    }

    private fun createPlaylistAndAddPendingItem(name: String) {
        val item = uiState.value.pendingItemForPlaylist ?: return
        val activeGroupId = preferencesManager.activeLibraryGroupId
        if (activeGroupId != 0L) {
            playlistManager.createPlaylistAndAddItem(name, item, activeGroupId)
        }
        _uiState.update { it.copy(showCreatePlaylistDialog = false, pendingItemForPlaylist = null) }
    }
}