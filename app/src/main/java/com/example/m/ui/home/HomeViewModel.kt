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
    val relatedBasedMix: List<StreamInfoItem> = emptyList(),
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
    val refreshTrigger: Long = 0L
)

sealed interface HomeEvent {
    data class PlayRecentMix(val index: Int) : HomeEvent
    data class PlayDiscoveryMix(val index: Int) : HomeEvent
    data class PlayRelatedBasedMix(val index: Int) : HomeEvent
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
        // Load "On Repeat" first
        viewModelScope.launch {
            val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            val songPlays = songDao.getSongPlaysInTimeRange(weekAgo)

            if (songPlays.isNotEmpty()) {
                val currentTime = System.currentTimeMillis()
                val weekDuration = 7 * 24 * 60 * 60 * 1000.0

                val songScores = songPlays.groupBy { it.songId }
                    .mapValues { (_, plays) ->
                        val playCount = plays.size
                        val recencyScore = plays.sumOf { play ->
                            val age = currentTime - play.timestamp
                            val recencyFactor = 1.0 - (age / weekDuration)
                            Math.pow(recencyFactor, 0.5)
                        }
                        val frequencyScore = playCount.toDouble()
                        val mostRecentPlay = plays.maxOf { it.timestamp }
                        val hoursAgo = (currentTime - mostRecentPlay) / (60 * 60 * 1000.0)
                        val recencyBonus = if (hoursAgo < 24) {
                            (24 - hoursAgo) / 24.0
                        } else 0.0
                        val baseScore = (frequencyScore * 0.40) +
                                       (recencyScore * 0.35) +
                                       (recencyBonus * playCount * 0.25)
                        val randomFactor = 0.9 + (Math.random() * 0.2)
                        baseScore * randomFactor
                    }

                val topSongIds = songScores.entries
                    .sortedByDescending { it.value }
                    .take(36)
                    .map { it.key }

                val songs = libraryRepository.getSongsByIds(topSongIds)
                val topSongs = topSongIds.mapNotNull { songId ->
                    songs.find { it.songId == songId }
                }

                _uiState.update { it.copy(topSongsThisWeek = topSongs) }

                val topRecentPlays = listeningHistoryDao.getTopRecentSongs(limit = 10).first()
                if (topRecentPlays.isNotEmpty()) {
                    generateRecommendations(topRecentPlays)
                }
            } else {
                _uiState.update { it.copy(topSongsThisWeek = emptyList()) }
                val topRecentPlays = listeningHistoryDao.getTopRecentSongs(limit = 10).first()
                if (topRecentPlays.isNotEmpty()) {
                    generateRecommendations(topRecentPlays)
                } else {
                    _uiState.update { it.copy(recentMix = emptyList(), discoveryMix = emptyList(), relatedBasedMix = emptyList(), recentMixSongs = emptyList()) }
                }
            }
        }

        viewModelScope.launch {
            listeningHistoryDao.getListeningHistory().collect { historyEntries ->
                val uniqueRecentSongs = historyEntries
                    .distinctBy { it.song.songId }
                    .take(10)
                    .map { it.song }
                _uiState.update { it.copy(recentlyPlayed = uniqueRecentSongs) }
            }
        }

        viewModelScope.launch {
            songDao.getRecentlyAddedSongs(limit = 10).collect { songs ->
                _uiState.update { it.copy(recentlyAdded = songs) }
            }
        }

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
            is HomeEvent.PlayRelatedBasedMix -> playRelatedBasedMix(event.index)
            is HomeEvent.PlayRecentlyPlayed -> playRecentlyPlayed(event.index)
            is HomeEvent.PlayTopSong -> playTopSong(event.index)
            is HomeEvent.PlayRecentlyAdded -> playRecentlyAdded(event.index)
            is HomeEvent.ShuffleAll -> shuffleAll()
            is HomeEvent.NavigateToPlaylist -> {}
            is HomeEvent.Refresh -> refreshHomeData()
        }
    }

    private suspend fun generateRecommendations(topRecentPlays: List<RecentPlay>) {
        Timber.tag(TAG).d("Generating recommendations...")
        val recentSongIds = topRecentPlays.map { it.songId }
        val recentSongs = libraryRepository.getSongsByIds(recentSongIds)
        val orderedRecentSongs = recentSongIds.mapNotNull { id -> recentSongs.find { it.songId == id } }

        _uiState.update { it.copy(recentMixSongs = orderedRecentSongs, recentMix = orderedRecentSongs.map { song -> song.toStreamInfoItem() }) }

        val onRepeatSongs = _uiState.value.topSongsThisWeek
        val seedSongs = if (onRepeatSongs.isNotEmpty()) {
            onRepeatSongs
        } else {
            orderedRecentSongs
        }

        if (seedSongs.isNotEmpty()) {
            Timber.tag(TAG).d("Discovery Mix: Finding unique artists from On Repeat")

            // Get unique artists from On Repeat and their top song
            val artistTopSongs = mutableMapOf<String, Song>()
            for (song in seedSongs) {
                val artist = song.artist.lowercase()
                if (!artistTopSongs.containsKey(artist)) {
                    artistTopSongs[artist] = song
                }
            }

            Timber.tag(TAG).d("Found ${artistTopSongs.size} unique artists in On Repeat")

            val youtubeMixItems = mutableListOf<StreamInfoItem>()
            val relatedBasedItems = mutableListOf<StreamInfoItem>()
            val downloadedVideoIds = seedSongs.map { it.videoId }.toSet()

            // Strategy 1: Get YouTube Mix for top song from each artist
            Timber.tag(TAG).d("Getting YouTube Mix from each artist's top song")
            for ((artist, song) in artistTopSongs) {
                try {
                    if (song.videoId != null && song.videoId.length == 11) {
                        val youtubeMix = youtubeRepository.getYoutubeMixPlaylist(song.videoId)
                        if (youtubeMix != null) {
                            val mixSongs = youtubeMix.playlistInfo.relatedItems.filterIsInstance<StreamInfoItem>()
                            Timber.tag(TAG).d("Found ${mixSongs.size} songs in YouTube Mix for artist: $artist (${song.title})")

                            // Take top 15 songs from each artist's mix
                            val filtered = mixSongs
                                .take(15)
                                .filter { it.url != null && !downloadedVideoIds.contains(it.url?.substringAfter("v=")) }

                            youtubeMixItems.addAll(filtered)
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to get YouTube Mix for: ${song.title}")
                }
            }

            // Strategy 2: Get Related Songs for top song from each artist
            Timber.tag(TAG).d("Getting Related Songs from each artist's top song")
            for ((artist, song) in artistTopSongs) {
                try {
                    if (song.videoId != null && song.videoId.length == 11) {
                        val videoUrl = "https://www.youtube.com/watch?v=${song.videoId}"
                        val relatedSongs = youtubeRepository.getRelatedStreams(videoUrl)
                        if (relatedSongs != null && relatedSongs.isNotEmpty()) {
                            Timber.tag(TAG).d("Found ${relatedSongs.size} related songs for artist: $artist (${song.title})")

                            // Take top 15 songs from each artist's related songs
                            val filtered = relatedSongs
                                .take(15)
                                .filter { it.url != null && !downloadedVideoIds.contains(it.url?.substringAfter("v=")) }

                            relatedBasedItems.addAll(filtered)
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to get related songs for: ${song.title}")
                }
            }

            // Filter, remove duplicates, shuffle, and limit for YouTube Mix
            val discoveryMix = youtubeMixItems
                .distinctBy { it.url }
                .filter { item ->
                    isLikelyMusicSong(item) && !songNameContainsArtistName(item)
                }
                .shuffled()
                .take(60)

            // Filter, remove duplicates, shuffle, and limit for Related-Based Mix
            val relatedBasedMix = relatedBasedItems
                .distinctBy { it.url }
                .filter { item ->
                    isLikelyMusicSong(item) && !songNameContainsArtistName(item)
                }
                .shuffled()
                .take(60)

            _uiState.update {
                it.copy(
                    discoveryMix = discoveryMix,
                    relatedBasedMix = relatedBasedMix
                )
            }

            Timber.tag(TAG).d(
                "Discovery Mixes generated: YouTube Mix: ${discoveryMix.size} songs, Related-Based Mix: ${relatedBasedMix.size} songs from ${artistTopSongs.size} artists"
            )
        } else {
            _uiState.update { it.copy(discoveryMix = emptyList(), relatedBasedMix = emptyList()) }
        }

        Timber.tag(TAG)
            .d("Recommendations generated. RecentMix: ${uiState.value.recentMix.size}, DiscoveryMix: ${uiState.value.discoveryMix.size}, RelatedBasedMix: ${uiState.value.relatedBasedMix.size}")
    }

    private fun songNameContainsArtistName(item: StreamInfoItem): Boolean {
        val title = item.name?.lowercase() ?: return false
        val artistName = item.uploaderName?.lowercase() ?: return false

        if (artistName.isBlank()) return false

        // Check if title contains the artist name
        return title.contains(artistName)
    }

    private fun isLikelyMusicSong(item: StreamInfoItem): Boolean {
        val title = item.name?.lowercase() ?: return false

        val excludeKeywords = listOf(
            "lyrics", "lyric video", "official lyric",
            "live", "live performance", "live at",
            "concert", "tour",
            "cover", "acoustic version", "piano version",
            "remix", "extended", "instrumental",
            "karaoke", "sing along",
            "reaction", "react",
            "interview", "behind the scenes",
            "making of", "official video", "music video",
            "visualizer", "official visualizer",
            "trailer", "teaser",
            "full album", "full ep"
        )

        val containsExcludeKeyword = excludeKeywords.any { keyword ->
            title.contains(keyword)
        }

        val isSuspiciouslyLong = item.duration > 600

        return !containsExcludeKeyword && !isSuspiciouslyLong
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

    private fun playRelatedBasedMix(selectedIndex: Int) {
        val items = _uiState.value.relatedBasedMix
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
            refreshInRotation()
            val topRecentPlays = listeningHistoryDao.getTopRecentSongs(limit = 10).first()
            if (topRecentPlays.isNotEmpty()) {
                generateRecommendations(topRecentPlays)
            }
            _uiState.update { it.copy(isRefreshing = false, refreshTrigger = System.currentTimeMillis()) }
        }
    }

    private suspend fun refreshInRotation() {
        val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        val songPlays = songDao.getSongPlaysInTimeRange(weekAgo)

        if (songPlays.isNotEmpty()) {
            val currentTime = System.currentTimeMillis()
            val weekDuration = 7 * 24 * 60 * 60 * 1000.0

            val songScores = songPlays.groupBy { it.songId }
                .mapValues { (_, plays) ->
                    val playCount = plays.size
                    val recencyScore = plays.sumOf { play ->
                        val age = currentTime - play.timestamp
                        val recencyFactor = 1.0 - (age / weekDuration)
                        Math.pow(recencyFactor, 0.5)
                    }
                    val frequencyScore = playCount.toDouble()
                    val mostRecentPlay = plays.maxOf { it.timestamp }
                    val hoursAgo = (currentTime - mostRecentPlay) / (60 * 60 * 1000.0)
                    val recencyBonus = if (hoursAgo < 24) {
                        (24 - hoursAgo) / 24.0
                    } else 0.0
                    val baseScore = (frequencyScore * 0.40) +
                                   (recencyScore * 0.35) +
                                   (recencyBonus * playCount * 0.25)
                    val randomFactor = 0.9 + (Math.random() * 0.2)
                    baseScore * randomFactor
                }

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

