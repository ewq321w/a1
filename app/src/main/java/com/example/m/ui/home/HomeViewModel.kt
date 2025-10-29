package com.example.m.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.database.ListeningHistoryDao
import com.example.m.data.database.PlaylistDao
import com.example.m.data.database.RecentPlay
import com.example.m.data.database.Song
import com.example.m.data.database.SongDao
import com.example.m.data.database.toStreamInfoItem
import com.example.m.data.repository.LibraryRepository
import com.example.m.data.repository.YoutubeRepository
import com.example.m.playback.MusicServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import timber.log.Timber
import javax.inject.Inject

private const val TAG = "HomeViewModel"

data class QuickAccessPlaylist(
    val playlistId: Long,
    val name: String,
    val songCount: Int
)

data class ListeningStats(
    val totalSongs: Int,
    val totalPlaylists: Int,
    val totalArtists: Int,
    val totalPlayCount: Int
)

data class HomeUiState(
    val recentMix: List<StreamInfoItem> = emptyList(),
    val discoveryMix: List<StreamInfoItem> = emptyList(),
    val recentlyPlayed: List<Song> = emptyList(),
    val topSongsThisWeek: List<Song> = emptyList(),
    val recentlyAdded: List<Song> = emptyList(),
    val quickAccessPlaylists: List<QuickAccessPlaylist> = emptyList(),
    val listeningStats: ListeningStats? = null,
    internal val recentMixSongs: List<Song> = emptyList(),
    val nowPlayingMediaId: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = true
)

sealed interface HomeEvent {
    data class PlayRecentMix(val index: Int) : HomeEvent
    data class PlayDiscoveryMix(val index: Int) : HomeEvent
    data class PlayRecentlyPlayed(val index: Int) : HomeEvent
    data class PlayTopSong(val index: Int) : HomeEvent
    data class PlayRecentlyAdded(val index: Int) : HomeEvent
    data object ShuffleAll : HomeEvent
    data class NavigateToPlaylist(val playlistId: Long) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val listeningHistoryDao: ListeningHistoryDao,
    private val libraryRepository: LibraryRepository,
    private val youtubeRepository: YoutubeRepository,
    private val musicServiceConnection: MusicServiceConnection,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
        observePlaybackState()
    }

    private fun loadHomeData() {
        // Load recommendations based on listening history
        viewModelScope.launch {
            listeningHistoryDao.getTopRecentSongs(limit = 10).collect { topRecentPlays ->
                if (topRecentPlays.isNotEmpty()) {
                    generateRecommendations(topRecentPlays)
                } else {
                    _uiState.update { it.copy(recentMix = emptyList(), discoveryMix = emptyList(), recentMixSongs = emptyList()) }
                }
            }
        }

        // Load recently played songs (unique, last 10)
        viewModelScope.launch {
            listeningHistoryDao.getListeningHistory().collect { historyEntries ->
                val uniqueRecentSongs = historyEntries
                    .distinctBy { it.song.songId }
                    .take(10)
                    .map { it.song }
                _uiState.update { it.copy(recentlyPlayed = uniqueRecentSongs) }
            }
        }

        // Load top songs this week (last 7 days)
        viewModelScope.launch {
            val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            val topSongIds = songDao.getTopSongsInTimeRange(weekAgo, limit = 10)
            val topSongs = libraryRepository.getSongsByIds(topSongIds.map { it.songId })
            _uiState.update { it.copy(topSongsThisWeek = topSongs) }
        }

        // Load recently added songs to library
        viewModelScope.launch {
            songDao.getRecentlyAddedSongs(limit = 10).collect { songs ->
                _uiState.update { it.copy(recentlyAdded = songs) }
            }
        }

        // Load quick access playlists (first 6 by custom order)
        viewModelScope.launch {
            playlistDao.getAllPlaylistsWithDetails().collect { playlistDetails ->
                val quickPlaylists = playlistDetails
                    .sortedBy { it.playlist.playlistId }
                    .take(6)
                    .map { details ->
                        QuickAccessPlaylist(
                            playlistId = details.playlist.playlistId,
                            name = details.playlist.name,
                            songCount = details.songs.size
                        )
                    }
                _uiState.update { it.copy(quickAccessPlaylists = quickPlaylists) }
            }
        }

        // Load listening stats
        viewModelScope.launch {
            combine(
                songDao.getSongsInLibrary(),
                playlistDao.getAllPlaylists(),
                songDao.getTotalPlayCount()
            ) { songs, playlists, totalPlayCount ->
                val uniqueArtists = songs.map { it.artist }.distinct().size
                ListeningStats(
                    totalSongs = songs.size,
                    totalPlaylists = playlists.size,
                    totalArtists = uniqueArtists,
                    totalPlayCount = totalPlayCount
                )
            }.collect { stats ->
                _uiState.update { it.copy(listeningStats = stats, isLoading = false) }
            }
        }
    }

    private fun observePlaybackState() {
        viewModelScope.launch {
            musicServiceConnection.currentMediaId.collect { mediaId ->
                _uiState.update { it.copy(nowPlayingMediaId = mediaId) }
            }
        }
        viewModelScope.launch {
            musicServiceConnection.isPlaying.collect { isPlaying ->
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }
        }
    }

    fun onEvent(event: HomeEvent) {
        when(event) {
            is HomeEvent.PlayRecentMix -> playRecentMix(event.index)
            is HomeEvent.PlayDiscoveryMix -> playDiscoveryMix(event.index)
            is HomeEvent.PlayRecentlyPlayed -> playRecentlyPlayed(event.index)
            is HomeEvent.PlayTopSong -> playTopSong(event.index)
            is HomeEvent.PlayRecentlyAdded -> playRecentlyAdded(event.index)
            is HomeEvent.ShuffleAll -> shuffleAll()
            is HomeEvent.NavigateToPlaylist -> {} // Handle navigation in UI
        }
    }

    private suspend fun generateRecommendations(topRecentPlays: List<RecentPlay>) {
        Timber.tag(TAG).d("Generating recommendations...")
        val recentSongIds = topRecentPlays.map { it.songId }
        val recentSongs = libraryRepository.getSongsByIds(recentSongIds)
        val orderedRecentSongs = recentSongIds.mapNotNull { id -> recentSongs.find { it.songId == id } }

        _uiState.update { it.copy(recentMixSongs = orderedRecentSongs, recentMix = orderedRecentSongs.map { song -> song.toStreamInfoItem() }) }

        val seedSong = orderedRecentSongs.firstOrNull()
        if (seedSong != null) {
            val discoveryQuery = "${seedSong.artist} songs"
            Timber.tag(TAG).d("Discovery Mix seed artist: ${seedSong.artist}")

            val searchResults = youtubeRepository.search(discoveryQuery, "music_songs")
            val downloadedVideoIds = orderedRecentSongs.map { it.videoId }.toSet()

            val discoveryItems = searchResults?.items
                ?.filterIsInstance<StreamInfoItem>()
                ?.filter { it.url != null && !downloadedVideoIds.contains(it.url.substringAfter("v=")) }
                ?: emptyList()
            _uiState.update { it.copy(discoveryMix = discoveryItems) }
        } else {
            _uiState.update { it.copy(discoveryMix = emptyList()) }
        }
        Timber.tag(TAG)
            .d("Recommendations generated. RecentMix: ${uiState.value.recentMix.size}, DiscoveryMix: ${uiState.value.discoveryMix.size}")
    }

    private fun playRecentMix(selectedIndex: Int) {
        val songs = _uiState.value.recentMixSongs
        if (songs.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(songs, selectedIndex)
            }
        }
    }

    private fun playDiscoveryMix(selectedIndex: Int) {
        val items = _uiState.value.discoveryMix
        if (items.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(items, selectedIndex)
            }
        }
    }

    private fun playRecentlyPlayed(selectedIndex: Int) {
        val songs = _uiState.value.recentlyPlayed
        if (songs.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(songs, selectedIndex)
            }
        }
    }

    private fun playTopSong(selectedIndex: Int) {
        val songs = _uiState.value.topSongsThisWeek
        if (songs.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(songs, selectedIndex)
            }
        }
    }

    private fun playRecentlyAdded(selectedIndex: Int) {
        val songs = _uiState.value.recentlyAdded
        if (songs.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(songs, selectedIndex)
            }
        }
    }

    private fun shuffleAll() {
        viewModelScope.launch {
            val allLibrarySongs = songDao.getSongsInLibrary().first()
            if (allLibrarySongs.isNotEmpty()) {
                val shuffled = allLibrarySongs.shuffled()
                musicServiceConnection.playSongList(shuffled, 0)
            }
        }
    }
}