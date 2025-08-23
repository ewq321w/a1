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
    val query: String = "",
    val songs: List<SearchResult> = emptyList(),
    val albums: List<AlbumResult> = emptyList(),
    val artists: List<ArtistResult> = emptyList(),
    val videoChannels: List<ArtistResult> = emptyList(),
    val videoPlaylists: List<AlbumResult> = emptyList(),
    val videoStreams: List<SearchResult> = emptyList(),
    val detailedViewCategory: SearchCategory? = null,
    val selectedFilter: String = "music_songs"
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
        if (uiState.value.query.isBlank()) return
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            val query = uiState.value.query
            val filter = uiState.value.selectedFilter

            val results = youtubeRepository.search(query, filter)
            updateSearchResults(results)
        }
    }

    private suspend fun updateSearchResults(results: List<InfoItem>) {
        coroutineScope {
            val downloadedSongs = songDao.getAllDownloadedSongsOnce().associateBy { it.youtubeUrl }
            val librarySongs = songDao.getLibrarySongsSortedByArtist().first().associateBy { it.youtubeUrl }

            val songs = results.filterIsInstance<StreamInfoItem>().map {
                val url = it.url ?: ""
                async {
                    SearchResult(
                        streamInfo = it,
                        isInLibrary = librarySongs.containsKey(url),
                        isDownloaded = downloadedSongs.containsKey(url)
                    )
                }
            }.awaitAll()

            val albums = results.filterIsInstance<PlaylistInfoItem>().map { AlbumResult(it) }
            val artists = results.filterIsInstance<ChannelInfoItem>().map { ArtistResult(it) }

            if (uiState.value.selectedFilter == "music_songs") {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        songs = songs,
                        albums = albums,
                        artists = artists
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        videoStreams = songs,
                        videoPlaylists = albums,
                        videoChannels = artists
                    )
                }
            }
        }
    }

    fun onSongSelected(selectedIndex: Int) {
        val items = if (uiState.value.selectedFilter == "music_songs") {
            uiState.value.songs
        } else {
            uiState.value.videoStreams
        }
        if (items.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(items.map { it.streamInfo }, selectedIndex)
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
            // First, ensure the song exists in the database and get its object.
            val song = playlistManager.getSongForItem(result.streamInfo)

            // If the song is not already in the library, add it.
            if (!song.isInLibrary) {
                val updatedSong = song.copy(
                    isInLibrary = true,
                    dateAddedTimestamp = System.currentTimeMillis()
                )
                songDao.updateSong(updatedSong)
                // Linking to an artist is handled by getSongForItem for new songs,
                // but we call it here to be safe for existing songs not in the library.
                libraryRepository.linkSongToArtist(updatedSong)
            }

            // Finally, start the download.
            playlistManager.startDownload(song)
        }
    }
}