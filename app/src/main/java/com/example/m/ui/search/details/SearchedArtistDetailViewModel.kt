// file: com/example/m/ui/search/details/SearchedArtistDetailViewModel.kt
package com.example.m.ui.search.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.example.m.data.database.*
import com.example.m.data.repository.YoutubeRepository
import com.example.m.managers.DialogState
import com.example.m.managers.LibraryActionsManager
import com.example.m.playback.MusicServiceConnection
import com.example.m.ui.search.SearchResult
import com.example.m.ui.search.SearchResultForList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject

data class SearchedArtistDetailsUiState(
    val isLoading: Boolean = true,
    val channelInfo: ChannelInfo? = null,
    val songs: List<SearchResultForList> = emptyList(),
    val releases: List<PlaylistInfoItem> = emptyList(),
    val errorMessage: String? = null,
    val searchType: String = "video",
    val showConfirmAddAllDialog: Boolean = false
)

sealed interface SearchedArtistDetailEvent {
    data class SongSelected(val index: Int) : SearchedArtistDetailEvent
    object Shuffle : SearchedArtistDetailEvent
    object ShowConfirmAddAllToLibraryDialog : SearchedArtistDetailEvent
    object DismissConfirmAddAllToLibraryDialog : SearchedArtistDetailEvent
    object ConfirmAddAllToLibrary : SearchedArtistDetailEvent
    data class AddToLibrary(val streamInfo: StreamInfoItem) : SearchedArtistDetailEvent
    data class PlayNext(val streamInfo: StreamInfoItem) : SearchedArtistDetailEvent
    data class AddToQueue(val streamInfo: StreamInfoItem) : SearchedArtistDetailEvent
    data class RequestCreateGroup(val name: String) : SearchedArtistDetailEvent
    data class SelectGroup(val groupId: Long) : SearchedArtistDetailEvent
    object ResolveConflict : SearchedArtistDetailEvent
    object DismissDialog : SearchedArtistDetailEvent
}


@HiltViewModel
class SearchedArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val youtubeRepository: YoutubeRepository,
    private val musicServiceConnection: MusicServiceConnection,
    private val songDao: SongDao,
    val imageLoader: ImageLoader,
    private val libraryActionsManager: LibraryActionsManager
) : ViewModel() {

    private val channelUrl: String = savedStateHandle["channelUrl"]!!
    private val searchType: String = savedStateHandle["searchType"]!!

    private val _uiState = MutableStateFlow(SearchedArtistDetailsUiState())
    val uiState: StateFlow<SearchedArtistDetailsUiState> = _uiState

    private val localLibrary: StateFlow<List<Song>> = songDao.getAllSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dialogState: StateFlow<DialogState> = libraryActionsManager.dialogState

    init {
        loadArtistDetails()
        viewModelScope.launch {
            localLibrary.drop(1).collect {
                refreshSongStatuses()
            }
        }
    }

    fun onEvent(event: SearchedArtistDetailEvent) {
        when(event) {
            is SearchedArtistDetailEvent.SongSelected -> onSongSelected(event.index)
            is SearchedArtistDetailEvent.Shuffle -> shuffle()
            is SearchedArtistDetailEvent.ShowConfirmAddAllToLibraryDialog -> _uiState.update { it.copy(showConfirmAddAllDialog = true) }
            is SearchedArtistDetailEvent.DismissConfirmAddAllToLibraryDialog -> _uiState.update { it.copy(showConfirmAddAllDialog = false) }
            is SearchedArtistDetailEvent.ConfirmAddAllToLibrary -> confirmAddAllToLibrary()
            is SearchedArtistDetailEvent.AddToLibrary -> libraryActionsManager.addToLibrary(event.streamInfo)
            is SearchedArtistDetailEvent.PlayNext -> musicServiceConnection.playNext(event.streamInfo)
            is SearchedArtistDetailEvent.AddToQueue -> musicServiceConnection.addToQueue(event.streamInfo)
            is SearchedArtistDetailEvent.RequestCreateGroup -> libraryActionsManager.onCreateGroup(event.name)
            is SearchedArtistDetailEvent.SelectGroup -> libraryActionsManager.onGroupSelected(event.groupId)
            is SearchedArtistDetailEvent.ResolveConflict -> libraryActionsManager.onResolveConflict()
            is SearchedArtistDetailEvent.DismissDialog -> libraryActionsManager.dismissDialog()
        }
    }

    private fun refreshSongStatuses() {
        if (_uiState.value.songs.isEmpty()) return
        val localSongsByUrl = localLibrary.value.associateBy { it.youtubeUrl }
        val updatedSongs = _uiState.value.songs.map { searchResultForList ->
            val result = searchResultForList.result
            val normalizedUrl = result.streamInfo.url?.replace("music.youtube.com", "www.youtube.com") ?: ""
            val localSong = localSongsByUrl[normalizedUrl]
            searchResultForList.copy(
                result = result.copy(isInLibrary = localSong?.isInLibrary ?: false, isDownloaded = localSong?.localFilePath != null),
                localSong = localSong
            )
        }
        _uiState.update { it.copy(songs = updatedSongs) }
    }

    private fun loadArtistDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, searchType = this@SearchedArtistDetailViewModel.searchType) }
            try {
                val artistDetails = if (searchType == "music") {
                    youtubeRepository.getMusicArtistDetails(channelUrl, fetchAllPages = false)
                } else {
                    youtubeRepository.getVideoCreatorDetails(channelUrl)
                }

                if (artistDetails != null) {
                    val localSongsByUrl = localLibrary.value.associateBy { it.youtubeUrl }
                    val songResults = artistDetails.songs
                        .distinctBy { "${it.name?.lowercase()?.trim()}::${it.uploaderName?.lowercase()?.trim()}" }
                        .map { streamInfo ->
                            val normalizedUrl = streamInfo.url?.replace("music.youtube.com", "www.youtube.com")
                            val localSong = localSongsByUrl[normalizedUrl]
                            val searchResult = SearchResult(streamInfo, localSong?.isInLibrary ?: false, localSong?.localFilePath != null)
                            SearchResultForList(searchResult, localSong)
                        }
                    _uiState.update {
                        it.copy(isLoading = false, channelInfo = artistDetails.channelInfo, songs = songResults, releases = artistDetails.albums)
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Could not load artist details.") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "An unknown error occurred.") }
            }
        }
    }

    private fun onSongSelected(selectedIndex: Int) {
        val items = _uiState.value.songs.map { it.result.streamInfo }
        if (items.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(items, selectedIndex)
            }
        }
    }

    private fun shuffle() {
        val songsToPlay = _uiState.value.songs
        if (songsToPlay.isNotEmpty()) {
            val finalShuffledList = songsToPlay.shuffled().map { it.result.streamInfo }
            viewModelScope.launch {
                musicServiceConnection.playSongList(finalShuffledList, 0)
            }
        }
    }

    private fun confirmAddAllToLibrary() {
        _uiState.value.songs.map { it.result.streamInfo }.forEach { libraryActionsManager.addToLibrary(it) }
        _uiState.update { it.copy(showConfirmAddAllDialog = false) }
    }
}