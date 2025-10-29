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
import com.example.m.managers.SnackbarManager
import com.example.m.playback.MusicServiceConnection
import com.example.m.ui.library.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SongsUiState(
    val songs: List<Song> = emptyList(),
    val filters: SongFilters = SongFilters(),
    val itemPendingDeletion: Song? = null,
    val sortOrder: SongSortOrder = SongSortOrder.ARTIST,
    val nowPlayingMediaId: String? = null
)

sealed interface SongsTabEvent {
    object ToggleIncludeNonLocal : SongsTabEvent
    object ToggleIncludeGroupedSongs : SongsTabEvent
    object ToggleIncludeGroupedArtists : SongsTabEvent
    object ToggleIncludeHiddenArtists : SongsTabEvent
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
    private val playlistActionsManager: PlaylistActionsManager,
    private val snackbarManager: SnackbarManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SongsUiState())
    val uiState: StateFlow<SongsUiState> = _uiState.asStateFlow()

    val dialogState: StateFlow<DialogState> = libraryActionsManager.dialogState
    val playlistActionState: StateFlow<PlaylistActionState> = playlistActionsManager.state

    private val _navigateToArtist = MutableSharedFlow<Long>()
    val navigateToArtist: SharedFlow<Long> = _navigateToArtist.asSharedFlow()

    init {
        // Load saved preferences
        _uiState.update {
            it.copy(
                sortOrder = preferencesManager.songsSortOrder,
                filters = SongFilters(
                    includeNonLocal = preferencesManager.includeNonLocal,
                    includeGroupedSongs = preferencesManager.includeGroupedSongs,
                    includeGroupedArtists = preferencesManager.includeGroupedArtists,
                    includeHiddenArtists = preferencesManager.includeHiddenArtists
                )
            )
        }

        combine(
            _uiState.map { it.sortOrder }.distinctUntilChanged(),
            preferencesManager.getActiveLibraryGroupIdFlow(),
            _uiState.map { it.filters }.distinctUntilChanged()
        ) { sortOrder, groupId, filters ->
            Triple(sortOrder, groupId, filters)
        }.flatMapLatest { (order, groupId, filters) ->
            val songsFlow = getSortedSongsFlow(order, groupId)
            combine(
                songsFlow,
                artistDao.getAllSongIdsInGroups(),
                artistDao.getHiddenArtists(),
                artistDao.getArtistNamesInGroups()
            ) { allSongs, songIdsInGroups, hiddenArtists, artistNamesInGroups ->
                val songIdsInGroupsSet = songIdsInGroups.toSet()
                val hiddenArtistNames = hiddenArtists.map { it.name }.toSet()
                val artistsInGroupsNames = artistNamesInGroups.toSet()

                allSongs.filter { song ->
                    val localMatch = if (filters.includeNonLocal) {
                        true // Include all songs
                    } else {
                        song.localFilePath != null // Only include downloaded songs
                    }

                    val groupedSongsMatch = if (filters.includeGroupedSongs) {
                        true // Include all songs
                    } else {
                        !songIdsInGroupsSet.contains(song.songId) // Exclude songs in groups
                    }

                    val groupedArtistsMatch = if (filters.includeGroupedArtists) {
                        true // Include all songs
                    } else {
                        !artistsInGroupsNames.contains(song.artist) // Exclude songs from artists in groups
                    }

                    val hiddenArtistsMatch = if (filters.includeHiddenArtists) {
                        true // Include all songs
                    } else {
                        !hiddenArtistNames.contains(song.artist) // Exclude songs from hidden artists
                    }

                    localMatch && groupedSongsMatch && groupedArtistsMatch && hiddenArtistsMatch
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
            is SongsTabEvent.ToggleIncludeNonLocal -> {
                val newValue = !_uiState.value.filters.includeNonLocal
                _uiState.update { it.copy(filters = it.filters.copy(includeNonLocal = newValue)) }
                preferencesManager.includeNonLocal = newValue
            }
            is SongsTabEvent.ToggleIncludeGroupedSongs -> {
                val newValue = !_uiState.value.filters.includeGroupedSongs
                _uiState.update { it.copy(filters = it.filters.copy(includeGroupedSongs = newValue)) }
                preferencesManager.includeGroupedSongs = newValue
            }
            is SongsTabEvent.ToggleIncludeGroupedArtists -> {
                val newValue = !_uiState.value.filters.includeGroupedArtists
                _uiState.update { it.copy(filters = it.filters.copy(includeGroupedArtists = newValue)) }
                preferencesManager.includeGroupedArtists = newValue
            }
            is SongsTabEvent.ToggleIncludeHiddenArtists -> {
                val newValue = !_uiState.value.filters.includeHiddenArtists
                _uiState.update { it.copy(filters = it.filters.copy(includeHiddenArtists = newValue)) }
                preferencesManager.includeHiddenArtists = newValue
            }
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
                snackbarManager.showMessage(message)
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

