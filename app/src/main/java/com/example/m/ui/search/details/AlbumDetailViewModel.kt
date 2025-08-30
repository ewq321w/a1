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
import com.example.m.managers.DownloadStatus
import com.example.m.managers.DownloadStatusManager
import com.example.m.managers.PlaylistManager
import com.example.m.playback.MusicServiceConnection
import com.example.m.ui.search.SearchResult
import com.example.m.ui.search.SearchResultForList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject

data class AlbumDetailUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val albumInfo: PlaylistInfo? = null,
    val songs: List<SearchResultForList> = emptyList(),
    val errorMessage: String? = null,
    val searchType: String = "music",
    val nextPage: Page? = null
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val youtubeRepository: YoutubeRepository,
    private val musicServiceConnection: MusicServiceConnection,
    private val songDao: SongDao,
    private val playlistManager: PlaylistManager,
    private val libraryRepository: LibraryRepository,
    private val downloadStatusManager: DownloadStatusManager,
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

    var showConfirmAddAllDialog by mutableStateOf(false)
        private set

    private var songUpdateJob: Job? = null

    init {
        loadAlbumDetails()
        viewModelScope.launch {
            localLibrary.drop(1).collect {
                refreshSongStatuses()
            }
        }
        viewModelScope.launch {
            downloadStatusManager.statuses.collect { statuses ->
                refreshDownloadStatuses(statuses)
            }
        }
    }

    private fun refreshDownloadStatuses(statuses: Map<String, DownloadStatus>) {
        val currentState = _uiState.value
        if (currentState.songs.isEmpty()) return

        val updatedSongs = currentState.songs.map { searchResultForList ->
            val normalizedUrl = searchResultForList.result.streamInfo.url?.replace("music.youtube.com", "www.youtube.com")
            searchResultForList.copy(downloadStatus = statuses[normalizedUrl])
        }
        _uiState.update { it.copy(songs = updatedSongs) }
    }

    private fun refreshSongStatuses() {
        val currentState = _uiState.value
        if (currentState.songs.isEmpty()) return

        val localSongsByUrl = localLibrary.value.associateBy { it.youtubeUrl }

        val updatedSongs = currentState.songs.map { searchResultForList ->
            val result = searchResultForList.result
            val normalizedUrl = result.streamInfo.url?.replace("music.youtube.com", "www.youtube.com") ?: ""
            val localSong = localSongsByUrl[normalizedUrl]
            searchResultForList.copy(
                result = result.copy(
                    isInLibrary = localSong?.isInLibrary ?: false,
                    isDownloaded = localSong?.localFilePath != null
                )
            )
        }
        _uiState.update { it.copy(songs = updatedSongs) }
    }

    private fun extractVideoId(url: String?): String? {
        return url?.substringAfter("v=")?.substringBefore('&')
    }

    private fun loadAlbumDetails() {
        songUpdateJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, searchType = this@AlbumDetailViewModel.searchType) }
            try {
                val result = youtubeRepository.getPlaylistDetails(albumUrl)

                if (result != null) {
                    val details = result.playlistInfo
                    val initialStatuses = downloadStatusManager.statuses.value
                    val librarySongs = localLibrary.value
                    val libraryMap = librarySongs.filter { !it.videoId.isNullOrBlank() }.associateBy { it.videoId!! }
                    val songResults = details.relatedItems.filterIsInstance<StreamInfoItem>().map { streamInfo ->
                        val videoId = extractVideoId(streamInfo.url)
                        val localSong = videoId?.let { libraryMap[it] }
                        val searchResult = SearchResult(
                            streamInfo,
                            localSong?.isInLibrary ?: false,
                            localSong?.localFilePath != null
                        )
                        val normalizedUrl = streamInfo.url?.replace("music.youtube.com", "www.youtube.com")
                        SearchResultForList(searchResult, initialStatuses[normalizedUrl])
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            albumInfo = details,
                            songs = songResults,
                            nextPage = result.nextPage
                        )
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

    fun loadMoreSongs() {
        val currentState = uiState.value
        if (currentState.isLoadingMore || currentState.nextPage == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val result = youtubeRepository.getMorePlaylistItems(albumUrl, currentState.nextPage)
                if (result != null) {
                    val newItems = result.items
                    val librarySongs = localLibrary.value
                    val statuses = downloadStatusManager.statuses.value
                    val libraryMap = librarySongs.filter { !it.videoId.isNullOrBlank() }.associateBy { it.videoId!! }

                    val newSongResults = newItems.map { streamInfo ->
                        val videoId = extractVideoId(streamInfo.url)
                        val localSong = videoId?.let { libraryMap[it] }
                        val searchResult = SearchResult(
                            streamInfo,
                            localSong?.isInLibrary ?: false,
                            localSong?.localFilePath != null
                        )
                        val normalizedUrl = streamInfo.url?.replace("music.youtube.com", "www.youtube.com")
                        SearchResultForList(searchResult, statuses[normalizedUrl])
                    }

                    _uiState.update {
                        val combinedSongs = buildList {
                            addAll(it.songs)
                            addAll(newSongResults)
                        }
                        it.copy(
                            isLoadingMore = false,
                            songs = combinedSongs,
                            nextPage = result.nextPage
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoadingMore = false, errorMessage = e.message) }
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

    fun shuffle() {
        val songsToPlay = uiState.value.songs
        if (songsToPlay.isNotEmpty()) {
            val (downloaded, remote) = songsToPlay.partition { it.result.isDownloaded }
            val finalShuffledList = (downloaded.shuffled() + remote.shuffled()).map { it.result.streamInfo }
            viewModelScope.launch {
                musicServiceConnection.playSongList(finalShuffledList, 0)
            }
        }
    }

    fun onAddAllToLibraryClicked() {
        showConfirmAddAllDialog = true
    }

    fun confirmAddAllToLibrary() {
        addAllSongsToLibrary()
        showConfirmAddAllDialog = false
    }

    fun dismissConfirmAddAllToLibraryDialog() {
        showConfirmAddAllDialog = false
    }

    private fun addAllSongsToLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            val songsToAdd = uiState.value.songs.map { it.result.streamInfo }
            for (streamInfo in songsToAdd) {
                val song = playlistManager.getSongForItem(streamInfo)
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
    }

    fun addSongToLibrary(result: SearchResult) {
        viewModelScope.launch(Dispatchers.IO) {
            val song = playlistManager.getSongForItem(result.streamInfo)
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

    fun onPlayNext(result: SearchResult) {
        musicServiceConnection.playNext(result.streamInfo)
    }

    fun onAddToQueue(result: SearchResult) {
        musicServiceConnection.addToQueue(result.streamInfo)
    }

    fun downloadSong(result: SearchResult) {
        viewModelScope.launch(Dispatchers.IO) {
            val song = playlistManager.getSongForItem(result.streamInfo)
            if (!song.isInLibrary) {
                val updatedSong = song.copy(
                    isInLibrary = true,
                    dateAddedTimestamp = System.currentTimeMillis()
                )
                songDao.updateSong(updatedSong)
                libraryRepository.linkSongToArtist(updatedSong)
            }
            playlistManager.startDownload(song)
        }
    }

    fun selectItemForPlaylist(item: Any) {
        if (item is SearchResultForList) {
            itemToAddToPlaylist = item.result.streamInfo
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