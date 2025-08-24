package com.example.m.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.example.m.data.database.Playlist
import com.example.m.data.database.SongDao
import com.example.m.data.repository.LibraryRepository
import com.example.m.data.repository.YoutubeRepository
import com.example.m.managers.DownloadStatus
import com.example.m.managers.DownloadStatusManager
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
    val artists: List<ArtistResult> = emptyList(),
    val videoChannels: List<ArtistResult> = emptyList(),
    val videoPlaylists: List<AlbumResult> = emptyList(),
    val videoStreams: List<SearchResult> = emptyList(),
    val detailedViewCategory: SearchCategory? = null,
    val selectedFilter: String = "music_songs",

    // Separate next page handlers for each category to support pagination in detailed view
    val songsNextPage: Page? = null,
    val albumsNextPage: Page? = null,
    val artistsNextPage: Page? = null,

    val videoStreamsNextPage: Page? = null,
    val videoPlaylistsNextPage: Page? = null,
    val videoChannelsNextPage: Page? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    val imageLoader: ImageLoader,
    private val youtubeRepository: YoutubeRepository,
    private val musicServiceConnection: MusicServiceConnection,
    private val playlistManager: PlaylistManager,
    private val libraryRepository: LibraryRepository,
    private val songDao: SongDao,
    private val downloadStatusManager: DownloadStatusManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val playlistActionHandler = PlaylistActionHandler(playlistManager)

    val allPlaylists: StateFlow<List<Playlist>> = libraryRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun resultsWithStatus(
        results: List<SearchResult>,
        statuses: Map<String, DownloadStatus>
    ): List<SearchResultForList> {
        return results.map { result ->
            SearchResultForList(result, statuses[result.streamInfo.url])
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
        _uiState.update { it.copy(isLoading = true, songs = emptyList(), albums = emptyList(), artists = emptyList(), videoStreams = emptyList(), videoPlaylists = emptyList(), videoChannels = emptyList()) }

        viewModelScope.launch {
            if (uiState.value.selectedFilter == "music_songs") {
                // Perform three separate searches concurrently for a richer "music" tab
                val songsDeferred = async(Dispatchers.IO) { youtubeRepository.search(query, "music_songs") }
                val albumsDeferred = async(Dispatchers.IO) { youtubeRepository.search(query, "playlists") }
                val artistsDeferred = async(Dispatchers.IO) { youtubeRepository.search(query, "channels") }

                val songsResult = songsDeferred.await()
                val albumsResult = albumsDeferred.await()
                val artistsResult = artistsDeferred.await()

                updateMusicSearchResults(
                    songItems = songsResult?.items ?: emptyList(),
                    albumItems = albumsResult?.items ?: emptyList(),
                    artistItems = artistsResult?.items ?: emptyList()
                )

                _uiState.update {
                    it.copy(
                        songsNextPage = songsResult?.nextPage,
                        albumsNextPage = albumsResult?.nextPage,
                        artistsNextPage = artistsResult?.nextPage,
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
                        videoPlaylistsNextPage = playlistsResult?.nextPage,
                        videoChannelsNextPage = channelsResult?.nextPage,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun loadMoreResults() {
        val currentState = uiState.value
        if (currentState.isLoadingMore || currentState.detailedViewCategory == null) return

        val pageToLoad = when (currentState.detailedViewCategory) {
            SearchCategory.SONGS -> currentState.songsNextPage
            SearchCategory.ALBUMS -> if (currentState.selectedFilter == "music_songs") currentState.albumsNextPage else currentState.videoPlaylistsNextPage
            SearchCategory.ARTISTS -> if (currentState.selectedFilter == "music_songs") currentState.artistsNextPage else currentState.videoChannelsNextPage
            SearchCategory.VIDEOS -> currentState.videoStreamsNextPage
            SearchCategory.CHANNELS -> currentState.videoChannelsNextPage
        }

        if (pageToLoad == null) return
        _uiState.update { it.copy(isLoadingMore = true) }

        viewModelScope.launch {
            val resultPage = youtubeRepository.getMoreSearchResults(pageToLoad)
            if (resultPage != null) {
                appendSearchResults(currentState.detailedViewCategory!!, resultPage.items)
                val newNextPage = resultPage.nextPage
                _uiState.update {
                    when (currentState.detailedViewCategory) {
                        SearchCategory.SONGS -> it.copy(songsNextPage = newNextPage)
                        SearchCategory.ALBUMS -> if (currentState.selectedFilter == "music_songs") it.copy(albumsNextPage = newNextPage) else it.copy(videoPlaylistsNextPage = newNextPage)
                        SearchCategory.ARTISTS -> if (currentState.selectedFilter == "music_songs") it.copy(artistsNextPage = newNextPage) else it.copy(videoChannelsNextPage = newNextPage)
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
            val downloadedSongs = songDao.getAllDownloadedSongsOnce().associateBy { it.youtubeUrl }
            val librarySongs = songDao.getLibrarySongsSortedByArtist().first().associateBy { it.youtubeUrl }

            when (category) {
                SearchCategory.SONGS, SearchCategory.VIDEOS -> {
                    val songs = newItems.filterIsInstance<StreamInfoItem>().map {
                        val url = it.url ?: ""
                        async {
                            SearchResult(
                                streamInfo = it,
                                isInLibrary = librarySongs.containsKey(url),
                                isDownloaded = downloadedSongs.containsKey(url)
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
                    if (uiState.value.selectedFilter == "music_songs") {
                        _uiState.update { it.copy(albums = it.albums + albums) }
                    } else {
                        _uiState.update { it.copy(videoPlaylists = it.videoPlaylists + albums) }
                    }
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

    private suspend fun updateMusicSearchResults(songItems: List<InfoItem>, albumItems: List<InfoItem>, artistItems: List<InfoItem>) {
        coroutineScope {
            val downloadedSongs = songDao.getAllDownloadedSongsOnce().associateBy { it.youtubeUrl }
            val librarySongs = songDao.getLibrarySongsSortedByArtist().first().associateBy { it.youtubeUrl }

            val songs = songItems.filterIsInstance<StreamInfoItem>().map {
                val url = it.url ?: ""
                async {
                    SearchResult(
                        streamInfo = it,
                        isInLibrary = librarySongs.containsKey(url),
                        isDownloaded = downloadedSongs.containsKey(url)
                    )
                }
            }.awaitAll()

            val albums = albumItems.filterIsInstance<PlaylistInfoItem>().map { AlbumResult(it) }
            val artists = artistItems.filterIsInstance<ChannelInfoItem>().map { ArtistResult(it) }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    songs = songs,
                    albums = albums,
                    artists = artists
                )
            }
        }
    }

    private suspend fun updateVideoSearchResults(videoItems: List<InfoItem>, playlistItems: List<InfoItem>, channelItems: List<InfoItem>) {
        coroutineScope {
            val downloadedSongs = songDao.getAllDownloadedSongsOnce().associateBy { it.youtubeUrl }
            val librarySongs = songDao.getLibrarySongsSortedByArtist().first().associateBy { it.youtubeUrl }

            val videos = videoItems.filterIsInstance<StreamInfoItem>().map {
                val url = it.url ?: ""
                async {
                    SearchResult(
                        streamInfo = it,
                        isInLibrary = librarySongs.containsKey(url),
                        isDownloaded = downloadedSongs.containsKey(url)
                    )
                }
            }.awaitAll()

            val playlists = playlistItems.filterIsInstance<PlaylistInfoItem>().map { AlbumResult(it) }
            val channels = channelItems.filterIsInstance<ChannelInfoItem>().map { ArtistResult(it) }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    videoStreams = videos,
                    videoPlaylists = playlists,
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
}
