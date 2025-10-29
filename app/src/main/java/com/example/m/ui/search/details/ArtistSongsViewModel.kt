// file: com/example/m/ui/search/details/ArtistSongsViewModel.kt
package com.example.m.ui.search.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.example.m.data.database.*
import com.example.m.data.repository.YoutubeRepository
import com.example.m.managers.DialogState
import com.example.m.managers.LibraryActionsManager
import com.example.m.managers.PlaybackListManager
import com.example.m.playback.MusicServiceConnection
import com.example.m.ui.search.SearchResult
import com.example.m.ui.search.SearchResultForList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject

data class ArtistSongsUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val channelInfo: ChannelInfo? = null,
    val songs: List<SearchResultForList> = emptyList(),
    val errorMessage: String? = null,
    val searchType: String = "video",
    val nextPage: Page? = null,
    val songsTabHandler: ListLinkHandler? = null,
    val searchHandler: SearchQueryHandler? = null,
    val nowPlayingMediaId: String? = null
)

sealed interface ArtistSongsEvent {
    data class SongSelected(val index: Int) : ArtistSongsEvent
    object LoadMore : ArtistSongsEvent
    data class AddToLibrary(val result: SearchResult) : ArtistSongsEvent
    data class PlayNext(val result: SearchResult) : ArtistSongsEvent
    data class AddToQueue(val result: SearchResult) : ArtistSongsEvent
    data class RequestCreateGroup(val name: String) : ArtistSongsEvent
    data class SelectGroup(val groupId: Long) : ArtistSongsEvent
    object ResolveConflict : ArtistSongsEvent
    object DismissDialog : ArtistSongsEvent
}

@HiltViewModel
class ArtistSongsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val youtubeRepository: YoutubeRepository,
    private val musicServiceConnection: MusicServiceConnection,
    private val songDao: SongDao,
    private val playbackListManager: PlaybackListManager,
    val imageLoader: ImageLoader,
    private val libraryActionsManager: LibraryActionsManager
) : ViewModel() {

    private val channelUrl: String = savedStateHandle["channelUrl"]!!
    private val searchType: String = savedStateHandle["searchType"]!!
    private var artistName: String? = null

    private val _uiState = MutableStateFlow(ArtistSongsUiState())
    val uiState: StateFlow<ArtistSongsUiState> = _uiState.asStateFlow()

    private val allLocalSongs: StateFlow<List<Song>> = songDao.getAllSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dialogState: StateFlow<DialogState> = libraryActionsManager.dialogState

    init {
        loadContent()
        viewModelScope.launch {
            allLocalSongs.drop(1).collect {
                refreshSongStatuses()
            }
        }
        viewModelScope.launch {
            musicServiceConnection.currentMediaId.collect { mediaId ->
                _uiState.update { it.copy(nowPlayingMediaId = mediaId) }
            }
        }
    }

    override fun onCleared() {
        playbackListManager.clearCurrentListContext()
        super.onCleared()
    }

    fun onEvent(event: ArtistSongsEvent) {
        when(event) {
            is ArtistSongsEvent.SongSelected -> onSongSelected(event.index)
            is ArtistSongsEvent.LoadMore -> loadMoreSongs()
            is ArtistSongsEvent.AddToLibrary -> libraryActionsManager.addToLibrary(event.result.streamInfo)
            is ArtistSongsEvent.PlayNext -> musicServiceConnection.playNext(event.result.streamInfo)
            is ArtistSongsEvent.AddToQueue -> musicServiceConnection.addToQueue(event.result.streamInfo)
            is ArtistSongsEvent.RequestCreateGroup -> libraryActionsManager.onCreateGroup(event.name)
            is ArtistSongsEvent.SelectGroup -> libraryActionsManager.onGroupSelected(event.groupId)
            is ArtistSongsEvent.ResolveConflict -> libraryActionsManager.onResolveConflict()
            is ArtistSongsEvent.DismissDialog -> libraryActionsManager.dismissDialog()
        }
    }

    fun onDialogRequestCreateGroup() = libraryActionsManager.requestCreateGroup()

    private fun refreshSongStatuses() {
        if (_uiState.value.songs.isEmpty()) return
        val localSongsByUrl = allLocalSongs.value.associateBy { it.youtubeUrl }
        val updatedSongs = _uiState.value.songs.map { item ->
            val result = item.result
            val normalizedUrl = result.streamInfo.url?.replace("music.youtube.com", "www.youtube.com") ?: ""
            val localSong = localSongsByUrl[normalizedUrl]
            item.copy(
                result = result.copy(isInLibrary = localSong?.isInLibrary ?: false, isDownloaded = localSong?.localFilePath != null),
                localSong = localSong
            )
        }
        _uiState.update { it.copy(songs = updatedSongs) }
    }

    private fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, searchType = this@ArtistSongsViewModel.searchType) }
            try {
                if (searchType == "music") {
                    val channelInfo = withContext(Dispatchers.IO) { ChannelInfo.getInfo(ServiceList.YouTube, channelUrl) }
                    this@ArtistSongsViewModel.artistName = channelInfo.name
                    val plainArtistName = channelInfo.name.removeSuffix(" - Topic").trim()
                    if (plainArtistName.isBlank()) throw ExtractionException("Artist name could not be determined.")

                    val searchPage = youtubeRepository.search(plainArtistName, "music_songs")
                    if (searchPage != null) {
                        val filteredSongs = searchPage.items.filterIsInstance<StreamInfoItem>()
                            .filter { it.uploaderName.equals(plainArtistName, ignoreCase = true) }
                            .distinctBy { "${it.name?.lowercase()?.trim()}::${it.uploaderName?.lowercase()?.trim()}" }
                        updateSongsInState(filteredSongs)
                        _uiState.update { it.copy(isLoading = false, channelInfo = channelInfo, nextPage = searchPage.nextPage, searchHandler = searchPage.queryHandler, songsTabHandler = null) }
                    } else {
                        throw ExtractionException("Search returned no results for artist: $artistName")
                    }
                } else {
                    val artistDetails = youtubeRepository.getVideoCreatorDetails(channelUrl)
                    if (artistDetails != null) {
                        updateSongsInState(artistDetails.songs)
                        _uiState.update { it.copy(isLoading = false, channelInfo = artistDetails.channelInfo, nextPage = artistDetails.songsNextPage, songsTabHandler = artistDetails.songsTabHandler, searchHandler = null) }
                    } else {
                        throw ExtractionException("Could not load channel details.")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "An unknown error occurred.") }
            }
        }
    }

    private fun loadMoreSongs() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState.isLoadingMore || currentState.nextPage == null) return@launch
            _uiState.update { it.copy(isLoadingMore = true) }

            val resultPage = if (currentState.searchHandler != null) {
                youtubeRepository.getMoreSearchResults(currentState.searchHandler, currentState.nextPage)
            } else if (currentState.songsTabHandler != null) {
                youtubeRepository.getMoreArtistSongs(currentState.songsTabHandler, currentState.nextPage)
            } else { null }

            if (resultPage != null) {
                val newItems = resultPage.items.filterIsInstance<StreamInfoItem>()
                val plainArtistName = artistName?.removeSuffix(" - Topic")?.trim()

                val uniqueNewItems = if (currentState.searchHandler != null && !plainArtistName.isNullOrBlank()) {
                    newItems.filter { it.uploaderName.equals(plainArtistName, ignoreCase = true) }
                } else { newItems }.distinctBy { "${it.name?.lowercase()?.trim()}::${it.uploaderName?.lowercase()?.trim()}" }

                val existingKeys = currentState.songs.map { val s = it.result.streamInfo; "${s.name?.lowercase()?.trim()}::${s.uploaderName?.lowercase()?.trim()}" }.toSet()
                val trulyNewItems = uniqueNewItems.filter { !existingKeys.contains("${it.name?.lowercase()?.trim()}::${it.uploaderName?.lowercase()?.trim()}") }

                updateSongsInState(trulyNewItems, append = true)
                _uiState.update { it.copy(nextPage = resultPage.nextPage) }
            }
            _uiState.update { it.copy(isLoadingMore = false) }
        }
    }


    private suspend fun updateSongsInState(songs: List<StreamInfoItem>, append: Boolean = false) {
        val librarySongs = allLocalSongs.value.associateBy { it.youtubeUrl }
        val songResults = songs.map { streamInfo ->
            val normalizedUrl = streamInfo.url?.replace("music.youtube.com", "www.youtube.com") ?: ""
            val localSong = librarySongs[normalizedUrl]
            val searchResult = SearchResult(
                streamInfo,
                isInLibrary = localSong?.isInLibrary ?: false,
                isDownloaded = localSong?.localFilePath != null
            )
            SearchResultForList(searchResult, localSong)
        }
        _uiState.update {
            if (append) it.copy(songs = it.songs + songResults)
            else it.copy(songs = songResults)
        }
    }

    private fun onSongSelected(selectedIndex: Int) {
        val items = _uiState.value.songs.map { it.result.streamInfo }
        if (items.isNotEmpty()) {
            viewModelScope.launch {
                val sourceHandler = _uiState.value.searchHandler ?: _uiState.value.songsTabHandler
                playbackListManager.setCurrentListContext(sourceHandler, _uiState.value.nextPage)
                musicServiceConnection.playSongList(items, selectedIndex)
            }
        }
    }
}