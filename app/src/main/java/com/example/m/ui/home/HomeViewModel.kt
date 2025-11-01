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
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val refreshTrigger: Long = 0L // Timestamp to trigger pager reset
)

sealed interface HomeEvent {
    data class PlayRecentMix(val index: Int) : HomeEvent
    data class PlayDiscoveryMix(val index: Int) : HomeEvent
    data class PlayRecentlyPlayed(val index: Int) : HomeEvent
    data class PlayTopSong(val index: Int) : HomeEvent
    data class PlayRecentlyAdded(val index: Int) : HomeEvent
    data object ShuffleAll : HomeEvent
    data class NavigateToPlaylist(val playlistId: Long) : HomeEvent
    data object Refresh : HomeEvent
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
        // Load "On Repeat" first - this will be used as seed for Discovery Mix
        viewModelScope.launch {
            val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            val songPlays = songDao.getSongPlaysInTimeRange(weekAgo)

            if (songPlays.isNotEmpty()) {
                val currentTime = System.currentTimeMillis()
                val weekDuration = 7 * 24 * 60 * 60 * 1000.0

                // Group plays by songId and calculate weighted scores
                val songScores = songPlays.groupBy { it.songId }
                    .mapValues { (_, plays) ->
                        val playCount = plays.size

                        // Calculate recency score (more recent plays get higher weight)
                        val recencyScore = plays.sumOf { play ->
                            val age = currentTime - play.timestamp
                            val recencyFactor = 1.0 - (age / weekDuration) // 1.0 for now, 0.0 for week ago
                            // Exponential curve: recent plays matter more
                            Math.pow(recencyFactor, 0.5) // Square root for moderate curve
                        }

                        // Play frequency score (normalized)
                        val frequencyScore = playCount.toDouble()

                        // Most recent play bonus (songs played in last 24h get extra boost)
                        val mostRecentPlay = plays.maxOf { it.timestamp }
                        val hoursAgo = (currentTime - mostRecentPlay) / (60 * 60 * 1000.0)
                        val recencyBonus = if (hoursAgo < 24) {
                            (24 - hoursAgo) / 24.0 // 0 to 1 boost
                        } else 0.0

                        // Weighted combination:
                        // - 40% frequency (consistent listening)
                        // - 35% recency (time-decayed recent plays)
                        // - 25% recency bonus (very recent plays)
                        val baseScore = (frequencyScore * 0.40) +
                                       (recencyScore * 0.35) +
                                       (recencyBonus * playCount * 0.25)

                        // Add slight randomness (±10%) for variety on each load
                        val randomFactor = 0.9 + (Math.random() * 0.2)
                        baseScore * randomFactor
                    }

                // Get top 36 songs by score
                val topSongIds = songScores.entries
                    .sortedByDescending { it.value }
                    .take(36)
                    .map { it.key }

                val songs = libraryRepository.getSongsByIds(topSongIds)
                // Maintain the order from scoring
                val topSongs = topSongIds.mapNotNull { songId ->
                    songs.find { it.songId == songId }
                }

                _uiState.update { it.copy(topSongsThisWeek = topSongs) }

                // After On Repeat is loaded, generate Discovery Mix using those songs
                // Use .first() instead of .collect to get data once, not continuously
                val topRecentPlays = listeningHistoryDao.getTopRecentSongs(limit = 10).first()
                if (topRecentPlays.isNotEmpty()) {
                    generateRecommendations(topRecentPlays)
                }
            } else {
                _uiState.update { it.copy(topSongsThisWeek = emptyList()) }

                // Still try to load recommendations even without On Repeat
                val topRecentPlays = listeningHistoryDao.getTopRecentSongs(limit = 10).first()
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
            is HomeEvent.Refresh -> refreshHomeData()
        }
    }

    private suspend fun generateRecommendations(topRecentPlays: List<RecentPlay>) {
        Timber.tag(TAG).d("Generating recommendations...")
        val recentSongIds = topRecentPlays.map { it.songId }
        val recentSongs = libraryRepository.getSongsByIds(recentSongIds)
        val orderedRecentSongs = recentSongIds.mapNotNull { id -> recentSongs.find { it.songId == id } }

        _uiState.update { it.copy(recentMixSongs = orderedRecentSongs, recentMix = orderedRecentSongs.map { song -> song.toStreamInfoItem() }) }

        // Get "On Repeat" songs to use as seeds for Discovery Mix
        val onRepeatSongs = _uiState.value.topSongsThisWeek
        val seedSongs = if (onRepeatSongs.isNotEmpty()) {
            // Use top 5-8 songs from On Repeat for diverse recommendations
            onRepeatSongs.take(8)
        } else {
            // Fallback to recent songs if On Repeat not loaded yet
            orderedRecentSongs.take(8)
        }

        if (seedSongs.isNotEmpty()) {
            Timber.tag(TAG).d("Discovery Mix using ${seedSongs.size} seed songs from On Repeat")

            val allDiscoveryItems = mutableListOf<StreamInfoItem>()
            val downloadedVideoIds = (orderedRecentSongs + onRepeatSongs).map { it.videoId }.toSet()
            val processedArtists = mutableSetOf<String>()

            // Strategy 1: Get YouTube Mix playlists from seed songs (official music discovery)
            Timber.tag(TAG).d("Phase 1: Getting YouTube Mix playlists from seed songs")
            val mixSeedsLimit = minOf(4, seedSongs.size) // Use 4 seeds for YouTube Mix
            for (song in seedSongs.take(mixSeedsLimit)) {
                try {
                    if (song.videoId != null && song.videoId.length == 11) {
                        val youtubeMix = youtubeRepository.getYoutubeMixPlaylist(song.videoId)
                        if (youtubeMix != null) {
                            val mixSongs = youtubeMix.playlistInfo.relatedItems.filterIsInstance<StreamInfoItem>()
                            Timber.tag(TAG).d("Found ${mixSongs.size} songs in YouTube Mix for: ${song.title}")
                            // Take top 12 songs from each mix
                            val filtered = mixSongs
                                .take(12)
                                .filter { it.url != null && !downloadedVideoIds.contains(it.url?.substringAfter("v=")) }
                            allDiscoveryItems.addAll(filtered)
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to get YouTube Mix for: ${song.title}")
                }
            }

            // Strategy 2: Search for songs from user's artists (using music_songs filter)
            Timber.tag(TAG).d("Phase 2: Searching songs from user's top artists")
            val userArtists = seedSongs.map { it.artist }.distinct().take(3)
            for (artist in userArtists) {
                try {
                    processedArtists.add(artist.lowercase())
                    val discoveryQuery = "$artist songs"
                    Timber.tag(TAG).d("Searching for: $artist")

                    val searchResults = youtubeRepository.search(discoveryQuery, "music_songs")
                    val items = searchResults?.items
                        ?.filterIsInstance<StreamInfoItem>()
                        ?.take(8) // Limit per artist
                        ?.filter { it.url != null && !downloadedVideoIds.contains(it.url?.substringAfter("v=")) }
                        ?: emptyList()

                    allDiscoveryItems.addAll(items)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to get songs for: $artist")
                }
            }

            // Strategy 3: Discover SIMILAR artists from YouTube Mix results
            Timber.tag(TAG).d("Phase 3: Discovering similar artists from YouTube Mix")
            val similarArtistsFound = mutableSetOf<String>()

            // Extract unique artists from the YouTube Mix results we found
            val relatedArtists = allDiscoveryItems
                .mapNotNull { it.uploaderName }
                .filter { it.isNotBlank() }
                .map { it.lowercase() }
                .distinct()
                .filter { !processedArtists.contains(it) } // Don't repeat user's artists
                .take(5) // Take top 5 similar artists

            Timber.tag(TAG).d("Found ${relatedArtists.size} similar artists to explore")

            // Search for popular songs from these similar artists (using music_songs filter)
            for (artist in relatedArtists) {
                try {
                    val searchResults = youtubeRepository.search("$artist popular songs", "music_songs")
                    val items = searchResults?.items
                        ?.filterIsInstance<StreamInfoItem>()
                        ?.filter { stream ->
                            // Make sure it's actually from this artist
                            stream.uploaderName?.lowercase()?.contains(artist) == true &&
                            stream.url != null &&
                            !downloadedVideoIds.contains(stream.url?.substringAfter("v="))
                        }
                        ?.take(5) // Take 5 songs from each similar artist
                        ?: emptyList()

                    if (items.isNotEmpty()) {
                        Timber.tag(TAG).d("Added ${items.size} songs from similar artist: $artist")
                        allDiscoveryItems.addAll(items)
                        similarArtistsFound.add(artist)
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to get songs from similar artist: $artist")
                }
            }

            // Remove duplicates, shuffle for variety, and limit
            val uniqueItems = allDiscoveryItems
                .distinctBy { it.url }
                .shuffled()
                .take(60) // 60 songs for good variety

            _uiState.update { it.copy(discoveryMix = uniqueItems) }
            Timber.tag(TAG).d(
                "Discovery Mix generated: ${uniqueItems.size} unique songs " +
                "(${userArtists.size} user artists + ${similarArtistsFound.size} similar artists + YouTube Mix)"
            )
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

    private fun refreshHomeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }

            // Refresh On Repeat with new randomization
            refreshInRotation()

            // Refresh Discovery Mix
            val topRecentPlays = listeningHistoryDao.getTopRecentSongs(limit = 10).first()
            if (topRecentPlays.isNotEmpty()) {
                generateRecommendations(topRecentPlays)
            }

            // Future: Add more algorithmic refreshes here as you add them
            // refreshTrendingSongs()
            // refreshMoodMix()
            // refreshGenreDiscovery()
            // etc.

            _uiState.update { it.copy(isRefreshing = false, refreshTrigger = System.currentTimeMillis()) }
        }
    }

    private suspend fun refreshInRotation() {
        val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        val songPlays = songDao.getSongPlaysInTimeRange(weekAgo)

        if (songPlays.isNotEmpty()) {
            val currentTime = System.currentTimeMillis()
            val weekDuration = 7 * 24 * 60 * 60 * 1000.0

            // Group plays by songId and calculate weighted scores
            val songScores = songPlays.groupBy { it.songId }
                .mapValues { (_, plays) ->
                    val playCount = plays.size

                    // Calculate recency score (more recent plays get higher weight)
                    val recencyScore = plays.sumOf { play ->
                        val age = currentTime - play.timestamp
                        val recencyFactor = 1.0 - (age / weekDuration)
                        Math.pow(recencyFactor, 0.5)
                    }

                    // Play frequency score (normalized)
                    val frequencyScore = playCount.toDouble()

                    // Most recent play bonus
                    val mostRecentPlay = plays.maxOf { it.timestamp }
                    val hoursAgo = (currentTime - mostRecentPlay) / (60 * 60 * 1000.0)
                    val recencyBonus = if (hoursAgo < 24) {
                        (24 - hoursAgo) / 24.0
                    } else 0.0

                    val baseScore = (frequencyScore * 0.40) +
                                   (recencyScore * 0.35) +
                                   (recencyBonus * playCount * 0.25)

                    // Add randomness for variety
                    val randomFactor = 0.9 + (Math.random() * 0.2)
                    baseScore * randomFactor
                }

            // Get top 36 songs by score
            val topSongIds = songScores.entries
                .sortedByDescending { it.value }
                .take(36)
                .map { it.key }

            val songs = libraryRepository.getSongsByIds(topSongIds)
            val topSongs = topSongIds.mapNotNull { songId ->
                songs.find { it.songId == songId }
            }

            _uiState.update { it.copy(topSongsThisWeek = topSongs) }
        } else {
            _uiState.update { it.copy(topSongsThisWeek = emptyList()) }
        }
    }
}