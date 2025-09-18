package com.example.m.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.database.ListeningHistoryDao
import com.example.m.data.database.RecentPlay
import com.example.m.data.database.Song
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

data class HomeUiState(
    val recentMix: List<StreamInfoItem> = emptyList(),
    val discoveryMix: List<StreamInfoItem> = emptyList(),
    internal val recentMixSongs: List<Song> = emptyList(), // Internal state for playback
    val nowPlayingMediaId: String? = null,
    val isPlaying: Boolean = false
)

sealed interface HomeEvent {
    data class PlayRecentMix(val index: Int) : HomeEvent
    data class PlayDiscoveryMix(val index: Int) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val listeningHistoryDao: ListeningHistoryDao,
    private val libraryRepository: LibraryRepository,
    private val youtubeRepository: YoutubeRepository,
    private val musicServiceConnection: MusicServiceConnection
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            listeningHistoryDao.getTopRecentSongs(limit = 10).collect { topRecentPlays ->
                if (topRecentPlays.isNotEmpty()) {
                    generateRecommendations(topRecentPlays)
                } else {
                    _uiState.update { it.copy(recentMix = emptyList(), discoveryMix = emptyList(), recentMixSongs = emptyList()) }
                }
            }
        }

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
}