// file: com/example/m/ui/library/LibraryViewModel.kt
package com.example.m.ui.library

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.PreferencesManager
import com.example.m.data.database.*
import com.example.m.data.repository.AutoDownloadConflict
import com.example.m.data.repository.LibraryRepository
import com.example.m.managers.*
import com.example.m.playback.MusicServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import javax.inject.Inject

sealed interface DeletableItem {
    data class DeletableSong(val song: Song) : DeletableItem
    data class DeletablePlaylist(val playlist: PlaylistWithSongs) : DeletableItem
    data class DeletableArtistGroup(val group: ArtistGroup) : DeletableItem
}

enum class SongSortOrder {
    ARTIST,
    TITLE,
    DATE_ADDED,
    PLAY_COUNT
}

enum class DownloadFilter { ALL, DOWNLOADED }
enum class PlaylistFilter { ALL, IN_PLAYLIST }
enum class GroupingFilter { ALL, UNGROUPED }

sealed interface LibraryArtistItem {
    data class ArtistItem(val artistWithSongs: ArtistWithSongs) : LibraryArtistItem
    data class GroupItem(val group: ArtistGroup, val thumbnailUrls: List<String>, val artistCount: Int) : LibraryArtistItem
}

data class LibraryUiState(
    val selectedView: String = "Playlists",
    val downloadFilter: DownloadFilter = DownloadFilter.ALL,
    val playlistFilter: PlaylistFilter = PlaylistFilter.ALL,
    val groupingFilter: GroupingFilter = GroupingFilter.ALL,
    val itemPendingDeletion: DeletableItem? = null,
    val sortOrder: SongSortOrder = SongSortOrder.ARTIST,
    val itemToAddToPlaylist: Any? = null,
    val showCreatePlaylistDialog: Boolean = false,
    val showCreateArtistGroupDialog: Boolean = false,
    val artistToMove: Artist? = null,
    val groupToRename: ArtistGroup? = null,
    val showManageGroupsDialog: Boolean = false,
    val pendingItemForPlaylist: Any? = null,
    val libraryGroups: List<LibraryGroup> = emptyList(),
    val activeLibraryGroupId: Long = 0L,
    val allArtistGroups: List<ArtistGroup> = emptyList(),
    val playlists: List<PlaylistWithSongs> = emptyList(),
    val allPlaylists: List<Playlist> = emptyList(),
    val libraryArtistItems: List<LibraryArtistItem> = emptyList(),
    val songs: List<Song> = emptyList()
)

sealed interface LibraryEvent {
    data class SetSelectedView(val view: String) : LibraryEvent
    data class SetDownloadFilter(val filter: DownloadFilter) : LibraryEvent
    data class SetPlaylistFilter(val filter: PlaylistFilter) : LibraryEvent
    data class SetGroupingFilter(val filter: GroupingFilter) : LibraryEvent
    data class SetSortOrder(val order: SongSortOrder) : LibraryEvent
    data class SongSelected(val index: Int) : LibraryEvent
    object ShuffleFilteredSongs : LibraryEvent
    object ShuffleUngroupedArtists : LibraryEvent
    data class DownloadSong(val song: Song) : LibraryEvent
    data class DeleteSongDownload(val song: Song) : LibraryEvent
    data class SetItemForDeletion(val item: DeletableItem) : LibraryEvent
    object ClearItemForDeletion : LibraryEvent
    object ConfirmDeletion : LibraryEvent
    data class PlayPlaylist(val playlist: PlaylistWithSongs) : LibraryEvent
    data class ShufflePlaylist(val playlist: PlaylistWithSongs) : LibraryEvent
    data class PlayArtistGroup(val group: ArtistGroup) : LibraryEvent
    data class ShuffleArtistGroup(val group: ArtistGroup) : LibraryEvent
    data class ToggleAutoDownloadPlaylist(val playlist: PlaylistWithSongs) : LibraryEvent
    data class RemoveDownloadsForPlaylist(val playlist: PlaylistWithSongs) : LibraryEvent
    data class PlayArtist(val artist: Artist) : LibraryEvent
    data class ShuffleArtist(val artist: Artist) : LibraryEvent
    data class ShuffleUngroupedSongsForArtist(val artist: Artist) : LibraryEvent
    data class ToggleAutoDownloadArtist(val artist: Artist) : LibraryEvent
    data class RemoveDownloadsForArtist(val artist: Artist) : LibraryEvent
    data class HideArtist(val artist: Artist) : LibraryEvent
    data class PrepareToShowPlaylistSheet(val item: Any) : LibraryEvent
    object DismissAddToPlaylistSheet : LibraryEvent
    data class PlaylistSelectedForAddition(val playlistId: Long) : LibraryEvent
    object PrepareToCreateEmptyPlaylist : LibraryEvent
    object PrepareToCreatePlaylistWithSong : LibraryEvent
    data class CreatePlaylist(val name: String) : LibraryEvent
    object DismissCreatePlaylistDialog : LibraryEvent
    object ShowCreateArtistGroupDialog : LibraryEvent
    object DismissCreateArtistGroupDialog : LibraryEvent
    data class CreateArtistGroup(val name: String) : LibraryEvent
    data class PrepareToRenameGroup(val group: ArtistGroup) : LibraryEvent
    object CancelRenameGroup : LibraryEvent
    data class ConfirmRenameGroup(val newName: String) : LibraryEvent
    data class PrepareToMoveArtist(val artist: Artist) : LibraryEvent
    object DismissMoveArtistSheet : LibraryEvent
    data class MoveArtistToGroup(val groupId: Long) : LibraryEvent
    data class PlaySongNext(val song: Song) : LibraryEvent
    data class AddSongToQueue(val song: Song) : LibraryEvent
    data class ShuffleSong(val song: Song) : LibraryEvent
    data class GoToArtist(val song: Song) : LibraryEvent
    data class AddToLibrary(val song: Song) : LibraryEvent
    object ManageGroupsClicked : LibraryEvent
    object ManageGroupsDismissed : LibraryEvent
    data class AddLibraryGroup(val name: String) : LibraryEvent
    data class RenameLibraryGroup(val group: LibraryGroup, val newName: String) : LibraryEvent
    data class DeleteLibraryGroup(val group: LibraryGroup) : LibraryEvent
    data class SetActiveLibraryGroup(val groupId: Long) : LibraryEvent
    object RequestCreateGroup: LibraryEvent
}


@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val libraryRepository: LibraryRepository,
    private val musicServiceConnection: MusicServiceConnection,
    @ApplicationContext private val context: Context,
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao,
    private val artistDao: ArtistDao,
    private val artistGroupDao: ArtistGroupDao,
    private val libraryGroupDao: LibraryGroupDao,
    private val playlistManager: PlaylistManager,
    val thumbnailProcessor: ThumbnailProcessor,
    private val libraryActionsManager: LibraryActionsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    val dialogState: StateFlow<DialogState> = libraryActionsManager.dialogState

    private val _navigateToArtist = MutableSharedFlow<Long>()
    val navigateToArtist: SharedFlow<Long> = _navigateToArtist.asSharedFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    init {
        // Initial state setup
        _uiState.update {
            it.copy(
                selectedView = preferencesManager.lastLibraryView,
                sortOrder = preferencesManager.songsSortOrder
            )
        }

        // Active Library Group Flow
        viewModelScope.launch {
            preferencesManager.getActiveLibraryGroupIdFlow().collect { groupId ->
                _uiState.update { it.copy(activeLibraryGroupId = groupId) }
            }
        }

        // Library Groups Flow
        viewModelScope.launch {
            libraryGroupDao.getAllGroups().collect { groups ->
                _uiState.update { it.copy(libraryGroups = groups) }
            }
        }

        // All Artist Groups Flow
        viewModelScope.launch {
            artistGroupDao.getAllGroups().collect { groups ->
                _uiState.update { it.copy(allArtistGroups = groups) }
            }
        }

        // Combined data flows reacting to filters and group changes
        viewModelScope.launch {
            val activeGroupIdFlow = _uiState.map { it.activeLibraryGroupId }.distinctUntilChanged()

            // Playlists
            activeGroupIdFlow.flatMapLatest { groupId ->
                if (groupId == 0L) libraryRepository.getPlaylistsWithSongs()
                else libraryRepository.getPlaylistsWithSongs(groupId)
            }.onEach { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }.launchIn(viewModelScope)

            // All Playlists (for sheets)
            activeGroupIdFlow.flatMapLatest { groupId ->
                if (groupId == 0L) libraryRepository.getAllPlaylists()
                else libraryRepository.getPlaylistsByGroupId(groupId)
            }.onEach { allPlaylists ->
                _uiState.update { it.copy(allPlaylists = allPlaylists) }
            }.launchIn(viewModelScope)

            // Artists Tab
            activeGroupIdFlow.flatMapLatest { groupId ->
                val artistsFlow = if (groupId == 0L) artistDao.getAllArtistsSortedByCustom()
                else artistDao.getAllArtistsSortedByCustom(groupId)

                combine(artistsFlow, artistGroupDao.getGroupsWithArtists()) { artists, groups ->
                    val artistItems = artists.map { LibraryArtistItem.ArtistItem(it) }
                    val groupItems = coroutineScope {
                        val allArtistIdsInGroups = groups.flatMap { it.artists }.map { it.artistId }
                        val thumbsByArtistId = if (allArtistIdsInGroups.isNotEmpty()) {
                            artistDao.getRepresentativeThumbnailsForArtists(allArtistIdsInGroups).groupBy { it.artistId }
                        } else {
                            emptyMap()
                        }
                        groups.map { groupWithArtists ->
                            val urls = groupWithArtists.artists.mapNotNull { artist ->
                                thumbsByArtistId[artist.artistId]?.firstOrNull()?.thumbnailUrl
                            }.take(4)
                            LibraryArtistItem.GroupItem(
                                group = groupWithArtists.group,
                                thumbnailUrls = urls,
                                artistCount = groupWithArtists.artists.size
                            )
                        }
                    }
                    (artistItems + groupItems)
                }
            }.flowOn(Dispatchers.Default)
                .onEach { items ->
                    _uiState.update { it.copy(libraryArtistItems = items) }
                }.launchIn(viewModelScope)

            // Songs Tab
            combine(
                _uiState.map { it.sortOrder }.distinctUntilChanged(),
                activeGroupIdFlow
            ) { order, groupId -> order to groupId }
                .flatMapLatest { (order, groupId) ->
                    val songsFlow = if (groupId == 0L) {
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
                    val filtersFlow = combine(
                        _uiState.map { it.downloadFilter }.distinctUntilChanged(),
                        _uiState.map { it.playlistFilter }.distinctUntilChanged(),
                        _uiState.map { it.groupingFilter }.distinctUntilChanged()
                    ) { download, playlist, grouping -> Triple(download, playlist, grouping) }

                    combine(
                        songsFlow,
                        libraryRepository.getSongsInPlaylists(),
                        artistDao.getAllSongIdsInGroups(),
                        filtersFlow
                    ) { allSongs, songsInPlaylists, songIdsInGroups, filters ->
                        val (downloadFilter, playlistFilter, groupingFilter) = filters
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
                }.flowOn(Dispatchers.Default)
                .onEach { songs ->
                    _uiState.update { it.copy(songs = songs) }
                }.launchIn(viewModelScope)
        }
    }


    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.SetSelectedView -> setSelectedView(event.view)
            is LibraryEvent.SetDownloadFilter -> _uiState.update { it.copy(downloadFilter = event.filter) }
            is LibraryEvent.SetPlaylistFilter -> _uiState.update { it.copy(playlistFilter = event.filter) }
            is LibraryEvent.SetGroupingFilter -> _uiState.update { it.copy(groupingFilter = event.filter) }
            is LibraryEvent.SetSortOrder -> setSortOrder(event.order)
            is LibraryEvent.SongSelected -> onSongSelected(event.index)
            is LibraryEvent.ShuffleFilteredSongs -> shuffleFilteredSongs()
            is LibraryEvent.ShuffleUngroupedArtists -> shuffleUngroupedArtists()
            is LibraryEvent.DownloadSong -> downloadSong(event.song)
            is LibraryEvent.DeleteSongDownload -> deleteSongDownload(event.song)
            is LibraryEvent.SetItemForDeletion -> _uiState.update { it.copy(itemPendingDeletion = event.item) }
            is LibraryEvent.ClearItemForDeletion -> _uiState.update { it.copy(itemPendingDeletion = null) }
            is LibraryEvent.ConfirmDeletion -> confirmDeletion()
            is LibraryEvent.PlayPlaylist -> playPlaylist(event.playlist)
            is LibraryEvent.ShufflePlaylist -> shufflePlaylist(event.playlist)
            is LibraryEvent.PlayArtistGroup -> playArtistGroup(event.group)
            is LibraryEvent.ShuffleArtistGroup -> shuffleArtistGroup(event.group)
            is LibraryEvent.ToggleAutoDownloadPlaylist -> toggleAutoDownload(event.playlist)
            is LibraryEvent.RemoveDownloadsForPlaylist -> removeDownloadsForPlaylist(event.playlist)
            is LibraryEvent.PlayArtist -> playArtist(event.artist)
            is LibraryEvent.ShuffleArtist -> shuffleArtist(event.artist)
            is LibraryEvent.ShuffleUngroupedSongsForArtist -> shuffleUngroupedSongsForArtist(event.artist)
            is LibraryEvent.ToggleAutoDownloadArtist -> toggleAutoDownloadForArtist(event.artist)
            is LibraryEvent.RemoveDownloadsForArtist -> removeDownloadsForArtist(event.artist)
            is LibraryEvent.HideArtist -> hideArtist(event.artist)
            is LibraryEvent.PrepareToShowPlaylistSheet -> _uiState.update { it.copy(itemToAddToPlaylist = event.item) }
            is LibraryEvent.DismissAddToPlaylistSheet -> _uiState.update { it.copy(itemToAddToPlaylist = null) }
            is LibraryEvent.PlaylistSelectedForAddition -> onPlaylistSelectedForAddition(event.playlistId)
            is LibraryEvent.PrepareToCreateEmptyPlaylist -> _uiState.update { it.copy(showCreatePlaylistDialog = true, pendingItemForPlaylist = null) }
            is LibraryEvent.PrepareToCreatePlaylistWithSong -> _uiState.update { it.copy(showCreatePlaylistDialog = true, pendingItemForPlaylist = _uiState.value.itemToAddToPlaylist, itemToAddToPlaylist = null) }
            is LibraryEvent.CreatePlaylist -> handlePlaylistCreation(event.name)
            is LibraryEvent.DismissCreatePlaylistDialog -> _uiState.update { it.copy(showCreatePlaylistDialog = false, pendingItemForPlaylist = null) }
            is LibraryEvent.ShowCreateArtistGroupDialog -> _uiState.update { it.copy(showCreateArtistGroupDialog = true) }
            is LibraryEvent.DismissCreateArtistGroupDialog -> _uiState.update { it.copy(showCreateArtistGroupDialog = false) }
            is LibraryEvent.CreateArtistGroup -> createArtistGroup(event.name)
            is LibraryEvent.PrepareToRenameGroup -> _uiState.update { it.copy(groupToRename = event.group) }
            is LibraryEvent.CancelRenameGroup -> _uiState.update { it.copy(groupToRename = null) }
            is LibraryEvent.ConfirmRenameGroup -> confirmRenameGroup(event.newName)
            is LibraryEvent.PrepareToMoveArtist -> _uiState.update { it.copy(artistToMove = event.artist) }
            is LibraryEvent.DismissMoveArtistSheet -> _uiState.update { it.copy(artistToMove = null) }
            is LibraryEvent.MoveArtistToGroup -> moveArtistToGroup(event.groupId)
            is LibraryEvent.PlaySongNext -> musicServiceConnection.playNext(event.song)
            is LibraryEvent.AddSongToQueue -> musicServiceConnection.addToQueue(event.song)
            is LibraryEvent.ShuffleSong -> onShuffleSong(event.song)
            is LibraryEvent.GoToArtist -> onGoToArtist(event.song)
            is LibraryEvent.AddToLibrary -> libraryActionsManager.addToLibrary(event.song)
            is LibraryEvent.ManageGroupsClicked -> _uiState.update { it.copy(showManageGroupsDialog = true) }
            is LibraryEvent.ManageGroupsDismissed -> _uiState.update { it.copy(showManageGroupsDialog = false) }
            is LibraryEvent.AddLibraryGroup -> addLibraryGroup(event.name)
            is LibraryEvent.RenameLibraryGroup -> renameLibraryGroup(event.group, event.newName)
            is LibraryEvent.DeleteLibraryGroup -> deleteLibraryGroup(event.group)
            is LibraryEvent.SetActiveLibraryGroup -> preferencesManager.activeLibraryGroupId = event.groupId
            is LibraryEvent.RequestCreateGroup -> libraryActionsManager.requestCreateGroup()
        }
    }

    private fun downloadSong(song: Song) {
        // Optimistic UI update
        _uiState.update { currentState ->
            val updatedSongs = currentState.songs.map { s ->
                if (s.songId == song.songId) {
                    s.copy(downloadStatus = DownloadStatus.QUEUED)
                } else {
                    s
                }
            }
            currentState.copy(songs = updatedSongs)
        }
        // Then call the manager to do the real work
        playlistManager.startDownload(song)
    }

    fun onDialogCreateGroup(name: String) = libraryActionsManager.onCreateGroup(name)
    fun onDialogGroupSelected(groupId: Long) = libraryActionsManager.onGroupSelected(groupId)
    fun onDialogResolveConflict() = libraryActionsManager.onResolveConflict()
    fun onDialogDismiss() = libraryActionsManager.dismissDialog()

    private fun addLibraryGroup(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryGroupDao.insertGroup(LibraryGroup(name = name.trim()))
        }
    }

    private fun renameLibraryGroup(group: LibraryGroup, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryGroupDao.updateGroup(group.copy(name = newName.trim()))
        }
    }

    private fun deleteLibraryGroup(group: LibraryGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_uiState.value.activeLibraryGroupId == group.groupId) {
                preferencesManager.activeLibraryGroupId = 0L
            }
            libraryRepository.deleteLibraryGroupAndContents(group.groupId)
        }
    }

    private fun setSortOrder(order: SongSortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
        preferencesManager.songsSortOrder = order
    }

    private fun setSelectedView(view: String) {
        _uiState.update { it.copy(selectedView = view) }
        preferencesManager.lastLibraryView = view
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

    private fun shuffleUngroupedArtists() {
        viewModelScope.launch {
            val ungroupedArtists = if (_uiState.value.activeLibraryGroupId == 0L) {
                artistDao.getAllArtistsSortedByCustom().first()
            } else {
                artistDao.getAllArtistsSortedByCustom(_uiState.value.activeLibraryGroupId).first()
            }
            val allUngroupedSongs = ungroupedArtists.flatMap { it.songs }
            if (allUngroupedSongs.isNotEmpty()) {
                musicServiceConnection.playSongList(allUngroupedSongs.shuffled(), 0)
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
        viewModelScope.launch {
            when (val item = _uiState.value.itemPendingDeletion) {
                is DeletableItem.DeletableSong -> libraryRepository.deleteSongFromDeviceAndDb(item.song)
                is DeletableItem.DeletablePlaylist -> libraryRepository.deletePlaylist(item.playlist.playlist.playlistId)
                is DeletableItem.DeletableArtistGroup -> libraryRepository.deleteArtistGroup(item.group.groupId)
                null -> {}
            }
            _uiState.update { it.copy(itemPendingDeletion = null) }
        }
    }

    private fun playPlaylist(playlist: PlaylistWithSongs) {
        viewModelScope.launch {
            musicServiceConnection.playSongList(playlist.songs, 0)
        }
    }

    private fun shufflePlaylist(playlist: PlaylistWithSongs) {
        val songsToPlay = playlist.songs
        if (songsToPlay.isNotEmpty()) {
            val finalShuffledList = songsToPlay.shuffled()
            viewModelScope.launch {
                musicServiceConnection.playSongList(finalShuffledList, 0)
            }
        }
    }

    private fun playArtistGroup(group: ArtistGroup) {
        viewModelScope.launch {
            val allSongs = withContext(Dispatchers.IO) {
                val groupWithArtists = artistGroupDao.getGroupWithArtistsOnce(group.groupId) ?: return@withContext emptyList()
                groupWithArtists.artists.map { artist ->
                    async { artistDao.getSongsForArtistSortedByCustom(artist.artistId) }
                }.awaitAll().flatten()
            }
            if (allSongs.isNotEmpty()) {
                musicServiceConnection.playSongList(allSongs, 0)
            }
        }
    }

    private fun shuffleArtistGroup(group: ArtistGroup) {
        viewModelScope.launch {
            val allSongs = withContext(Dispatchers.IO) {
                val groupWithArtists = artistGroupDao.getGroupWithArtistsOnce(group.groupId) ?: return@withContext emptyList()
                groupWithArtists.artists.map { artist ->
                    async { artistDao.getSongsForArtistSortedByCustom(artist.artistId) }
                }.awaitAll().flatten()
            }
            if (allSongs.isNotEmpty()) {
                musicServiceConnection.playSongList(allSongs.shuffled(), 0)
            }
        }
    }

    private fun toggleAutoDownload(playlistWithSongs: PlaylistWithSongs) {
        val playlist = playlistWithSongs.playlist
        val isEnabling = !playlist.downloadAutomatically
        viewModelScope.launch(Dispatchers.IO) {
            playlistDao.updatePlaylist(playlist.copy(downloadAutomatically = isEnabling))
            if (isEnabling) {
                playlistWithSongs.songs.forEach { song ->
                    playlistManager.startDownload(song)
                }
            }
        }
    }

    private fun removeDownloadsForPlaylist(playlist: PlaylistWithSongs) {
        viewModelScope.launch(Dispatchers.IO) {
            if (playlist.playlist.downloadAutomatically) {
                playlistDao.updatePlaylist(playlist.playlist.copy(downloadAutomatically = false))
            }
            playlist.songs.forEach { song ->
                val artist = artistDao.getArtistByName(song.artist)
                if (artist?.downloadAutomatically == true) return@forEach
                if (song.localFilePath != null) {
                    try {
                        val uri = song.localFilePath!!.toUri()
                        if (uri.scheme == "content") context.contentResolver.delete(uri, null, null)
                        else File(song.localFilePath!!).delete()
                        songDao.updateSong(song.copy(localFilePath = null))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun playArtist(artist: Artist) {
        viewModelScope.launch {
            val songs = artistDao.getSongsForArtistSortedByCustom(artist.artistId)
            if (songs.isNotEmpty()) musicServiceConnection.playSongList(songs, 0)
        }
    }

    private fun shuffleArtist(artist: Artist) {
        viewModelScope.launch {
            val songsToPlay = artistDao.getSongsForArtistSortedByCustom(artist.artistId)
            if (songsToPlay.isNotEmpty()) musicServiceConnection.playSongList(songsToPlay.shuffled(), 0)
        }
    }

    private fun shuffleUngroupedSongsForArtist(artist: Artist) {
        viewModelScope.launch {
            val groupsMap = artistDao.getAllArtistSongGroupsWithSongsOrdered(artist.artistId).first()
            val groupedSongIds = groupsMap.values.flatten().map { it.songId }.toSet()
            val artistWithSongs = artistDao.getArtistWithLibrarySongs(artist.artistId).first()
            val allArtistSongs = artistWithSongs?.songs ?: emptyList()
            val ungroupedSongs = allArtistSongs.filter { it.songId !in groupedSongIds }
            if (ungroupedSongs.isNotEmpty()) musicServiceConnection.playSongList(ungroupedSongs.shuffled(), 0)
        }
    }

    private fun toggleAutoDownloadForArtist(artist: Artist) {
        val isEnabling = !artist.downloadAutomatically
        viewModelScope.launch(Dispatchers.IO) {
            artistDao.updateArtist(artist.copy(downloadAutomatically = isEnabling))
            if (isEnabling) {
                artistDao.getSongsForArtistSortedByCustom(artist.artistId).forEach { song ->
                    playlistManager.startDownload(song)
                }
            }
        }
    }

    private fun removeDownloadsForArtist(artist: Artist) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryRepository.removeDownloadsForArtist(artist)
        }
    }

    private fun hideArtist(artist: Artist) {
        viewModelScope.launch(Dispatchers.IO) {
            artistDao.hideArtist(artist.artistId)
        }
    }

    private fun onPlaylistSelectedForAddition(playlistId: Long) {
        _uiState.value.itemToAddToPlaylist?.let { item ->
            playlistManager.addItemToPlaylist(playlistId, item)
        }
        _uiState.update { it.copy(itemToAddToPlaylist = null) }
    }

    private fun handlePlaylistCreation(name: String) {
        viewModelScope.launch {
            val activeGroupId = _uiState.value.activeLibraryGroupId
            if (activeGroupId == 0L) {
                _userMessage.emit("Please select a specific library group to create a playlist in.")
                return@launch
            }
            val item = _uiState.value.pendingItemForPlaylist
            if (item != null) {
                val song = playlistManager.getSongForItem(item, activeGroupId)
                if (!song.isInLibrary) libraryActionsManager.addToLibrary(song)
                playlistManager.createPlaylistAndAddItem(name, song, activeGroupId)
            } else {
                playlistManager.createEmptyPlaylist(name, activeGroupId)
            }
            _uiState.update { it.copy(showCreatePlaylistDialog = false, pendingItemForPlaylist = null) }
        }
    }

    private fun createArtistGroup(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            artistGroupDao.insertGroup(ArtistGroup(name = name.trim()))
        }
        _uiState.update { it.copy(showCreateArtistGroupDialog = false) }
    }

    private fun confirmRenameGroup(newName: String) {
        _uiState.value.groupToRename?.let { group ->
            viewModelScope.launch(Dispatchers.IO) {
                artistGroupDao.updateGroup(group.copy(name = newName.trim()))
            }
        }
        _uiState.update { it.copy(groupToRename = null) }
    }

    private fun moveArtistToGroup(groupId: Long) {
        _uiState.value.artistToMove?.let { artist ->
            viewModelScope.launch(Dispatchers.IO) {
                artistDao.moveArtistToGroup(artist.artistId, groupId)
            }
        }
        _uiState.update { it.copy(artistToMove = null) }
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