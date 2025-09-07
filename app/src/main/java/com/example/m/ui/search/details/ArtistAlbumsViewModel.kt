// file: com/example/m/ui/search/details/ArtistAlbumsViewModel.kt
package com.example.m.ui.search.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.example.m.data.repository.YoutubeRepository
import com.example.m.playback.MusicServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject

data class ArtistAlbumsUiState(
    val isLoading: Boolean = true,
    val channelInfo: ChannelInfo? = null,
    val albums: List<PlaylistInfoItem> = emptyList(),
    val errorMessage: String? = null,
    val searchType: String = "video",
    val releaseType: String? = null,
    val artistName: String? = null
)

sealed interface ArtistAlbumsEvent {
    data class ShuffleAlbum(val album: PlaylistInfoItem) : ArtistAlbumsEvent
}

@HiltViewModel
class ArtistAlbumsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val youtubeRepository: YoutubeRepository,
    private val musicServiceConnection: MusicServiceConnection,
    val imageLoader: ImageLoader
) : ViewModel() {

    private val channelUrl: String = savedStateHandle["channelUrl"]!!
    private val searchType: String = savedStateHandle["searchType"]!!
    private val releaseType: String? = savedStateHandle["releaseType"]
    private val artistName: String? = savedStateHandle["artistName"]

    private val _uiState = MutableStateFlow(ArtistAlbumsUiState())
    val uiState: StateFlow<ArtistAlbumsUiState> = _uiState.asStateFlow()

    init {
        loadContent()
    }

    fun onEvent(event: ArtistAlbumsEvent) {
        when (event) {
            is ArtistAlbumsEvent.ShuffleAlbum -> onShuffleAlbum(event.album)
        }
    }

    private fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, searchType = this@ArtistAlbumsViewModel.searchType, releaseType = this@ArtistAlbumsViewModel.releaseType, artistName = this@ArtistAlbumsViewModel.artistName) }
            try {
                val artistDetails = if (searchType == "music") {
                    youtubeRepository.getMusicArtistDetails(channelUrl, fetchAllPages = true)
                } else {
                    youtubeRepository.getVideoCreatorDetails(channelUrl)
                }

                if (artistDetails != null) {
                    val albumsToShow = if (releaseType == "all") {
                        artistDetails.albums
                    } else {
                        when (releaseType) {
                            "albums" -> artistDetails.albums.filter { it.streamCount > 3 }
                            "singles" -> artistDetails.albums.filter { it.streamCount in 1..3 }
                            else -> artistDetails.albums
                        }
                    }
                    _uiState.update { it.copy(isLoading = false, channelInfo = artistDetails.channelInfo, albums = albumsToShow) }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Could not load content.") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "An unknown error occurred.") }
            }
        }
    }

    private fun onShuffleAlbum(album: PlaylistInfoItem) {
        viewModelScope.launch {
            val playlistDetails = youtubeRepository.getPlaylistDetails(album.url!!)
            playlistDetails?.let {
                val songs = it.playlistInfo.relatedItems.filterIsInstance<StreamInfoItem>()
                musicServiceConnection.shuffleSongList(songs, 0)
            }
        }
    }
}