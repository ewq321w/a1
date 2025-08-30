package com.example.m.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.example.m.data.database.Playlist
import com.example.m.data.database.Song
import com.example.m.data.database.SongDao
import com.example.m.data.repository.LibraryRepository
import com.example.m.data.repository.SearchPage
import com.example.m.data.repository.YoutubeRepository
import com.example.m.managers.DownloadStatus
import com.example.m.managers.DownloadStatusManager
import com.example.m.managers.PlaybackListManager
import com.example.m.managers.PlaylistManager
import com.example.m.playback.MusicServiceConnection
import com.example.m.ui.common.PlaylistActionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject

enum class SearchCategory {
    SONGS, ALBUMS, ARTISTS, VIDEOS, CHANNELS, PLAYLISTS
}

data class SearchResult(
    val streamInfo: StreamInfoItem,
    val isInLibrary: Boolean = false,
    val isDownloaded: Boolean = false
)

data class SearchResultForList(
    val result: SearchResult,
    val downloadStatus: DownloadStatus?
)

data class AlbumResult(
    val albumInfo: PlaylistInfoItem
)

data class ArtistResult(
    val artistInfo: ChannelInfoItem
)

data class SearchUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val query: String = "",
    val songs: List<SearchResult> = emptyList(),
    val albums: List<AlbumResult> = emptyList(),
    val playlists: List<AlbumResult> = emptyList(),
    val artists: List<ArtistResult> = emptyList(),
    val videoChannels: List<ArtistResult> = emptyList(),
    val videoStreams: List<SearchResult> = emptyList(),
    val detailedViewCategory: SearchCategory? = null,
    val selectedFilter: String = "music_songs",

    // Separate next page handlers for each category to support pagination in detailed view
    val songsNextPage: Page? = null,
    val albumsNextPage: Page? = null,
    val playlistsNextPage: Page? = null,
    val artistsNextPage: Page? = null,

    val videoStreamsNextPage: Page? = null,
    val videoChannelsNextPage: Page? = null,

    // Handlers for pagination
    val songsHandler: SearchQueryHandler? = null,
    val albumsHandler: SearchQueryHandler? = null,
    val playlistsHandler: SearchQueryHandler? = null,
    val artistsHandler: SearchQueryHandler? = null,
    val videoStreamsHandler: SearchQueryHandler? = null,
    val videoChannelsHandler: SearchQueryHandler? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    val imageLoader: ImageLoader,
    private val youtubeRepository: YoutubeRepository,
    private val musicServiceConnection: MusicServiceConnection,
    private val playlistManager: PlaylistManager,
    private val libraryRepository: LibraryRepository,
    private val songDao: SongDao,
    private val downloadStatusManager: DownloadStatusManager,
    private val playbackListManager: PlaybackListManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val playlistActionHandler = PlaylistActionHandler(playlistManager)

    val allPlaylists: StateFlow<List<Playlist>> = libraryRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allLocalSongs: StateFlow<List<Song>> = songDao.getAllSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun resultsWithStatus(
        results: List<SearchResult>,
        statuses: Map<String, DownloadStatus>
    ): List<SearchResultForList> {
        return results.map { result ->
            val normalizedUrl = result.streamInfo.url?.replace("music.youtube.com", "www.youtube.com")
            SearchResultForList(result, statuses[normalizedUrl])
        }
    }

    val songsWithStatus: StateFlow<List<SearchResultForList>> =
        combine(
            _uiState.map { it.songs },
            downloadStatusManager.statuses,
            ::resultsWithStatus
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videoStreamsWithStatus: StateFlow<List<SearchResultForList>> =
        combine(
            _uiState.map { it.videoStreams },
            downloadStatusManager.statuses,
            ::resultsWithStatus
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            allLocalSongs.drop(1).collect {
                refreshResultStatuses()
            }
        }
    }

    private fun refreshResultStatuses() {
        val currentState = _uiState.value
        if (currentState.songs.isEmpty() && currentState.videoStreams.isEmpty()) return

        val localSongsByUrl = allLocalSongs.value.associateBy { it.youtubeUrl }

        val updatedSongs = currentState.songs.map { searchResult ->
            val normalizedUrl = searchResult.streamInfo.url?.replace("music.youtube.com", "www.youtube.com") ?: ""
            val localSong = localSongsByUrl[normalizedUrl]
            searchResult.copy(
                isInLibrary = localSong?.isInLibrary ?: false,
                isDownloaded = localSong?.localFilePath != null
            )
        }

        val updatedVideoStreams = currentState.videoStreams.map { searchResult ->
            val normalizedUrl = searchResult.streamInfo.url?.replace("music.youtube.com", "www.youtube.com") ?: ""
            val localSong = localSongsByUrl[normalizedUrl]
            searchResult.copy(
                isInLibrary = localSong?.isInLibrary ?: false,
                isDownloaded = localSong?.localFilePath != null
            )
        }

        _uiState.update {
            it.copy(
                songs = updatedSongs,
                videoStreams = updatedVideoStreams
            )
        }
    }


    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
    }

    fun onFilterChange(newFilter: String) {
        _uiState.update { it.copy(selectedFilter = newFilter) }
        search()
    }

    fun search() {
        val query = uiState.value.query
        if (query.isBlank()) return
        _uiState.update { it.copy(isLoading = true, songs = emptyList(), albums = emptyList(), artists = emptyList(), videoStreams = emptyList(), playlists = emptyList(), videoChannels = emptyList()) }

        viewModelScope.launch {
            if (uiState.value.selectedFilter == "music_songs") {
                // Perform concurrent searches for a rich "music" tab
                val songsDeferred = async(Dispatchers.IO) { youtubeRepository.search(query, "music_songs") }
                val albumsDeferred = async(Dispatchers.IO) { youtubeRepository.search(query, "music_albums") }
                val playlistsDeferred = async(Dispatchers.IO) { youtubeRepository.search(query, "playlists") }
                val artistsDeferred = async(Dispatchers.IO) { youtubeRepository.search(query, "channels") }

                val songsResult = songsDeferred.await()
                val albumsResult = albumsDeferred.await()
                val playlistsResult = playlistsDeferred.await()
                val artistsResult = artistsDeferred.await()

                updateMusicSearchResults(
                    songItems = songsResult?.items ?: emptyList(),
                    albumItems = albumsResult?.items ?: emptyList(),
                    playlistItems = playlistsResult?.items ?: emptyList(),
                    artistItems = artistsResult?.items ?: emptyList()
                )

                _uiState.update {
                    it.copy(
                        songsNextPage = songsResult?.nextPage,
                        albumsNextPage = albumsResult?.nextPage,
                        playlistsNextPage = playlistsResult?.nextPage,
                        artistsNextPage = artistsResult?.nextPage,
                        songsHandler = songsResult?.queryHandler,
                        albumsHandler = albumsResult?.queryHandler,
                        playlistsHandler = playlistsResult?.queryHandler,
                        artistsHandler = artistsResult?.queryHandler,
                        isLoading = false
                    )
                }
            } else {
                // Perform three separate searches concurrently for a richer "videos" tab
                val videosDeferred = async(Dispatchers.IO) { youtubeRepository.search(query, "all") }
                val playlistsDeferred = async(Dispatchers.IO) { youtubeRepository.search(query, "playlists") }
                val channelsDeferred = async(Dispatchers.IO) { youtubeRepository.search(query, "channels") }

                val videosResult = videosDeferred.await()
                val playlistsResult = playlistsDeferred.await()
                val channelsResult = channelsDeferred.await()

                updateVideoSearchResults(
                    videoItems = videosResult?.items ?: emptyList(),
                    playlistItems = playlistsResult?.items ?: emptyList(),
                    channelItems = channelsResult?.items ?: emptyList()
                )

                _uiState.update {
                    it.copy(
                        videoStreamsNextPage = videosResult?.nextPage,
                        playlistsNextPage = playlistsResult?.nextPage,
                        videoChannelsNextPage = channelsResult?.nextPage,
                        videoStreamsHandler = videosResult?.queryHandler,
                        playlistsHandler = playlistsResult?.queryHandler,
                        videoChannelsHandler = channelsResult?.queryHandler,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun loadMoreResults() {
        val currentState = uiState.value
        if (currentState.isLoadingMore || currentState.detailedViewCategory == null) return

        val pageAndHandler: Pair<Page?, SearchQueryHandler?> = when (currentState.detailedViewCategory) {
            SearchCategory.SONGS -> currentState.songsNextPage to currentState.songsHandler
            SearchCategory.ALBUMS -> currentState.albumsNextPage to currentState.albumsHandler
            SearchCategory.PLAYLISTS -> currentState.playlistsNextPage to currentState.playlistsHandler
            SearchCategory.ARTISTS -> currentState.artistsNextPage to currentState.artistsHandler
            SearchCategory.VIDEOS -> currentState.videoStreamsNextPage to currentState.videoStreamsHandler
            SearchCategory.CHANNELS -> currentState.videoChannelsNextPage to currentState.videoChannelsHandler
        }

        val pageToLoad = pageAndHandler.first
        val handler = pageAndHandler.second

        if (pageToLoad == null || handler == null) return
        _uiState.update { it.copy(isLoadingMore = true) }

        viewModelScope.launch {
            val resultPage = youtubeRepository.getMoreSearchResults(handler, pageToLoad)
            if (resultPage != null) {
                appendSearchResults(currentState.detailedViewCategory!!, resultPage.items)
                val newNextPage = resultPage.nextPage
                _uiState.update {
                    when (currentState.detailedViewCategory) {
                        SearchCategory.SONGS -> it.copy(songsNextPage = newNextPage)
                        SearchCategory.ALBUMS -> it.copy(albumsNextPage = newNextPage)
                        SearchCategory.PLAYLISTS -> it.copy(playlistsNextPage = newNextPage)
                        SearchCategory.ARTISTS -> it.copy(artistsNextPage = newNextPage)
                        SearchCategory.VIDEOS -> it.copy(videoStreamsNextPage = newNextPage)
                        SearchCategory.CHANNELS -> it.copy(videoChannelsNextPage = newNextPage)
                    }
                }
            }
            _uiState.update { it.copy(isLoadingMore = false) }
        }
    }

    private suspend fun appendSearchResults(category: SearchCategory, newItems: List<InfoItem>) {
        coroutineScope {
            val localSongsByUrl = allLocalSongs.value.associateBy { it.youtubeUrl }

            when (category) {
                SearchCategory.SONGS, SearchCategory.VIDEOS -> {
                    val songs = newItems.filterIsInstance<StreamInfoItem>().map {
                        val rawUrl = it.url ?: ""
                        val normalizedUrl = rawUrl.replace("music.youtube.com", "www.youtube.com")
                        async {
                            val localSong = localSongsByUrl[normalizedUrl]
                            SearchResult(
                                streamInfo = it,
                                isInLibrary = localSong?.isInLibrary ?: false,
                                isDownloaded = localSong?.localFilePath != null
                            )
                        }
                    }.awaitAll()
                    if (category == SearchCategory.SONGS) {
                        _uiState.update { it.copy(songs = it.songs + songs) }
                    } else {
                        _uiState.update { it.copy(videoStreams = it.videoStreams + songs) }
                    }
                }
                SearchCategory.ALBUMS -> {
                    val albums = newItems.filterIsInstance<PlaylistInfoItem>().map { AlbumResult(it) }
                    _uiState.update { it.copy(albums = it.albums + albums) }
                }
                SearchCategory.PLAYLISTS -> {
                    val playlists = newItems.filterIsInstance<PlaylistInfoItem>().map { AlbumResult(it) }
                    _uiState.update { it.copy(playlists = it.playlists + playlists) }
                }
                SearchCategory.ARTISTS, SearchCategory.CHANNELS -> {
                    val artists = newItems.filterIsInstance<ChannelInfoItem>().map { ArtistResult(it) }
                    if (uiState.value.selectedFilter == "music_songs") {
                        _uiState.update { it.copy(artists = it.artists + artists) }
                    } else {
                        _uiState.update { it.copy(videoChannels = it.videoChannels + artists) }
                    }
                }
            }
        }
    }

    private suspend fun updateMusicSearchResults(songItems: List<InfoItem>, albumItems: List<InfoItem>, playlistItems: List<InfoItem>, artistItems: List<InfoItem>) {
        coroutineScope {
            val localSongsByUrl = allLocalSongs.value.associateBy { it.youtubeUrl }

            val songs = songItems.filterIsInstance<StreamInfoItem>()
                .distinctBy { "${it.name?.lowercase()?.trim()}::${it.uploaderName?.lowercase()?.trim()}" }
                .map {
                    val rawUrl = it.url ?: ""
                    val normalizedUrl = rawUrl.replace("music.youtube.com", "www.youtube.com")
                    async {
                        val localSong = localSongsByUrl[normalizedUrl]
                        SearchResult(
                            streamInfo = it,
                            isInLibrary = localSong?.isInLibrary ?: false,
                            isDownloaded = localSong?.localFilePath != null
                        )
                    }
                }.awaitAll()

            val albums = albumItems.filterIsInstance<PlaylistInfoItem>().map { AlbumResult(it) }
            val playlists = playlistItems.filterIsInstance<PlaylistInfoItem>().map { AlbumResult(it) }
            val artists = artistItems.filterIsInstance<ChannelInfoItem>().map { ArtistResult(it) }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    songs = songs,
                    albums = albums,
                    playlists = playlists,
                    artists = artists
                )
            }
        }
    }

    private suspend fun updateVideoSearchResults(videoItems: List<InfoItem>, playlistItems: List<InfoItem>, channelItems: List<InfoItem>) {
        coroutineScope {
            val localSongsByUrl = allLocalSongs.value.associateBy { it.youtubeUrl }

            val videos = videoItems.filterIsInstance<StreamInfoItem>().map {
                val rawUrl = it.url ?: ""
                val normalizedUrl = rawUrl.replace("music.youtube.com", "www.youtube.com")
                async {
                    val localSong = localSongsByUrl[normalizedUrl]
                    SearchResult(
                        streamInfo = it,
                        isInLibrary = localSong?.isInLibrary ?: false,
                        isDownloaded = localSong?.localFilePath != null
                    )
                }
            }.awaitAll()

            val playlists = playlistItems.filterIsInstance<PlaylistInfoItem>().map { AlbumResult(it) }
            val channels = channelItems.filterIsInstance<ChannelInfoItem>().map { ArtistResult(it) }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    videoStreams = videos,
                    playlists = playlists,
                    videoChannels = channels
                )
            }
        }
    }

    fun onSongSelected(selectedIndex: Int) {
        val allItems = if (uiState.value.selectedFilter == "music_songs") {
            uiState.value.songs
        } else {
            uiState.value.videoStreams
        }

        val fullListToPlay = allItems.map { it.streamInfo }

        if (fullListToPlay.isNotEmpty()) {
            viewModelScope.launch {
                val currentState = uiState.value
                val sourceHandler = if (currentState.selectedFilter == "music_songs") {
                    currentState.songsHandler
                } else {
                    currentState.videoStreamsHandler
                }
                val nextPage = if (currentState.selectedFilter == "music_songs") {
                    currentState.songsNextPage
                } else {
                    currentState.videoStreamsNextPage
                }

                playbackListManager.setCurrentListContext(sourceHandler, nextPage)
                musicServiceConnection.playSongList(fullListToPlay, selectedIndex)
            }
        }
    }

    fun showDetailedView(category: SearchCategory) {
        _uiState.update { it.copy(detailedViewCategory = category) }
    }

    fun hideDetailedView() {
        _uiState.update { it.copy(detailedViewCategory = null) }
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

    fun onPlayNext(result: SearchResult) {
        musicServiceConnection.playNext(result.streamInfo)
    }

    fun onAddToQueue(result: SearchResult) {
        musicServiceConnection.addToQueue(result.streamInfo)
    }

    fun onShuffleAlbum(album: AlbumResult) {
        viewModelScope.launch {
            val playlistDetails = youtubeRepository.getPlaylistDetails(album.albumInfo.url!!)
            playlistDetails?.let {
                val songs = it.playlistInfo.relatedItems.filterIsInstance<StreamInfoItem>()
                musicServiceConnection.shuffleSongList(songs, 0)
            }
        }
    }
}