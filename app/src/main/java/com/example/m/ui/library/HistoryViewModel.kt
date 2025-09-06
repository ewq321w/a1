// file: com/example/m/ui/library/HistoryViewModel.kt
package com.example.m.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.PreferencesManager
import com.example.m.data.database.*
import com.example.m.data.repository.AutoDownloadConflict
import com.example.m.data.repository.LibraryRepository
import com.example.m.managers.DownloadStatus
import com.example.m.managers.DownloadStatusManager
import com.example.m.managers.PlaylistManager
import com.example.m.playback.MusicServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject

data class HistoryEntryForList(
    val entry: HistoryEntry,
    val downloadStatus: DownloadStatus?
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val listeningHistoryDao: ListeningHistoryDao,
    private val musicServiceConnection: MusicServiceConnection,
    private val playlistManager: PlaylistManager,
    private val libraryRepository: LibraryRepository,
    private val artistDao: ArtistDao,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val downloadStatusManager: DownloadStatusManager,
    private val preferencesManager: PreferencesManager,
    private val libraryGroupDao: LibraryGroupDao
) : ViewModel() {

    val history: StateFlow<List<HistoryEntryForList>> =
        combine(
            listeningHistoryDao.getListeningHistory(),
            downloadStatusManager.statuses
        ) { historyEntries, statuses ->
            historyEntries.map { entry ->
                HistoryEntryForList(entry, statuses[entry.song.youtubeUrl])
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val activeLibraryGroupId = snapshotFlow { preferencesManager.activeLibraryGroupId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), preferencesManager.activeLibraryGroupId)

    @OptIn(ExperimentalCoroutinesApi::class)
    val allPlaylists: StateFlow<List<Playlist>> = activeLibraryGroupId.flatMapLatest { groupId ->
        if (groupId == 0L) libraryRepository.getAllPlaylists() else libraryRepository.getPlaylistsByGroupId(groupId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    val libraryGroups: StateFlow<List<LibraryGroup>> = libraryGroupDao.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var itemToAddToPlaylist by mutableStateOf<Any?>(null)
        private set
    var showCreatePlaylistDialog by mutableStateOf(false)
        private set
    private var pendingItemForPlaylist by mutableStateOf<Any?>(null)

    var conflictDialogState by mutableStateOf<ConflictDialogState?>(null)
        private set

    var showCreateGroupDialog by mutableStateOf(false)
        private set
    private var pendingAction by mutableStateOf<PendingAction?>(null)
    var showSelectGroupDialog by mutableStateOf(false)
        private set

    private val _navigateToArtist = MutableSharedFlow<Long>()
    val navigateToArtist: SharedFlow<Long> = _navigateToArtist.asSharedFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    fun onSongSelected(selectedIndex: Int) {
        val currentSongs = history.value.map { it.entry.song }
        if (currentSongs.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(currentSongs, selectedIndex)
            }
        }
    }

    fun deleteFromHistory(entry: HistoryEntry) {
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

    fun clearHistory(keep: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (keep == 0) {
                listeningHistoryDao.clearAllHistory()
            } else {
                listeningHistoryDao.clearHistoryExceptLast(keep)
            }
        }
    }

    fun addToLibrary(song: Song) {
        handleAction(PendingAction.AddToLibrary(song))
    }

    private fun addSongToLibraryAndLink(song: Song, groupId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedSong = song.copy(
                isInLibrary = true,
                dateAddedTimestamp = System.currentTimeMillis(),
                libraryGroupId = groupId
            )
            songDao.updateSong(updatedSong)
            libraryRepository.linkSongToArtist(updatedSong)
        }
    }

    fun resolveConflictByMoving() {
        val state = conflictDialogState ?: return
        viewModelScope.launch(Dispatchers.IO) {
            libraryRepository.moveArtistToLibraryGroup(state.song.artist, state.targetGroupId)
            addSongToLibraryAndLink(state.song, state.targetGroupId)
        }
        dismissConflictDialog()
    }

    fun dismissConflictDialog() {
        conflictDialogState = null
        pendingAction = null
    }

    fun download(song: Song) {
        playlistManager.startDownload(song)
    }

    fun deleteSongDownload(song: Song) {
        viewModelScope.launch {
            val conflict = libraryRepository.checkForAutoDownloadConflict(song)
            if (conflict != null) {
                val message = when (conflict) {
                    is AutoDownloadConflict.Artist ->
                        "Cannot delete download. Auto-download is enabled for artist '${conflict.name}'."
                    is AutoDownloadConflict.Playlist ->
                        "Cannot delete download. Song is in auto-downloading playlist '${conflict.name}'."
                }
                _userMessage.emit(message)
            } else {
                libraryRepository.deleteDownloadedFileForSong(song)
            }
        }
    }

    fun onPlaySongNext(song: Song) {
        musicServiceConnection.playNext(song)
    }

    fun onAddToQueue(song: Song) {
        musicServiceConnection.addToQueue(song)
    }

    fun onShuffleSong(song: Song) {
        val currentSongs = history.value.map { it.entry.song }
        if (currentSongs.isNotEmpty()) {
            val finalShuffledList = currentSongs.shuffled()
            viewModelScope.launch {
                musicServiceConnection.playSongList(finalShuffledList, 0)
            }
        }
    }

    fun onGoToArtist(song: Song) {
        viewModelScope.launch {
            val artist = artistDao.getArtistByName(song.artist)
            artist?.let {
                _navigateToArtist.emit(it.artistId)
            }
        }
    }

    fun selectItemForPlaylist(item: Any) {
        if (item is Song || item is StreamInfoItem) {
            handleAction(PendingAction.ShowAddToPlaylistSheet(item))
        }
    }

    fun dismissAddToPlaylistSheet() {
        itemToAddToPlaylist = null
    }

    fun onPlaylistSelectedForAddition(playlistId: Long) {
        itemToAddToPlaylist?.let { item ->
            playlistManager.addItemToPlaylist(playlistId, item)
        }
        dismissAddToPlaylistSheet()
    }

    fun prepareToCreatePlaylist() {
        pendingItemForPlaylist = itemToAddToPlaylist
        dismissAddToPlaylistSheet()
        showCreatePlaylistDialog = true
    }

    fun createPlaylistAndAddPendingItem(name: String) {
        val item = pendingItemForPlaylist ?: return
        val activeGroupId = preferencesManager.activeLibraryGroupId
        if (activeGroupId != 0L) {
            playlistManager.createPlaylistAndAddItem(name, item, activeGroupId)
        }
        dismissCreatePlaylistDialog()
    }

    fun dismissCreatePlaylistDialog() {
        showCreatePlaylistDialog = false
        pendingItemForPlaylist = null
    }

    private fun handleAction(action: PendingAction) {
        viewModelScope.launch(Dispatchers.IO) {
            pendingAction = action
            if (libraryGroups.value.isEmpty()) {
                withContext(Dispatchers.Main) { showCreateGroupDialog = true }
                return@launch
            }

            val activeGroupId = preferencesManager.activeLibraryGroupId
            if (activeGroupId == 0L) {
                withContext(Dispatchers.Main) { showSelectGroupDialog = true }
                return@launch
            }
            proceedWithAction(action, activeGroupId)
        }
    }

    private suspend fun proceedWithAction(action: PendingAction, groupId: Long) {
        when (action) {
            is PendingAction.AddToLibrary -> {
                if (action.song.isInLibrary) return
                val conflict = libraryRepository.checkArtistGroupConflict(action.song.artist, groupId)
                if (conflict != null) {
                    val targetGroup = libraryGroupDao.getGroup(groupId)
                    if (targetGroup != null) {
                        withContext(Dispatchers.Main) {
                            conflictDialogState = ConflictDialogState(action.song, groupId, targetGroup.name, conflict)
                        }
                    }
                } else {
                    addSongToLibraryAndLink(action.song, groupId)
                }
            }
            is PendingAction.ShowAddToPlaylistSheet -> {
                withContext(Dispatchers.Main) {
                    itemToAddToPlaylist = action.item
                }
            }
            else -> { /* Other actions not handled in this screen */ }
        }
    }

    fun createGroupAndProceed(groupName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val action = pendingAction ?: return@launch
            val newGroupId = libraryGroupDao.insertGroup(LibraryGroup(name = groupName.trim()))
            withContext(Dispatchers.Main) {
                preferencesManager.activeLibraryGroupId = newGroupId
            }
            proceedWithAction(action, newGroupId)
            pendingAction = null
        }
        showCreateGroupDialog = false
    }

    fun onGroupSelectedForAddition(groupId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val action = pendingAction ?: return@launch
            withContext(Dispatchers.Main) {
                preferencesManager.activeLibraryGroupId = groupId
            }
            proceedWithAction(action, groupId)
            pendingAction = null
        }
        showSelectGroupDialog = false
    }

    fun dismissCreateGroupDialog() {
        showCreateGroupDialog = false
        pendingAction = null
    }

    fun dismissSelectGroupDialog() {
        showSelectGroupDialog = false
        pendingAction = null
    }

    fun prepareToCreateGroup() {
        showSelectGroupDialog = false
        showCreateGroupDialog = true
    }
}