package com.example.m.ui.library.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.PreferencesManager
import com.example.m.data.database.*
import com.example.m.data.repository.LibraryRepository
import com.example.m.managers.PlaylistManager
import com.example.m.playback.MusicServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject

enum class ArtistSortOrder {
    CUSTOM, TITLE, DATE_ADDED, PLAY_COUNT
}

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val musicServiceConnection: MusicServiceConnection,
    private val songDao: SongDao,
    private val artistDao: ArtistDao,
    private val listeningHistoryDao: ListeningHistoryDao,
    private val playlistManager: PlaylistManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    private val artistId: Long = checkNotNull(savedStateHandle["artistId"])

    private val _sortOrder = MutableStateFlow(preferencesManager.artistSortOrder)
    val sortOrder: StateFlow<ArtistSortOrder> = _sortOrder

    val itemPendingDeletion = mutableStateOf<Any?>(null)

    val artistWithSongs: StateFlow<ArtistWithSongs?> = artistDao.getArtistWithSongs(artistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val songs: StateFlow<List<Song>> = combine(
        artistWithSongs,
        sortOrder,
        listeningHistoryDao.getAllPlayCounts()
    ) { artistWithSongs, order, playCounts ->
        val songList = artistWithSongs?.songs ?: emptyList()
        val playCountMap = playCounts.associateBy({ it.songId }, { it.playCount })

        when (order) {
            ArtistSortOrder.CUSTOM -> songList
            ArtistSortOrder.TITLE -> songList.sortedBy { it.title }
            ArtistSortOrder.DATE_ADDED -> songList.sortedBy { it.dateAddedTimestamp }
            ArtistSortOrder.PLAY_COUNT -> songList.sortedByDescending { playCountMap[it.songId] ?: 0 }
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
        val currentSongs = songs.value
        if (currentSongs.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(currentSongs, selectedIndex)
            }
        }
    }

    fun downloadSong(song: Song) {
        viewModelScope.launch {
            val librarySong = playlistManager.getSongForItem(song)
            playlistManager.startDownload(librarySong)
        }
    }

    fun playArtist() {
        val currentSongs = songs.value
        if (currentSongs.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(currentSongs, 0)
            }
        }
    }

    fun shuffleArtist() {
        val currentSongs = songs.value
        if (currentSongs.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(currentSongs.shuffled(), 0)
            }
        }
    }

    fun toggleAutoDownload() {
        val artist = artistWithSongs.value?.artist ?: return
        val isEnabling = !artist.downloadAutomatically
        viewModelScope.launch(Dispatchers.IO) {
            artistDao.updateArtist(artist.copy(downloadAutomatically = isEnabling))
            if (isEnabling) {
                val songsToDownload = artistWithSongs.value?.songs ?: return@launch
                songsToDownload.forEach { song ->
                    playlistManager.startDownload(song)
                }
            }
        }
    }

    fun setSortOrder(order: ArtistSortOrder) {
        _sortOrder.value = order
        preferencesManager.artistSortOrder = order
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            libraryRepository.deleteSongFromDeviceAndDb(song)
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

    fun onPlaySongNext(song: Song) {
        musicServiceConnection.playNext(song)
    }

    fun onAddSongToQueue(song: Song) {
        musicServiceConnection.addToQueue(song)
    }

    fun onShuffleSong(song: Song) {
        val currentSongs = songs.value
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