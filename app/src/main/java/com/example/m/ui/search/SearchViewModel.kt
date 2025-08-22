package com.example.m.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject

enum class SearchCategory {
    SONGS, ALBUMS, ARTISTS, VIDEOS, CHANNELS
}

data class SearchResult(
    val streamInfo: StreamInfoItem,
    val isInLibrary: Boolean = false,
    val isDownloaded: Boolean = false
)

data class AlbumResult(
    val albumInfo: PlaylistInfoItem,
    val score: Int
)

data class ArtistResult(
    val artistInfo: ChannelInfoItem,
    val score: Int
)

data class SearchUiState(
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedFilter: String = "music_songs",
    val detailedViewCategory: SearchCategory? = null,
    val topResult: InfoItem? = null,
    val songs: List<SearchResult> = emptyList(),
    val albums: List<AlbumResult> = emptyList(),
    val artists: List<ArtistResult> = emptyList(),
    val videos: List<SearchResult> = emptyList(),
    val videoChannels: List<ArtistResult> = emptyList() // To hold channels for the "Videos" search
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val youtubeRepository: YoutubeRepository,
    private val musicServiceConnection: MusicServiceConnection,
    private val songDao: SongDao,
    libraryRepository: LibraryRepository,
    private val playlistManager: PlaylistManager,
    val imageLoader: ImageLoader
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    private val localLibrary: StateFlow<List<Song>> = songDao.getAllSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlaylists: StateFlow<List<Playlist>> = libraryRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var itemToAddToPlaylist by mutableStateOf<Any?>(null)
        private set
    var showCreatePlaylistDialog by mutableStateOf(false)
        private set
    private var pendingItem: Any? = null

    init {
        viewModelScope.launch {
            localLibrary.collect { librarySongs ->
                val libraryMap = librarySongs.filter { !it.videoId.isNullOrBlank() }.associateBy { it.videoId!! }
                val currentSongs = _uiState.value.songs
                val currentVideos = _uiState.value.videos

                if (currentSongs.isNotEmpty() || currentVideos.isNotEmpty()) {
                    val updatedSongs = currentSongs.map { searchResult ->
                        val videoId = extractVideoId(searchResult.streamInfo.url)
                        val localSong = videoId?.let { libraryMap[it] }
                        searchResult.copy(
                            isInLibrary = localSong?.isInLibrary ?: false,
                            isDownloaded = localSong?.localFilePath != null
                        )
                    }
                    val updatedVideos = currentVideos.map { searchResult ->
                        val videoId = extractVideoId(searchResult.streamInfo.url)
                        val localSong = videoId?.let { libraryMap[it] }
                        searchResult.copy(
                            isInLibrary = localSong?.isInLibrary ?: false,
                            isDownloaded = localSong?.localFilePath != null
                        )
                    }
                    _uiState.update { it.copy(songs = updatedSongs, videos = updatedVideos) }
                }
            }
        }
    }


    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    private fun identifyArtists(channels: List<ChannelInfoItem>): List<ArtistResult> {
        // Only include channels that are official "Topic" channels.
        return channels
            .filter { (it.name ?: "").endsWith(" - Topic", ignoreCase = true) }
            .map { ArtistResult(it, 0) } // Score is not needed for this simple filter.
    }

    private fun identifyNormalChannels(channels: List<ChannelInfoItem>): List<ArtistResult> {
        // Exclude channels that are "Topic" channels and sort by subscribers.
        return channels
            .filter { !(it.name ?: "").endsWith(" - Topic", ignoreCase = true) }
            .sortedByDescending { it.subscriberCount }
            .map { ArtistResult(it, 0) }
    }

    private fun identifyAlbums(playlists: List<PlaylistInfoItem>, artists: List<ArtistResult>): List<AlbumResult> {
        val artistNames = artists.map { it.artistInfo.name?.replace(" - Topic", "")?.trim() }

        return playlists.map { playlist ->
            var score = 0
            val uploader = playlist.uploaderName ?: ""
            val playlistName = playlist.name ?: ""

            if (artistNames.any { uploader.contains(it!!, ignoreCase = true) }) score += 10
            if (uploader.endsWith(" - Topic", ignoreCase = true)) score += 8
            if (uploader.endsWith("VEVO", ignoreCase = true)) score += 5

            if (listOf("mix", "favorites", "liked", "playlist").any { playlistName.contains(it, ignoreCase = true) }) {
                score -= 5
            }
            if ((playlist.streamCount ?: 0) in 5..30) {
                score += 1
            }

            AlbumResult(playlist, score)
        }
            .filter { it.score > 5 }
            .sortedByDescending { it.score }
    }

    private fun extractVideoId(url: String?): String? {
        return url?.substringAfter("v=")?.substringBefore('&')
    }

    fun executeSearch() {
        if (_uiState.value.searchQuery.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, detailedViewCategory = null)
            try {
                val localSongVideoIdMap = localLibrary.value.filter { !it.videoId.isNullOrBlank() }.associateBy { it.videoId }

                if (_uiState.value.selectedFilter == "music_songs") {
                    val musicResults = youtubeRepository.searchMusic(_uiState.value.searchQuery)

                    val sortedSongs = musicResults.songs
                        .map { song ->
                            val score = if (song.uploaderName?.endsWith(" - Topic", ignoreCase = true) == true) {
                                10
                            } else {
                                5
                            }
                            Pair(song, score)
                        }
                        .sortedByDescending { it.second }
                        .map { it.first }

                    val songSearchResults = sortedSongs.map { streamInfo ->
                        val videoId = extractVideoId(streamInfo.url)
                        val localSong = videoId?.let { localSongVideoIdMap[it] }
                        SearchResult(streamInfo, localSong?.isInLibrary ?: false, localSong?.localFilePath != null)
                    }

                    val topicArtists = identifyArtists(musicResults.artists)
                    val albumResults = identifyAlbums(musicResults.albums, topicArtists)

                    _uiState.value = _uiState.value.copy(
                        songs = songSearchResults,
                        albums = albumResults,
                        artists = topicArtists,
                        topResult = songSearchResults.firstOrNull()?.streamInfo,
                        videos = emptyList(),
                        videoChannels = emptyList()
                    )
                } else {
                    val (videoResults, channelResults) = coroutineScope {
                        val videoJob = async { youtubeRepository.search(_uiState.value.searchQuery, "all") }
                        val channelJob = async { youtubeRepository.search(_uiState.value.searchQuery, "channels") }
                        Pair(videoJob.await(), channelJob.await())
                    }

                    val videoSearchResults = videoResults
                        .filterIsInstance<StreamInfoItem>()
                        .filter { !(it.uploaderName ?: "").endsWith(" - Topic", ignoreCase = true) }
                        .map { streamInfo ->
                            val videoId = extractVideoId(streamInfo.url)
                            val localSong = videoId?.let { localSongVideoIdMap[it] }
                            SearchResult(streamInfo, localSong?.isInLibrary ?: false, localSong?.localFilePath != null)
                        }

                    val channelSearchResults = identifyNormalChannels(
                        channelResults.filterIsInstance<ChannelInfoItem>()
                    )

                    _uiState.value = _uiState.value.copy(
                        videos = videoSearchResults,
                        videoChannels = channelSearchResults,
                        songs = emptyList(), albums = emptyList(), artists = emptyList(), topResult = null
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to load search results")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun onSongSelected(selectedIndex: Int, items: List<SearchResult>) {
        items.getOrNull(selectedIndex)?.let { result ->
            viewModelScope.launch {
                musicServiceConnection.playSingleSong(result.streamInfo)
            }
        }
    }

    fun onFilterChanged(filter: String) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter, songs = emptyList(), videos = emptyList(), albums = emptyList(), artists = emptyList(), topResult = null, detailedViewCategory = null)
        if (_uiState.value.searchQuery.isNotBlank()) {
            executeSearch()
        }
    }

    fun showMore(category: SearchCategory) {
        _uiState.value = _uiState.value.copy(detailedViewCategory = category)
    }

    fun closeDetailedView() {
        _uiState.value = _uiState.value.copy(detailedViewCategory = null)
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