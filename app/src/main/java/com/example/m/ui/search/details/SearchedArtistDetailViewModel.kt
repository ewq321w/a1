// file: com/example/m/ui/search/details/SearchedArtistDetailViewModel.kt
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
import com.example.m.managers.PlaylistManager
import com.example.m.playback.MusicServiceConnection
import com.example.m.ui.library.ConflictDialogState
import com.example.m.ui.search.SearchResult
import com.example.m.ui.search.SearchResultForList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val searchType: String = "video"
)

@HiltViewModel
class SearchedArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val youtubeRepository: YoutubeRepository,
    private val playlistManager: PlaylistManager,
    private val musicServiceConnection: MusicServiceConnection,
    private val songDao: SongDao,
    private val libraryRepository: LibraryRepository,
    private val downloadStatusManager: DownloadStatusManager,
    val imageLoader: ImageLoader,
    private val preferencesManager: PreferencesManager,
    private val libraryGroupDao: LibraryGroupDao
) : ViewModel() {

    private val channelUrl: String = savedStateHandle["channelUrl"]!!
    private val searchType: String = savedStateHandle["searchType"]!!

    private val _uiState = MutableStateFlow(SearchedArtistDetailsUiState())
    val uiState: StateFlow<SearchedArtistDetailsUiState> = _uiState

    private val localLibrary: StateFlow<List<Song>> = songDao.getAllSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val libraryGroups: StateFlow<List<LibraryGroup>> = libraryGroupDao.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var showConfirmAddAllDialog by mutableStateOf(false)
        private set

    var conflictDialogState by mutableStateOf<ConflictDialogState?>(null)
        private set

    var showCreateGroupDialog by mutableStateOf(false)
        private set
    var showSelectGroupDialog by mutableStateOf(false)
        private set
    private var pendingAction by mutableStateOf<PendingAction?>(null)


    private var songUpdateJob: Job? = null

    init {
        loadArtistDetails()
        viewModelScope.launch {
            localLibrary.drop(1).collect {
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

        val localSongsByUrl = localLibrary.value.associateBy { it.youtubeUrl }

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

    private fun extractVideoId(url: String?): String? {
        return url?.substringAfter("v=")?.substringBefore('&')
    }

    private fun loadArtistDetails() {
        songUpdateJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, searchType = this@SearchedArtistDetailViewModel.searchType) }
            try {
                val artistDetails = if (searchType == "music") {
                    youtubeRepository.getMusicArtistDetails(channelUrl, fetchAllPages = false)
                } else {
                    youtubeRepository.getVideoCreatorDetails(channelUrl)
                }

                if (artistDetails != null) {
                    val initialStatuses = downloadStatusManager.statuses.value
                    val librarySongs = localLibrary.value
                    val libraryMap = librarySongs.filter { !it.videoId.isNullOrBlank() }.associateBy { it.videoId!! }
                    val songResults = artistDetails.songs
                        .distinctBy { "${it.name?.lowercase()?.trim()}::${it.uploaderName?.lowercase()?.trim()}" }
                        .map { streamInfo ->
                            val videoId = extractVideoId(streamInfo.url)
                            val localSong = videoId?.let { libraryMap[it] }
                            val searchResult = SearchResult(
                                streamInfo,
                                localSong?.isInLibrary ?: false,
                                localSong?.localFilePath != null
                            )
                            val normalizedUrl = streamInfo.url?.replace("music.youtube.com", "www.youtube.com")
                            SearchResultForList(searchResult, initialStatuses[normalizedUrl])
                        }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            channelInfo = artistDetails.channelInfo,
                            songs = songResults,
                            releases = artistDetails.albums
                        )
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

    fun onSongSelected(selectedIndex: Int) {
        val items = uiState.value.songs.map { it.result.streamInfo }
        if (items.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(items, selectedIndex)
            }
        }
    }

    fun shuffle() {
        val songsToPlay = uiState.value.songs
        if (songsToPlay.isNotEmpty()) {
            val finalShuffledList = songsToPlay.shuffled().map { it.result.streamInfo }
            viewModelScope.launch {
                musicServiceConnection.playSongList(finalShuffledList, 0)
            }
        }
    }

    fun onAddAllToLibraryClicked() {
        showConfirmAddAllDialog = true
    }

    fun confirmAddAllToLibrary() {
        val songsToAdd = uiState.value.songs.map { it.result.streamInfo }
        handleLibraryAction(PendingAction.AddAllToLibrary(songsToAdd))
        showConfirmAddAllDialog = false
    }

    fun dismissConfirmAddAllToLibraryDialog() {
        showConfirmAddAllDialog = false
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

    fun addSongToLibrary(streamInfo: StreamInfoItem) {
        handleLibraryAction(PendingAction.AddToLibrary(streamInfo))
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
            is PendingAction.AddAllToLibrary -> {
                action.items.forEach { streamInfo ->
                    val song = playlistManager.getSongForItem(streamInfo, groupId)
                    if (!song.isInLibrary) {
                        proceedWithAddingToLibrary(song, groupId)
                    }
                }
            }
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

    fun onPlayNext(streamInfo: StreamInfoItem) {
        musicServiceConnection.playNext(streamInfo)
    }

    fun onAddToQueue(streamInfo: StreamInfoItem) {
        musicServiceConnection.addToQueue(streamInfo)
    }

    fun prepareToCreateGroup() {
        showSelectGroupDialog = false
        showCreateGroupDialog = true
    }
}