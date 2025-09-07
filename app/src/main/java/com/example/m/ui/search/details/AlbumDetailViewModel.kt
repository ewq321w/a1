// file: com/example/m/ui/search/details/AlbumDetailViewModel.kt
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
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject

data class AlbumDetailUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val albumInfo: PlaylistInfo? = null,
    val songs: List<SearchResultForList> = emptyList(),
    val errorMessage: String? = null,
    val searchType: String = "music",
    val nextPage: Page? = null,
    val showConfirmAddAllDialog: Boolean = false
)

sealed interface AlbumDetailEvent {
    data class SongSelected(val index: Int) : AlbumDetailEvent
    object Shuffle : AlbumDetailEvent
    object LoadMore : AlbumDetailEvent
    object ShowConfirmAddAllToLibraryDialog : AlbumDetailEvent
    object DismissConfirmAddAllToLibraryDialog : AlbumDetailEvent
    object ConfirmAddAllToLibrary : AlbumDetailEvent
    data class AddToLibrary(val result: SearchResult) : AlbumDetailEvent
    data class PlayNext(val result: SearchResult) : AlbumDetailEvent
    data class AddToQueue(val result: SearchResult) : AlbumDetailEvent
    data class RequestCreateGroup(val name: String) : AlbumDetailEvent
    data class SelectGroup(val groupId: Long) : AlbumDetailEvent
    object ResolveConflict : AlbumDetailEvent
    object DismissDialog : AlbumDetailEvent
}

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val youtubeRepository: YoutubeRepository,
    private val musicServiceConnection: MusicServiceConnection,
    private val songDao: SongDao,
    val imageLoader: ImageLoader,
    private val libraryActionsManager: LibraryActionsManager
) : ViewModel() {
    private val albumUrl: String = savedStateHandle["albumUrl"]!!
    private val searchType: String = savedStateHandle["searchType"]!!

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    private val localLibrary: StateFlow<List<Song>> = songDao.getAllSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dialogState: StateFlow<DialogState> = libraryActionsManager.dialogState

    init {
        loadAlbumDetails()
        viewModelScope.launch {
            localLibrary.drop(1).collect { refreshSongStatuses() }
        }
    }

    fun onEvent(event: AlbumDetailEvent) {
        when(event) {
            is AlbumDetailEvent.SongSelected -> onSongSelected(event.index)
            is AlbumDetailEvent.Shuffle -> shuffle()
            is AlbumDetailEvent.LoadMore -> loadMoreSongs()
            is AlbumDetailEvent.ShowConfirmAddAllToLibraryDialog -> _uiState.update { it.copy(showConfirmAddAllDialog = true) }
            is AlbumDetailEvent.DismissConfirmAddAllToLibraryDialog -> _uiState.update { it.copy(showConfirmAddAllDialog = false) }
            is AlbumDetailEvent.ConfirmAddAllToLibrary -> confirmAddAllToLibrary()
            is AlbumDetailEvent.AddToLibrary -> libraryActionsManager.addToLibrary(event.result.streamInfo)
            is AlbumDetailEvent.PlayNext -> musicServiceConnection.playNext(event.result.streamInfo)
            is AlbumDetailEvent.AddToQueue -> musicServiceConnection.addToQueue(event.result.streamInfo)
            is AlbumDetailEvent.RequestCreateGroup -> libraryActionsManager.onCreateGroup(event.name)
            is AlbumDetailEvent.SelectGroup -> libraryActionsManager.onGroupSelected(event.groupId)
            is AlbumDetailEvent.ResolveConflict -> libraryActionsManager.onResolveConflict()
            is AlbumDetailEvent.DismissDialog -> libraryActionsManager.dismissDialog()
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

    private fun loadAlbumDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, searchType = this@AlbumDetailViewModel.searchType) }
            try {
                val result = youtubeRepository.getPlaylistDetails(albumUrl)
                if (result != null) {
                    val details = result.playlistInfo
                    val localSongsByUrl = localLibrary.value.associateBy { it.youtubeUrl }
                    val songResults = details.relatedItems.filterIsInstance<StreamInfoItem>().map { streamInfo ->
                        val normalizedUrl = streamInfo.url?.replace("music.youtube.com", "www.youtube.com")
                        val localSong = localSongsByUrl[normalizedUrl]
                        val searchResult = SearchResult(streamInfo, localSong?.isInLibrary ?: false, localSong?.localFilePath != null)
                        SearchResultForList(searchResult, localSong)
                    }
                    _uiState.update { it.copy(isLoading = false, albumInfo = details, songs = songResults, nextPage = result.nextPage) }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Could not load album details.") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "An unknown error occurred.") }
            }
        }
    }

    private fun loadMoreSongs() {
        val currentState = _uiState.value
        if (currentState.isLoadingMore || currentState.nextPage == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val result = youtubeRepository.getMorePlaylistItems(albumUrl, currentState.nextPage)
                if (result != null) {
                    val localSongsByUrl = localLibrary.value.associateBy { it.youtubeUrl }
                    val newSongResults = result.items.map { streamInfo ->
                        val normalizedUrl = streamInfo.url?.replace("music.youtube.com", "www.youtube.com")
                        val localSong = localSongsByUrl[normalizedUrl]
                        val searchResult = SearchResult(streamInfo, localSong?.isInLibrary ?: false, localSong?.localFilePath != null)
                        SearchResultForList(searchResult, localSong)
                    }
                    _uiState.update {
                        it.copy(isLoadingMore = false, songs = it.songs + newSongResults, nextPage = result.nextPage)
                    }
                } else {
                    _uiState.update { it.copy(isLoadingMore = false, nextPage = null) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoadingMore = false, errorMessage = e.message) }
            }
        }
    }

    private fun onSongSelected(selectedIndex: Int) {
        val items = _uiState.value.songs.map { it.result.streamInfo }
        if (items.isNotEmpty()) {
            viewModelScope.launch { musicServiceConnection.playSongList(items, selectedIndex) }
        }
    }

    private fun shuffle() {
        val songsToPlay = _uiState.value.songs
        if (songsToPlay.isNotEmpty()) {
            viewModelScope.launch { musicServiceConnection.playSongList(songsToPlay.shuffled().map { it.result.streamInfo }, 0) }
        }
    }

    private fun confirmAddAllToLibrary() {
        _uiState.value.songs.map { it.result.streamInfo }.forEach {
            libraryActionsManager.addToLibrary(it)
        }
        _uiState.update { it.copy(showConfirmAddAllDialog = false) }
    }
}