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
import com.example.m.managers.PlaybackListManager
import com.example.m.managers.PlaylistManager
import com.example.m.playback.MusicServiceConnection
import com.example.m.ui.search.SearchResult
import com.example.m.ui.search.SearchResultForList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject

data class ArtistSongsUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val channelInfo: ChannelInfo? = null,
    val songs: List<SearchResultForList> = emptyList(),
    val errorMessage: String? = null,
    val searchType: String = "video",
    val nextPage: Page? = null,
    val songsTabHandler: ListLinkHandler? = null, // For tab pagination
    val searchHandler: SearchQueryHandler? = null // For search pagination
)

@HiltViewModel
class ArtistSongsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val youtubeRepository: YoutubeRepository,
    private val musicServiceConnection: MusicServiceConnection,
    private val songDao: SongDao,
    private val playlistManager: PlaylistManager,
    libraryRepository: LibraryRepository,
    private val downloadStatusManager: DownloadStatusManager,
    private val playbackListManager: PlaybackListManager,
    val imageLoader: ImageLoader
) : ViewModel() {

    private val channelUrl: String = savedStateHandle["channelUrl"]!!
    private val searchType: String = savedStateHandle["searchType"]!!
    private var artistName: String? = null

    private val _uiState = MutableStateFlow(ArtistSongsUiState())
    val uiState: StateFlow<ArtistSongsUiState> = _uiState.asStateFlow()

    val allPlaylists: StateFlow<List<Playlist>> = libraryRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var itemToAddToPlaylist by mutableStateOf<Any?>(null)
        private set
    var showCreatePlaylistDialog by mutableStateOf(false)
        private set
    private var pendingItem: Any? = null

    init {
        loadContent()
    }

    override fun onCleared() {
        playbackListManager.clearCurrentListContext()
        super.onCleared()
    }

    private fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, searchType = this@ArtistSongsViewModel.searchType) }
            try {
                if (searchType == "music") {
                    // For music artists, use the search-based method for popular songs
                    val channelInfo = withContext(Dispatchers.IO) {
                        ChannelInfo.getInfo(ServiceList.YouTube, channelUrl)
                    }
                    this@ArtistSongsViewModel.artistName = channelInfo.name

                    val plainArtistName = channelInfo.name.removeSuffix(" - Topic").trim()
                    if (plainArtistName.isBlank()) {
                        throw ExtractionException("Artist name could not be determined.")
                    }

                    val searchPage = youtubeRepository.search(plainArtistName, "music_songs")
                    if (searchPage != null) {
                        val unfilteredSongs = searchPage.items.filterIsInstance<StreamInfoItem>()
                        val filteredSongs = unfilteredSongs.filter {
                            it.uploaderName.equals(plainArtistName, ignoreCase = true)
                        }
                        updateSongsInState(filteredSongs)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                channelInfo = channelInfo,
                                nextPage = searchPage.nextPage,
                                searchHandler = searchPage.queryHandler,
                                songsTabHandler = null // Ensure tab handler is cleared
                            )
                        }
                    } else {
                        throw ExtractionException("Search returned no results for artist: $artistName")
                    }
                } else {
                    // For video channels, fetch content from their videos tab
                    val artistDetails = youtubeRepository.getVideoCreatorDetails(channelUrl)
                    if (artistDetails != null) {
                        updateSongsInState(artistDetails.songs)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                channelInfo = artistDetails.channelInfo,
                                nextPage = artistDetails.songsNextPage,
                                songsTabHandler = artistDetails.songsTabHandler,
                                searchHandler = null // Ensure search handler is cleared
                            )
                        }
                    } else {
                        throw ExtractionException("Could not load channel details.")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "An unknown error occurred.") }
            }
        }
    }

    fun loadMoreSongs() {
        viewModelScope.launch {
            val currentState = uiState.value
            if (currentState.isLoadingMore || currentState.nextPage == null) return@launch

            _uiState.update { it.copy(isLoadingMore = true) }

            // Determine which pagination handler to use based on what was saved in the state
            val resultPage = if (currentState.searchHandler != null) {
                youtubeRepository.getMoreSearchResults(currentState.searchHandler, currentState.nextPage)
            } else if (currentState.songsTabHandler != null) {
                youtubeRepository.getMoreArtistSongs(currentState.songsTabHandler, currentState.nextPage)
            } else {
                null
            }

            if (resultPage != null) {
                val newItems = resultPage.items.filterIsInstance<StreamInfoItem>()
                val plainArtistName = artistName?.removeSuffix(" - Topic")?.trim()

                // Only filter the results if we are in a search context (i.e., searchHandler is not null)
                val filteredNewItems = if (currentState.searchHandler != null && !plainArtistName.isNullOrBlank()) {
                    newItems.filter { it.uploaderName.equals(plainArtistName, ignoreCase = true) }
                } else {
                    newItems
                }
                updateSongsInState(filteredNewItems, append = true)
                _uiState.update { it.copy(nextPage = resultPage.nextPage) }
            }

            _uiState.update { it.copy(isLoadingMore = false) }
        }
    }


    private suspend fun updateSongsInState(songs: List<StreamInfoItem>, append: Boolean = false) {
        val librarySongs = songDao.getLibrarySongsSortedByArtist().first().associateBy { it.youtubeUrl }
        val downloadedSongs = songDao.getAllDownloadedSongsOnce().associateBy { it.youtubeUrl }
        val statuses = downloadStatusManager.statuses.value

        val songResults = songs.map { streamInfo ->
            val rawUrl = streamInfo.url ?: ""
            val normalizedUrl = rawUrl.replace("music.youtube.com", "www.youtube.com")

            val searchResult = SearchResult(
                streamInfo,
                isInLibrary = librarySongs.containsKey(normalizedUrl),
                isDownloaded = downloadedSongs.containsKey(normalizedUrl)
            )
            SearchResultForList(searchResult, statuses[normalizedUrl])
        }

        _uiState.update {
            if (append) {
                it.copy(songs = it.songs + songResults)
            } else {
                it.copy(songs = songResults)
            }
        }
    }

    fun onSongSelected(selectedIndex: Int) {
        val items = uiState.value.songs.map { it.result.streamInfo }
        if (items.isNotEmpty()) {
            viewModelScope.launch {
                val sourceHandler = uiState.value.searchHandler ?: uiState.value.songsTabHandler
                playbackListManager.setCurrentListContext(sourceHandler, uiState.value.nextPage)
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