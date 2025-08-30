package com.example.m.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.database.*
import com.example.m.data.repository.LibraryRepository
import com.example.m.managers.DownloadStatus
import com.example.m.managers.DownloadStatusManager
import com.example.m.managers.PlaylistManager
import com.example.m.playback.MusicServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    private val downloadStatusManager: DownloadStatusManager
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


    val allPlaylists: StateFlow<List<Playlist>> = libraryRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var itemToAddToPlaylist by mutableStateOf<Any?>(null)
        private set
    var showCreatePlaylistDialog by mutableStateOf(false)
        private set
    private var pendingItem: Any? = null

    private val _navigateToArtist = MutableSharedFlow<Long>()
    val navigateToArtist: SharedFlow<Long> = _navigateToArtist.asSharedFlow()

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
        viewModelScope.launch {
            if (!song.isInLibrary) {
                val updatedSong = song.copy(
                    isInLibrary = true,
                    dateAddedTimestamp = System.currentTimeMillis()
                )
                songDao.updateSong(updatedSong)
                libraryRepository.linkSongToArtist(updatedSong)
            }
        }
    }

    fun download(song: Song) {
        playlistManager.startDownload(song)
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
            val (downloaded, remote) = currentSongs.partition { it.localFilePath != null }
            val finalShuffledList = downloaded.shuffled() + remote.shuffled()
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
            itemToAddToPlaylist = item
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
        pendingItem = itemToAddToPlaylist
        dismissAddToPlaylistSheet()
        showCreatePlaylistDialog = true
    }

    fun createPlaylistAndAddPendingItem(name: String) {
        pendingItem?.let { item ->
            playlistManager.createPlaylistAndAddItem(name, item)
            pendingItem = null
        }
        dismissCreatePlaylistDialog()
    }

    fun dismissCreatePlaylistDialog() {
        showCreatePlaylistDialog = false
    }
}