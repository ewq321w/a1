// file: com/example/m/ui/search/details/ArtistSongsViewModel.kt
package com.example.m.ui.search.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.example.m.data.PreferencesManager
import com.example.m.data.database.*
import com.example.m.data.repository.LibraryRepository
import com.example.m.data.repository.YoutubeRepository
import com.example.m.managers.DownloadStatus
import com.example.m.managers.DownloadStatusManager
import com.example.m.managers.PlaybackListManager
import com.example.m.managers.PlaylistManager
import com.example.m.playback.MusicServiceConnection
import com.example.m.ui.library.ConflictDialogState
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
    val searchHandler: SearchQueryHandler? = null
)

@HiltViewModel
class ArtistSongsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val youtubeRepository: YoutubeRepository,
    private val musicServiceConnection: MusicServiceConnection,
    private val songDao: SongDao,
    private val playlistManager: PlaylistManager,
    private val libraryRepository: LibraryRepository,
    private val downloadStatusManager: DownloadStatusManager,
    private val playbackListManager: PlaybackListManager,
    val imageLoader: ImageLoader,
    private val preferencesManager: PreferencesManager,
    private val libraryGroupDao: LibraryGroupDao
) : ViewModel() {

    private val channelUrl: String = savedStateHandle["channelUrl"]!!
    private val searchType: String = savedStateHandle["searchType"]!!
    private var artistName: String? = null

    private val _uiState = MutableStateFlow(ArtistSongsUiState())
    val uiState: StateFlow<ArtistSongsUiState> = _uiState.asStateFlow()

    private val allLocalSongs: StateFlow<List<Song>> = songDao.getAllSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val libraryGroups: StateFlow<List<LibraryGroup>> = libraryGroupDao.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var conflictDialogState by mutableStateOf<ConflictDialogState?>(null)
        private set

    var showCreateGroupDialog by mutableStateOf(false)
        private set
    var showSelectGroupDialog by mutableStateOf(false)
        private set
    private var pendingAction by mutableStateOf<PendingAction?>(null)

    init {
        loadContent()
        viewModelScope.launch {
            allLocalSongs.drop(1).collect {
                refreshSongStatuses()
            }
        }
        viewModelScope.launch {
            downloadStatusManager.statuses.collect { statuses ->
                refreshDownloadStatuses(statuses)
            }
        }
    }

    private fun refreshDownloadStatuses(statuses: Map<String, DownloadStatus>) {
        val currentState = _uiState.value
        if (currentState.songs.isEmpty()) return

        val updatedSongs = currentState.songs.map { searchResultForList ->
            val normalizedUrl = searchResultForList.result.streamInfo.url?.replace("music.youtube.com", "www.youtube.com")
            searchResultForList.copy(downloadStatus = statuses[normalizedUrl])
        }
        _uiState.update { it.copy(songs = updatedSongs) }
    }

    private fun refreshSongStatuses() {
        val currentState = _uiState.value
        if (currentState.songs.isEmpty()) return

        val localSongsByUrl = allLocalSongs.value.associateBy { it.youtubeUrl }

        val updatedSongs = currentState.songs.map { searchResultForList ->
            val result = searchResultForList.result
            val normalizedUrl = result.streamInfo.url?.replace("music.youtube.com", "www.youtube.com") ?: ""
            val localSong = localSongsByUrl[normalizedUrl]
            searchResultForList.copy(
                result = result.copy(
                    isInLibrary = localSong?.isInLibrary ?: false,
                    isDownloaded = localSong?.localFilePath != null
                )
            )
        }
        _uiState.update { it.copy(songs = updatedSongs) }
    }

    override fun onCleared() {
        playbackListManager.clearCurrentListContext()
        super.onCleared()
    }

    private fun loadContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, searchType = this@ArtistSongsViewModel.searchType) }
            try {
                if (searchType == "music") {
                    val channelInfo = withContext(Dispatchers.IO) {
                        ChannelInfo.getInfo(ServiceList.YouTube, channelUrl)
                    }
                    this@ArtistSongsViewModel.artistName = channelInfo.name

                    val plainArtistName = channelInfo.name.removeSuffix(" - Topic").trim()
                    if (plainArtistName.isBlank()) {
                        throw ExtractionException("Artist name could not be determined.")
                    }

                    val searchPage = youtubeRepository.search(plainArtistName, "music_songs")
                    if (searchPage != null) {
                        val unfilteredSongs = searchPage.items.filterIsInstance<StreamInfoItem>()
                        val filteredSongs = unfilteredSongs.filter {
                            it.uploaderName.equals(plainArtistName, ignoreCase = true)
                        }.distinctBy { "${it.name?.lowercase()?.trim()}::${it.uploaderName?.lowercase()?.trim()}" }
                        updateSongsInState(filteredSongs)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                channelInfo = channelInfo,
                                nextPage = searchPage.nextPage,
                                searchHandler = searchPage.queryHandler,
                                songsTabHandler = null
                            )
                        }
                    } else {
                        throw ExtractionException("Search returned no results for artist: $artistName")
                    }
                } else {
                    val artistDetails = youtubeRepository.getVideoCreatorDetails(channelUrl)
                    if (artistDetails != null) {
                        updateSongsInState(artistDetails.songs)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                channelInfo = artistDetails.channelInfo,
                                nextPage = artistDetails.songsNextPage,
                                songsTabHandler = artistDetails.songsTabHandler,
                                searchHandler = null
                            )
                        }
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

    fun loadMoreSongs() {
        viewModelScope.launch {
            val currentState = uiState.value
            if (currentState.isLoadingMore || currentState.nextPage == null) return@launch

            _uiState.update { it.copy(isLoadingMore = true) }

            val resultPage = if (currentState.searchHandler != null) {
                youtubeRepository.getMoreSearchResults(currentState.searchHandler, currentState.nextPage)
            } else if (currentState.songsTabHandler != null) {
                youtubeRepository.getMoreArtistSongs(currentState.songsTabHandler, currentState.nextPage)
            } else {
                null
            }

            if (resultPage != null) {
                val newItems = resultPage.items.filterIsInstance<StreamInfoItem>()
                val plainArtistName = artistName?.removeSuffix(" - Topic")?.trim()

                val uniqueNewItems = if (currentState.searchHandler != null && !plainArtistName.isNullOrBlank()) {
                    newItems.filter { it.uploaderName.equals(plainArtistName, ignoreCase = true) }
                } else {
                    newItems
                }.distinctBy { "${it.name?.lowercase()?.trim()}::${it.uploaderName?.lowercase()?.trim()}" }

                val existingKeys = currentState.songs.map {
                    val s = it.result.streamInfo
                    "${s.name?.lowercase()?.trim()}::${s.uploaderName?.lowercase()?.trim()}"
                }.toSet()

                val trulyNewItems = uniqueNewItems.filter {
                    val key = "${it.name?.lowercase()?.trim()}::${it.uploaderName?.lowercase()?.trim()}"
                    !existingKeys.contains(key)
                }

                updateSongsInState(trulyNewItems, append = true)
                _uiState.update { it.copy(nextPage = resultPage.nextPage) }
            }

            _uiState.update { it.copy(isLoadingMore = false) }
        }
    }


    private suspend fun updateSongsInState(songs: List<StreamInfoItem>, append: Boolean = false) {
        val librarySongs = allLocalSongs.value.associateBy { it.youtubeUrl }
        val statuses = downloadStatusManager.statuses.value

        val songResults = songs.map { streamInfo ->
            val rawUrl = streamInfo.url ?: ""
            val normalizedUrl = rawUrl.replace("music.youtube.com", "www.youtube.com")

            val localSong = librarySongs[normalizedUrl]
            val searchResult = SearchResult(
                streamInfo,
                isInLibrary = localSong?.isInLibrary ?: false,
                isDownloaded = localSong?.localFilePath != null
            )
            SearchResultForList(searchResult, statuses[normalizedUrl])
        }

        _uiState.update {
            if (append) {
                it.copy(songs = it.songs + songResults)
            } else {
                it.copy(songs = songResults)
            }
        }
    }

    fun onSongSelected(selectedIndex: Int) {
        val items = uiState.value.songs.map { it.result.streamInfo }
        if (items.isNotEmpty()) {
            viewModelScope.launch {
                val sourceHandler = uiState.value.searchHandler ?: uiState.value.songsTabHandler
                playbackListManager.setCurrentListContext(sourceHandler, uiState.value.nextPage)
                musicServiceConnection.playSongList(items, selectedIndex)
            }
        }
    }

    private suspend fun addSongToLibraryAndLink(song: Song, groupId: Long) {
        val updatedSong = song.copy(
            isInLibrary = true,
            dateAddedTimestamp = System.currentTimeMillis(),
            libraryGroupId = groupId
        )
        songDao.updateSong(updatedSong)
        libraryRepository.linkSongToArtist(updatedSong)
    }

    fun addSongToLibrary(result: SearchResult) {
        handleLibraryAction(PendingAction.AddToLibrary(result.streamInfo))
    }

    private fun handleLibraryAction(action: PendingAction) {
        viewModelScope.launch(Dispatchers.IO) {
            pendingAction = action
            if (libraryGroups.value.isEmpty()) {
                withContext(Dispatchers.Main) { showCreateGroupDialog = true }
                return@launch
            }

            val activeGroupId = preferencesManager.activeLibraryGroupId
            if (activeGroupId == 0L) {
                withContext(Dispatchers.Main) { showSelectGroupDialog = true }
                return@launch
            }
            proceedWithAction(action, activeGroupId)
        }
    }

    private suspend fun proceedWithAction(action: PendingAction, groupId: Long) {
        when (action) {
            is PendingAction.AddToLibrary -> {
                val song = playlistManager.getSongForItem(action.item, groupId)
                if (song.isInLibrary) return
                proceedWithAddingToLibrary(song, groupId)
            }
            else -> {}
        }
    }


    private suspend fun proceedWithAddingToLibrary(song: Song, targetGroupId: Long) {
        val conflict = libraryRepository.checkArtistGroupConflict(song.artist, targetGroupId)
        if (conflict != null && !song.isInLibrary) {
            withContext(Dispatchers.Main) {
                val targetGroup = libraryGroupDao.getGroup(targetGroupId)
                if (targetGroup != null) {
                    conflictDialogState = ConflictDialogState(song, targetGroupId, targetGroup.name, conflict)
                }
            }
        } else {
            if (!song.isInLibrary) {
                addSongToLibraryAndLink(song, targetGroupId)
            }
        }
    }

    fun createGroupAndProceed(groupName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val action = pendingAction ?: return@launch
            val newGroupId = libraryGroupDao.insertGroup(LibraryGroup(name = groupName.trim()))
            withContext(Dispatchers.Main) {
                preferencesManager.activeLibraryGroupId = newGroupId
            }
            proceedWithAction(action, newGroupId)
            pendingAction = null
        }
        showCreateGroupDialog = false
    }

    fun onGroupSelectedForAddition(groupId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val action = pendingAction ?: return@launch
            withContext(Dispatchers.Main) {
                preferencesManager.activeLibraryGroupId = groupId
            }
            proceedWithAction(action, groupId)
            pendingAction = null
        }
        showSelectGroupDialog = false
    }


    fun dismissCreateGroupDialog() {
        showCreateGroupDialog = false
        pendingAction = null
    }

    fun dismissSelectGroupDialog() {
        showSelectGroupDialog = false
        pendingAction = null
    }

    fun resolveConflictByMoving() {
        val state = conflictDialogState ?: return
        viewModelScope.launch(Dispatchers.IO) {
            libraryRepository.moveArtistToLibraryGroup(state.song.artist, state.targetGroupId)
            addSongToLibraryAndLink(state.song, state.targetGroupId)
        }
        dismissConflictDialog()
    }

    fun dismissConflictDialog() {
        conflictDialogState = null
    }

    fun onPlayNext(result: SearchResult) {
        musicServiceConnection.playNext(result.streamInfo)
    }

    fun onAddToQueue(result: SearchResult) {
        musicServiceConnection.addToQueue(result.streamInfo)
    }

    fun prepareToCreateGroup() {
        showSelectGroupDialog = false
        showCreateGroupDialog = true
    }
}