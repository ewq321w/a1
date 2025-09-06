// file: com/example/m/ui/library/details/PlaylistDetailViewModel.kt
package com.example.m.ui.library.details

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.PreferencesManager
import com.example.m.data.database.*
import com.example.m.data.repository.AutoDownloadConflict
import com.example.m.data.repository.LibraryRepository
import com.example.m.managers.DownloadStatusManager
import com.example.m.managers.PlaylistManager
import com.example.m.playback.MusicServiceConnection
import com.example.m.ui.library.SongForList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import javax.inject.Inject


enum class PlaylistSortOrder {
    CUSTOM, TITLE, ARTIST, DATE_ADDED, PLAY_COUNT
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
    private val preferencesManager: PreferencesManager,
    private val downloadStatusManager: DownloadStatusManager
) : ViewModel() {
    private val playlistId: Long = checkNotNull(savedStateHandle["playlistId"])

    private val _sortOrder = MutableStateFlow(preferencesManager.playlistSortOrder)
    val sortOrder: StateFlow<PlaylistSortOrder> = _sortOrder

    private val basePlaylistWithSongs: StateFlow<PlaylistWithSongs?> =
        playlistDao.getPlaylistWithSongsById(playlistId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val playlist: StateFlow<Playlist?> = basePlaylistWithSongs.map { it?.playlist }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val songs: StateFlow<List<SongForList>> = combine(
        basePlaylistWithSongs,
        _sortOrder,
        downloadStatusManager.statuses
    ) { playlistWithSongs, order, statuses ->
        val songList = playlistWithSongs?.songs ?: emptyList()
        val sortedList = when (order) {
            PlaylistSortOrder.CUSTOM -> songList
            PlaylistSortOrder.TITLE -> songList.sortedBy { it.title }
            PlaylistSortOrder.ARTIST -> songList.sortedBy { it.artist }
            PlaylistSortOrder.DATE_ADDED -> songList.sortedBy { it.dateAddedTimestamp }
            PlaylistSortOrder.PLAY_COUNT -> songList.sortedByDescending { it.playCount }
        }
        sortedList.map { song ->
            SongForList(song, statuses[song.youtubeUrl])
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val activeLibraryGroupId = snapshotFlow { preferencesManager.activeLibraryGroupId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), preferencesManager.activeLibraryGroupId)

    @OptIn(ExperimentalCoroutinesApi::class)
    val allPlaylists: StateFlow<List<Playlist>> = activeLibraryGroupId.flatMapLatest { groupId ->
        if (groupId == 0L) libraryRepository.getAllPlaylists() else libraryRepository.getPlaylistsByGroupId(groupId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    var itemToAddToPlaylist by mutableStateOf<Any?>(null)
        private set
    var showCreatePlaylistDialog by mutableStateOf(false)
        private set
    private var pendingItem: Any? = null

    private val _navigateToArtist = MutableSharedFlow<Long>()
    val navigateToArtist: SharedFlow<Long> = _navigateToArtist.asSharedFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    fun onSongSelected(selectedIndex: Int) {
        val currentSongs = songs.value.map { it.song }
        if (currentSongs.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(currentSongs, selectedIndex)
            }
        }
    }

    fun downloadSong(song: Song) {
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

    fun setSortOrder(order: PlaylistSortOrder) {
        _sortOrder.value = order
        preferencesManager.playlistSortOrder = order
    }

    fun shufflePlaylist() {
        val currentSongs = songs.value.map { it.song }
        if (currentSongs.isNotEmpty()) {
            val finalShuffledList = currentSongs.shuffled()
            viewModelScope.launch {
                musicServiceConnection.playSongList(finalShuffledList, 0)
            }
        }
    }

    fun removeSongFromPlaylist(songId: Long) {
        viewModelScope.launch {
            libraryRepository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun deletePlaylist() {
        viewModelScope.launch {
            libraryRepository.deletePlaylist(playlistId)
        }
    }

    fun onAutoDownloadToggled(isToggledOn: Boolean) {
        val currentPlaylist = playlist.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val updatedPlaylist = currentPlaylist.copy(downloadAutomatically = isToggledOn)
            playlistDao.updatePlaylist(updatedPlaylist)
            if (isToggledOn) {
                downloadAllSongs()
            }
        }
    }

    fun removeDownloadsForPlaylist() {
        val currentPlaylist = playlist.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (currentPlaylist.downloadAutomatically) {
                playlistDao.updatePlaylist(currentPlaylist.copy(downloadAutomatically = false))
            }
            songs.value.forEach { songForList ->
                val song = songForList.song
                if (song.localFilePath != null) {
                    try {
                        val uri = song.localFilePath!!.toUri()
                        if (uri.scheme == "content") {
                            context.contentResolver.delete(uri, null, null)
                        } else {
                            File(song.localFilePath!!).delete()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    songDao.updateSong(song.copy(localFilePath = null))
                }
            }
        }
    }

    fun downloadAllSongs() {
        val songsToDownload = songs.value
            .map { it.song }
            .filter {
                it.localFilePath == null || !File(it.localFilePath!!).exists()
            }

        songsToDownload.forEach { song ->
            playlistManager.startDownload(song)
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
        val item = pendingItem ?: return
        val groupId = playlist.value?.libraryGroupId
        if (groupId != null) {
            playlistManager.createPlaylistAndAddItem(name, item, groupId)
        }
        dismissCreatePlaylistDialog()
    }

    fun dismissCreatePlaylistDialog() {
        showCreatePlaylistDialog = false
        pendingItem = null
    }

    fun onPlaySongNext(song: Song) {
        musicServiceConnection.playNext(song)
    }

    fun onAddSongToQueue(song: Song) {
        musicServiceConnection.addToQueue(song)
    }

    fun onShuffleSong(song: Song) {
        val currentSongs = songs.value.map { it.song }
        val index = currentSongs.indexOf(song)
        if (index != -1) {
            viewModelScope.launch {
                musicServiceConnection.shuffleSongList(currentSongs, index)
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
}