package com.example.m.ui.search.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.example.m.data.repository.YoutubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import javax.inject.Inject

data class ArtistAlbumsUiState(
    val isLoading: Boolean = true,
    val channelInfo: ChannelInfo? = null,
    val albums: List<PlaylistInfoItem> = emptyList(),
    val errorMessage: String? = null,
    val searchType: String = "video"
)

@HiltViewModel
class ArtistAlbumsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val youtubeRepository: YoutubeRepository,
    val imageLoader: ImageLoader
) : ViewModel() {

    private val channelUrl: String = savedStateHandle["channelUrl"]!!
    private val searchType: String = savedStateHandle["searchType"]!!

    private val _uiState = MutableStateFlow(ArtistAlbumsUiState())
    val uiState: StateFlow<ArtistAlbumsUiState> = _uiState.asStateFlow()

    init {
        loadContent()
    }

    private fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, searchType = this@ArtistAlbumsViewModel.searchType) }
            try {
                val artistDetails = if (searchType == "music") {
                    youtubeRepository.getMusicArtistDetails(channelUrl)
                } else {
                    youtubeRepository.getVideoCreatorDetails(channelUrl)
                }

                if (artistDetails != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            channelInfo = artistDetails.channelInfo,
                            albums = artistDetails.albums
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Could not load content.") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "An unknown error occurred.") }
            }
        }
    }
}