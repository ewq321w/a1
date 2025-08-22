package com.example.m.ui.home

import android.util.Log
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
import javax.inject.Inject

private const val TAG = "HomeViewModel"

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val listeningHistoryDao: ListeningHistoryDao,
    private val libraryRepository: LibraryRepository,
    private val youtubeRepository: YoutubeRepository,
    private val musicServiceConnection: MusicServiceConnection
) : ViewModel() {

    private val _recentMixSongs = MutableStateFlow<List<Song>>(emptyList())

    val recentMix: StateFlow<List<StreamInfoItem>> = _recentMixSongs.map { songs ->
        songs.map { it.toStreamInfoItem() }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _discoveryMix = MutableStateFlow<List<StreamInfoItem>>(emptyList())
    val discoveryMix: StateFlow<List<StreamInfoItem>> = _discoveryMix

    init {
        viewModelScope.launch {
            listeningHistoryDao.getTopRecentSongs(limit = 10).collect { topRecentPlays ->
                if (topRecentPlays.isNotEmpty()) {
                    generateRecommendations(topRecentPlays)
                } else {
                    _recentMixSongs.value = emptyList()
                    _discoveryMix.value = emptyList()
                }
            }
        }
    }

    private suspend fun generateRecommendations(topRecentPlays: List<RecentPlay>) {
        Log.d(TAG, "Generating recommendations...")
        val recentSongIds = topRecentPlays.map { it.songId }
        val recentSongs = libraryRepository.getSongsByIds(recentSongIds)
        _recentMixSongs.value = recentSongIds.mapNotNull { id -> recentSongs.find { it.songId == id } }

        val seedSong = _recentMixSongs.value.firstOrNull()
        if (seedSong != null) {
            val discoveryQuery = "${seedSong.artist} songs"
            Log.d(TAG, "Discovery Mix seed artist: ${seedSong.artist}")

            // FIXED: Call the restored scraper-based search function
            val searchResults = youtubeRepository.search(discoveryQuery, "music_songs")

            val downloadedVideoIds = _recentMixSongs.value.map { it.videoId }.toSet()

            // FIXED: This now correctly assigns a List to the StateFlow's value
            _discoveryMix.value = searchResults.filterIsInstance<StreamInfoItem>().filter { it.url != null && !downloadedVideoIds.contains(it.url.substringAfter("v=")) }
        } else {
            _discoveryMix.value = emptyList()
        }
        Log.d(TAG, "Recommendations generated. RecentMix: ${_recentMixSongs.value.size}, DiscoveryMix: ${_discoveryMix.value.size}")
    }

    fun playRecentMix(selectedIndex: Int) {
        val songs = _recentMixSongs.value
        if (songs.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(songs, selectedIndex)
            }
        }
    }

    fun playDiscoveryMix(selectedIndex: Int) {
        val items = _discoveryMix.value
        if (items.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(items, selectedIndex)
            }
        }
    }
}