// file: com/example/m/ui/library/tabs/SongsViewModel.kt
package com.example.m.ui.library.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.PreferencesManager
import com.example.m.data.database.ArtistDao
import com.example.m.data.database.Song
import com.example.m.data.database.SongDao
import com.example.m.data.repository.AutoDownloadConflict
import com.example.m.data.repository.LibraryRepository
import com.example.m.managers.DialogState
import com.example.m.managers.LibraryActionsManager
import com.example.m.managers.PlaylistActionState
import com.example.m.managers.PlaylistActionsManager
import com.example.m.playback.MusicServiceConnection
import com.example.m.ui.library.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SongsUiState(
    val songs: List<Song> = emptyList(),
    val downloadFilter: DownloadFilter = DownloadFilter.ALL,
    val playlistFilter: PlaylistFilter = PlaylistFilter.ALL,
    val groupingFilter: GroupingFilter = GroupingFilter.ALL,
    val itemPendingDeletion: Song? = null,
    val sortOrder: SongSortOrder = SongSortOrder.ARTIST,
    val nowPlayingMediaId: String? = null
)

sealed interface SongsTabEvent {
    data class SetDownloadFilter(val filter: DownloadFilter) : SongsTabEvent
    data class SetPlaylistFilter(val filter: PlaylistFilter) : SongsTabEvent
    data class SetGroupingFilter(val filter: GroupingFilter) : SongsTabEvent
    data class SetSortOrder(val order: SongSortOrder) : SongsTabEvent
    data class SongSelected(val index: Int) : SongsTabEvent
    object ShuffleFilteredSongs : SongsTabEvent
    data class DownloadSong(val song: Song) : SongsTabEvent
    data class DeleteSongDownload(val song: Song) : SongsTabEvent
    data class SetItemForDeletion(val song: Song) : SongsTabEvent
    object ClearItemForDeletion : SongsTabEvent
    object ConfirmDeletion : SongsTabEvent
    data class AddToPlaylist(val song: Song) : SongsTabEvent
    data class PlaySongNext(val song: Song) : SongsTabEvent
    data class AddSongToQueue(val song: Song) : SongsTabEvent
    data class ShuffleSong(val song: Song) : SongsTabEvent
    data class GoToArtist(val song: Song) : SongsTabEvent
    data class AddToLibrary(val song: Song) : SongsTabEvent
}

@HiltViewModel
class SongsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val libraryRepository: LibraryRepository,
    private val musicServiceConnection: MusicServiceConnection,
    private val songDao: SongDao,
    private val artistDao: ArtistDao,
    private val libraryActionsManager: LibraryActionsManager,
    private val playlistActionsManager: PlaylistActionsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SongsUiState())
    val uiState: StateFlow<SongsUiState> = _uiState.asStateFlow()

    val dialogState: StateFlow<DialogState> = libraryActionsManager.dialogState
    val playlistActionState: StateFlow<PlaylistActionState> = playlistActionsManager.state

    private val _navigateToArtist = MutableSharedFlow<Long>()
    val navigateToArtist: SharedFlow<Long> = _navigateToArtist.asSharedFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    init {
        _uiState.update { it.copy(sortOrder = preferencesManager.songsSortOrder) }

        combine(
            _uiState.map { it.sortOrder }.distinctUntilChanged(),
            preferencesManager.getActiveLibraryGroupIdFlow(),
            _uiState.map { it.downloadFilter }.distinctUntilChanged(),
            _uiState.map { it.playlistFilter }.distinctUntilChanged(),
            _uiState.map { it.groupingFilter }.distinctUntilChanged()
        ) { sortOrder, groupId, downloadFilter, playlistFilter, groupingFilter ->
            Quintuple(sortOrder, groupId, downloadFilter, playlistFilter, groupingFilter)
        }.flatMapLatest { (order, groupId, downloadFilter, playlistFilter, groupingFilter) ->
            val songsFlow = getSortedSongsFlow(order, groupId)
            combine(
                songsFlow,
                libraryRepository.getSongsInPlaylists(),
                artistDao.getAllSongIdsInGroups()
            ) { allSongs, songsInPlaylists, songIdsInGroups ->
                val songsInPlaylistsIds = songsInPlaylists.map { it.songId }.toSet()
                val songIdsInGroupsSet = songIdsInGroups.toSet()

                allSongs.filter { song ->
                    val downloadMatch = when (downloadFilter) {
                        DownloadFilter.ALL -> true
                        DownloadFilter.DOWNLOADED -> song.localFilePath != null
                    }
                    val playlistMatch = when (playlistFilter) {
                        PlaylistFilter.ALL -> true
                        PlaylistFilter.IN_PLAYLIST -> songsInPlaylistsIds.contains(song.songId)
                    }
                    val groupingMatch = when (groupingFilter) {
                        GroupingFilter.ALL -> true
                        GroupingFilter.UNGROUPED -> !songIdsInGroupsSet.contains(song.songId)
                    }
                    downloadMatch && playlistMatch && groupingMatch
                }
            }
        }.onEach { songs ->
            _uiState.update { it.copy(songs = songs) }
        }.launchIn(viewModelScope)

        viewModelScope.launch {
            musicServiceConnection.currentMediaId.collect { mediaId ->
                _uiState.update { it.copy(nowPlayingMediaId = mediaId) }
            }
        }
    }

    private fun getSortedSongsFlow(order: SongSortOrder, groupId: Long): Flow<List<Song>> {
        return if (groupId == 0L) {
            when (order) {
                SongSortOrder.ARTIST -> songDao.getLibrarySongsSortedByArtist()
                SongSortOrder.TITLE -> songDao.getLibrarySongsSortedByTitle()
                SongSortOrder.DATE_ADDED -> songDao.getLibrarySongsSortedByDateAdded()
                SongSortOrder.PLAY_COUNT -> songDao.getLibrarySongsSortedByPlayCount()
            }
        } else {
            when (order) {
                SongSortOrder.ARTIST -> songDao.getLibrarySongsSortedByArtist(groupId)
                SongSortOrder.TITLE -> songDao.getLibrarySongsSortedByTitle(groupId)
                SongSortOrder.DATE_ADDED -> songDao.getLibrarySongsSortedByDateAdded(groupId)
                SongSortOrder.PLAY_COUNT -> songDao.getLibrarySongsSortedByPlayCount(groupId)
            }
        }
    }

    fun onEvent(event: SongsTabEvent) {
        when (event) {
            is SongsTabEvent.SetDownloadFilter -> _uiState.update { it.copy(downloadFilter = event.filter) }
            is SongsTabEvent.SetPlaylistFilter -> _uiState.update { it.copy(playlistFilter = event.filter) }
            is SongsTabEvent.SetGroupingFilter -> _uiState.update { it.copy(groupingFilter = event.filter) }
            is SongsTabEvent.SetSortOrder -> setSortOrder(event.order)
            is SongsTabEvent.SongSelected -> onSongSelected(event.index)
            is SongsTabEvent.ShuffleFilteredSongs -> shuffleFilteredSongs()
            is SongsTabEvent.DownloadSong -> viewModelScope.launch { libraryRepository.startDownload(event.song) }
            is SongsTabEvent.DeleteSongDownload -> deleteSongDownload(event.song)
            is SongsTabEvent.SetItemForDeletion -> _uiState.update { it.copy(itemPendingDeletion = event.song) }
            is SongsTabEvent.ClearItemForDeletion -> _uiState.update { it.copy(itemPendingDeletion = null) }
            is SongsTabEvent.ConfirmDeletion -> confirmDeletion()
            is SongsTabEvent.AddToPlaylist -> playlistActionsManager.selectItem(event.song)
            is SongsTabEvent.PlaySongNext -> musicServiceConnection.playNext(event.song)
            is SongsTabEvent.AddSongToQueue -> musicServiceConnection.addToQueue(event.song)
            is SongsTabEvent.ShuffleSong -> onShuffleSong(event.song)
            is SongsTabEvent.GoToArtist -> onGoToArtist(event.song)
            is SongsTabEvent.AddToLibrary -> libraryActionsManager.addToLibrary(event.song)
        }
    }

    fun onDialogCreateGroup(name: String) = libraryActionsManager.onCreateGroup(name)
    fun onDialogGroupSelected(groupId: Long) = libraryActionsManager.onGroupSelected(groupId)
    fun onDialogResolveConflict() = libraryActionsManager.onResolveConflict()
    fun onDialogDismiss() = libraryActionsManager.dismissDialog()
    fun onDialogRequestCreateGroup() = libraryActionsManager.requestCreateGroup()

    private fun setSortOrder(order: SongSortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
        preferencesManager.songsSortOrder = order
    }

    private fun onSongSelected(selectedIndex: Int) {
        val songsToPlay = _uiState.value.songs
        if (songsToPlay.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(songsToPlay, selectedIndex)
            }
        }
    }

    private fun shuffleFilteredSongs() {
        val songsToPlay = _uiState.value.songs
        if (songsToPlay.isNotEmpty()) {
            val finalShuffledList = songsToPlay.shuffled()
            viewModelScope.launch {
                musicServiceConnection.playSongList(finalShuffledList, 0)
            }
        }
    }

    private fun deleteSongDownload(song: Song) {
        viewModelScope.launch {
            val conflict = libraryRepository.checkForAutoDownloadConflict(song)
            if (conflict != null) {
                val message = when (conflict) {
                    is AutoDownloadConflict.Artist -> "Cannot delete download. Auto-download is enabled for artist '${conflict.name}'."
                    is AutoDownloadConflict.Playlist -> "Cannot delete download. Song is in auto-downloading playlist '${conflict.name}'."
                }
                _userMessage.emit(message)
            } else {
                libraryRepository.deleteDownloadedFileForSong(song)
            }
        }
    }

    private fun confirmDeletion() {
        _uiState.value.itemPendingDeletion?.let { song ->
            viewModelScope.launch {
                libraryRepository.deleteSongFromDeviceAndDb(song)
                _uiState.update { it.copy(itemPendingDeletion = null) }
            }
        }
    }

    private fun onShuffleSong(song: Song) {
        val currentSongs = _uiState.value.songs
        val index = currentSongs.indexOf(song)
        if (index != -1) {
            viewModelScope.launch { musicServiceConnection.shuffleSongList(currentSongs, index) }
        }
    }

    private fun onGoToArtist(song: Song) {
        viewModelScope.launch {
            val artist = artistDao.getArtistByName(song.artist)
            artist?.let { _navigateToArtist.emit(it.artistId) }
        }
    }
}

private data class Quintuple<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)