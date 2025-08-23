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
import com.example.m.managers.DownloadStatusManager
import com.example.m.managers.PlaylistManager
import com.example.m.playback.MusicServiceConnection
import com.example.m.ui.search.SearchResult
import com.example.m.ui.search.SearchResultForList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject

data class SearchedArtistDetailsUiState(
    val isLoading: Boolean = true,
    val channelInfo: ChannelInfo? = null,
    val songs: List<SearchResultForList> = emptyList(),
    val albums: List<PlaylistInfoItem> = emptyList(),
    val errorMessage: String? = null,
    val searchType: String = "video"
)

@HiltViewModel
class SearchedArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val youtubeRepository: YoutubeRepository,
    private val playlistManager: PlaylistManager,
    private val musicServiceConnection: MusicServiceConnection,
    private val songDao: SongDao,
    libraryRepository: LibraryRepository,
    private val downloadStatusManager: DownloadStatusManager,
    val imageLoader: ImageLoader
) : ViewModel() {

    private val channelUrl: String = savedStateHandle["channelUrl"]!!
    private val searchType: String = savedStateHandle["searchType"]!!

    private val _uiState = MutableStateFlow(SearchedArtistDetailsUiState())
    val uiState: StateFlow<SearchedArtistDetailsUiState> = _uiState

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
        loadArtistDetails()
    }

    private fun extractVideoId(url: String?): String? {
        return url?.substringAfter("v=")?.substringBefore('&')
    }

    private fun loadArtistDetails() {
        songUpdateJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, searchType = this@SearchedArtistDetailViewModel.searchType) }
            try {
                val artistDetails = if (searchType == "music") {
                    youtubeRepository.getMusicArtistDetails(channelUrl)
                } else {
                    youtubeRepository.getVideoCreatorDetails(channelUrl)
                }

                if (artistDetails != null) {
                    songUpdateJob = combine(
                        localLibrary,
                        downloadStatusManager.statuses
                    ) { librarySongs, statuses ->
                        val libraryMap = librarySongs.filter { !it.videoId.isNullOrBlank() }.associateBy { it.videoId!! }
                        val songResults = artistDetails.songs.map { streamInfo ->
                            val videoId = extractVideoId(streamInfo.url)
                            val localSong = videoId?.let { libraryMap[it] }
                            val searchResult = SearchResult(
                                streamInfo,
                                localSong?.isInLibrary ?: false,
                                localSong?.localFilePath != null
                            )
                            SearchResultForList(searchResult, statuses[streamInfo.url])
                        }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                channelInfo = artistDetails.channelInfo,
                                songs = songResults,
                                albums = artistDetails.albums
                            )
                        }
                    }.launchIn(viewModelScope)
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Could not load artist details.") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "An unknown error occurred.") }
            }
        }
    }

    fun onSongSelected(selectedIndex: Int) {
        val items = uiState.value.songs.map { it.result.streamInfo }
        if (items.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(items, selectedIndex)
            }
        }
    }

    fun addSongToLibrary(streamInfo: StreamInfoItem) {
        viewModelScope.launch {
            playlistManager.getSongForItem(streamInfo)
        }
    }

    fun downloadSong(item: StreamInfoItem) {
        viewModelScope.launch {
            val song = playlistManager.getSongForItem(item)
            playlistManager.startDownload(song)
        }
    }

    fun selectItemForPlaylist(item: StreamInfoItem) {
        itemToAddToPlaylist = item
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