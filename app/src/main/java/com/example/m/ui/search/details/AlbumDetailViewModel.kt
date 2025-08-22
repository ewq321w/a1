package com.example.m.ui.search.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.example.m.data.database.Playlist
import com.example.m.data.database.Song
import com.example.m.data.database.SongDao
import com.example.m.data.repository.LibraryRepository
import com.example.m.data.repository.YoutubeRepository
import com.example.m.managers.PlaylistManager
import com.example.m.playback.MusicServiceConnection
import com.example.m.ui.search.SearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject

data class AlbumDetailUiState(
    val isLoading: Boolean = true,
    val albumInfo: PlaylistInfo? = null,
    val songs: List<SearchResult> = emptyList(),
    val errorMessage: String? = null,
    val searchType: String = "music"
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val youtubeRepository: YoutubeRepository,
    private val musicServiceConnection: MusicServiceConnection,
    private val songDao: SongDao,
    private val playlistManager: PlaylistManager,
    libraryRepository: LibraryRepository,
    val imageLoader: ImageLoader
) : ViewModel() {
    private val albumUrl: String = savedStateHandle["albumUrl"]!!
    private val searchType: String = savedStateHandle["searchType"]!!

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    private val localLibrary: StateFlow<List<Song>> = songDao.getAllSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlaylists: StateFlow<List<Playlist>> = libraryRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var itemToAddToPlaylist by mutableStateOf<Any?>(null)
        private set
    var showCreatePlaylistDialog by mutableStateOf(false)
        private set
    private var pendingItem: Any? = null

    private var songUpdateJob: Job? = null

    init {
        loadAlbumDetails()
    }

    private fun extractVideoId(url: String?): String? {
        return url?.substringAfter("v=")?.substringBefore('&')
    }

    private fun loadAlbumDetails() {
        songUpdateJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, searchType = this@AlbumDetailViewModel.searchType) }
            try {
                val details = youtubeRepository.getPlaylistDetails(albumUrl)

                if (details != null) {
                    songUpdateJob = viewModelScope.launch {
                        localLibrary.collect { librarySongs ->
                            val libraryMap = librarySongs.filter { !it.videoId.isNullOrBlank() }.associateBy { it.videoId!! }
                            val songResults = details.relatedItems.filterIsInstance<StreamInfoItem>().map { streamInfo ->
                                val videoId = extractVideoId(streamInfo.url)
                                val localSong = videoId?.let { libraryMap[it] }
                                SearchResult(
                                    streamInfo,
                                    localSong?.isInLibrary ?: false,
                                    localSong?.localFilePath != null
                                )
                            }
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    albumInfo = details,
                                    songs = songResults
                                )
                            }
                        }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Could not load album details.") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "An unknown error occurred.") }
            }
        }
    }

    fun onSongSelected(selectedIndex: Int) {
        val items = uiState.value.songs.map { it.streamInfo }
        if (items.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(items, selectedIndex)
            }
        }
    }

    fun addSongToLibrary(result: SearchResult) {
        viewModelScope.launch {
            playlistManager.getSongForItem(result.streamInfo)
        }
    }

    fun downloadSong(result: SearchResult) {
        viewModelScope.launch {
            val song = playlistManager.getSongForItem(result.streamInfo)
            playlistManager.startDownload(song)
        }
    }

    fun selectItemForPlaylist(item: Any) {
        if (item is SearchResult) {
            itemToAddToPlaylist = item.streamInfo
        } else if (item is Song || item is StreamInfoItem) {
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