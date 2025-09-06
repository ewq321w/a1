// file: com/example/m/ui/library/LibraryViewModel.kt
package com.example.m.ui.library

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.PreferencesManager
import com.example.m.data.database.*
import com.example.m.data.repository.ArtistGroupConflict
import com.example.m.data.repository.AutoDownloadConflict
import com.example.m.data.repository.LibraryRepository
import com.example.m.managers.DownloadStatus
import com.example.m.managers.DownloadStatusManager
import com.example.m.managers.PlaylistManager
import com.example.m.managers.ThumbnailProcessor
import com.example.m.playback.MusicServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import javax.inject.Inject

data class SongForList(
    val song: Song,
    val downloadStatus: DownloadStatus?
)

sealed interface DeletableItem {
    data class DeletableSong(val song: Song) : DeletableItem
    data class DeletablePlaylist(val playlist: PlaylistWithSongs) : DeletableItem
    data class DeletableArtistGroup(val group: ArtistGroup) : DeletableItem
}

data class ConflictDialogState(
    val song: Song,
    val targetGroupId: Long,
    val targetGroupName: String,
    val conflict: ArtistGroupConflict
)

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

sealed class PendingAction {
    data class AddToLibrary(val song: Song) : PendingAction()
    data class ShowAddToPlaylistSheet(val item: Any) : PendingAction()
    object CreateEmptyPlaylist : PendingAction()
    data class CreatePlaylistWithSong(val item: Any) : PendingAction()
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
    private val thumbnailProcessor: ThumbnailProcessor,
    private val downloadStatusManager: DownloadStatusManager
) : ViewModel() {

    private val _selectedView = MutableStateFlow(preferencesManager.lastLibraryView)
    val selectedView: StateFlow<String> = _selectedView

    private val _downloadFilter = MutableStateFlow(DownloadFilter.ALL)
    val downloadFilter: StateFlow<DownloadFilter> = _downloadFilter

    private val _playlistFilter = MutableStateFlow(PlaylistFilter.ALL)
    val playlistFilter: StateFlow<PlaylistFilter> = _playlistFilter

    private val _groupingFilter = MutableStateFlow(GroupingFilter.ALL)
    val groupingFilter: StateFlow<GroupingFilter> = _groupingFilter

    val itemPendingDeletion = mutableStateOf<DeletableItem?>(null)

    private val _sortOrder = MutableStateFlow(preferencesManager.songsSortOrder)
    val sortOrder: StateFlow<SongSortOrder> = _sortOrder

    var itemToAddToPlaylist by mutableStateOf<Any?>(null)
        private set
    var showCreatePlaylistDialog by mutableStateOf(false)
    var showCreateArtistGroupDialog by mutableStateOf(false)

    var artistToMove by mutableStateOf<Artist?>(null)
        private set

    var groupToRename by mutableStateOf<ArtistGroup?>(null)
        private set

    var conflictDialogState by mutableStateOf<ConflictDialogState?>(null)
        private set

    var showManageGroupsDialog by mutableStateOf(false)
        private set

    var showCreateGroupDialog by mutableStateOf(false)
        private set
    var showSelectGroupDialog by mutableStateOf(false)
        private set
    private var pendingAction by mutableStateOf<PendingAction?>(null)
    private var groupIdForPendingAction: Long? = null

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()


    fun onManageGroupsClicked() {
        showManageGroupsDialog = true
    }

    fun onManageGroupsDismissed() {
        showManageGroupsDialog = false
    }

    fun addLibraryGroup(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryGroupDao.insertGroup(LibraryGroup(name = name.trim()))
        }
    }

    fun renameLibraryGroup(group: LibraryGroup, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryGroupDao.updateGroup(group.copy(name = newName.trim()))
        }
    }

    fun deleteLibraryGroup(group: LibraryGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            if (activeLibraryGroupId.value == group.groupId) {
                setActiveLibraryGroup(0L)
            }
            libraryRepository.deleteLibraryGroupAndContents(group.groupId)
        }
    }

    val libraryGroups: StateFlow<List<LibraryGroup>> = libraryGroupDao.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeLibraryGroupId: StateFlow<Long> = preferencesManager.getActiveLibraryGroupIdFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), preferencesManager.activeLibraryGroupId)

    fun setActiveLibraryGroup(groupId: Long) {
        preferencesManager.activeLibraryGroupId = groupId
    }

    val allArtistGroups: StateFlow<List<ArtistGroup>> = artistGroupDao.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _navigateToArtist = MutableSharedFlow<Long>()
    val navigateToArtist: SharedFlow<Long> = _navigateToArtist.asSharedFlow()

    suspend fun processThumbnails(urls: List<String>) = thumbnailProcessor.process(urls)

    @OptIn(ExperimentalCoroutinesApi::class)
    val playlists: StateFlow<List<PlaylistWithSongs>> = activeLibraryGroupId.flatMapLatest { groupId ->
        if (groupId == 0L) {
            libraryRepository.getPlaylistsWithSongs()
        } else {
            libraryRepository.getPlaylistsWithSongs(groupId)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    @OptIn(ExperimentalCoroutinesApi::class)
    val allPlaylists: StateFlow<List<Playlist>> = activeLibraryGroupId.flatMapLatest { groupId ->
        if (groupId == 0L) {
            libraryRepository.getAllPlaylists()
        } else {
            libraryRepository.getPlaylistsByGroupId(groupId)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val libraryArtistItems: StateFlow<List<LibraryArtistItem>> = activeLibraryGroupId.flatMapLatest { groupId ->
        val artistsFlow = if (groupId == 0L) {
            artistDao.getAllArtistsSortedByCustom()
        } else {
            artistDao.getAllArtistsSortedByCustom(groupId)
        }

        combine(
            artistsFlow,
            artistGroupDao.getGroupsWithArtists()
        ) { artists, groups ->
            val artistItems = artists.map { LibraryArtistItem.ArtistItem(it) }

            val groupItems = coroutineScope {
                val allArtistIdsInGroups = groups.flatMap { it.artists }.map { it.artistId }
                val thumbsByArtistId = if (allArtistIdsInGroups.isNotEmpty()) {
                    artistDao.getRepresentativeThumbnailsForArtists(allArtistIdsInGroups)
                        .groupBy { it.artistId }
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
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val songs: StateFlow<List<SongForList>> = combine(
        sortOrder,
        activeLibraryGroupId
    ) { order, groupId ->
        order to groupId
    }.flatMapLatest { (order, groupId) ->
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
            _downloadFilter,
            _playlistFilter,
            _groupingFilter
        ) { download, playlist, grouping ->
            Triple(download, playlist, grouping)
        }

        combine(
            songsFlow,
            libraryRepository.getSongsInPlaylists(),
            artistDao.getAllSongIdsInGroups(),
            filtersFlow,
            downloadStatusManager.statuses
        ) { allSongs, songsInPlaylists, songIdsInGroups, filters, statuses ->
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
            }.map { song ->
                SongForList(song, statuses[song.youtubeUrl])
            }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun handleAction(action: PendingAction) {
        viewModelScope.launch {
            pendingAction = action
            if (libraryGroups.value.isEmpty()) {
                showCreateGroupDialog = true
                return@launch
            }
            val activeGroupId = preferencesManager.activeLibraryGroupId
            if (activeGroupId == 0L) {
                showSelectGroupDialog = true
                return@launch
            }
            groupIdForPendingAction = activeGroupId
            proceedWithAction(action, activeGroupId)
        }
    }

    private suspend fun proceedWithAction(action: PendingAction, groupId: Long) {
        when (action) {
            is PendingAction.AddToLibrary -> {
                if (action.song.isInLibrary) return
                proceedWithAddingToLibrary(action.song, groupId)
            }
            is PendingAction.ShowAddToPlaylistSheet -> {
                withContext(Dispatchers.Main) {
                    itemToAddToPlaylist = action.item
                }
            }
            is PendingAction.CreateEmptyPlaylist -> {
                withContext(Dispatchers.Main) {
                    showCreatePlaylistDialog = true
                }
            }
            is PendingAction.CreatePlaylistWithSong -> {
                withContext(Dispatchers.Main) {
                    itemToAddToPlaylist = action.item
                    showCreatePlaylistDialog = true
                }
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

    fun addSongToLibrary(song: Song) {
        handleAction(PendingAction.AddToLibrary(song))
    }

    private suspend fun proceedWithAddingToLibrary(song: Song, targetGroupId: Long) {
        val conflict = libraryRepository.checkArtistGroupConflict(song.artist, targetGroupId)
        if (conflict != null) {
            val targetGroup = libraryGroupDao.getGroup(targetGroupId)
            if (targetGroup != null) {
                withContext(Dispatchers.Main) {
                    conflictDialogState = ConflictDialogState(song, targetGroupId, targetGroup.name, conflict)
                }
            }
        } else {
            addSongToLibraryAndLink(song, targetGroupId)
        }
    }

    fun createGroupAndProceed(groupName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val action = pendingAction ?: return@launch
            val newGroupId = libraryGroupDao.insertGroup(LibraryGroup(name = groupName.trim()))
            groupIdForPendingAction = newGroupId
            withContext(Dispatchers.Main) {
                setActiveLibraryGroup(newGroupId)
            }
            proceedWithAction(action, newGroupId)
        }
        showCreateGroupDialog = false
    }

    fun onGroupSelectedForAddition(groupId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val action = pendingAction ?: return@launch
            groupIdForPendingAction = groupId
            withContext(Dispatchers.Main) {
                setActiveLibraryGroup(groupId)
            }
            proceedWithAction(action, groupId)
        }
        showSelectGroupDialog = false
    }

    fun dismissCreateGroupDialog() {
        showCreateGroupDialog = false
        pendingAction = null
        groupIdForPendingAction = null
    }

    fun dismissSelectGroupDialog() {
        showSelectGroupDialog = false
        pendingAction = null
        groupIdForPendingAction = null
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

    fun setSortOrder(order: SongSortOrder) {
        _sortOrder.value = order
        preferencesManager.songsSortOrder = order
    }

    fun saveCustomArtistOrder(reorderedArtists: List<ArtistWithSongs>) {
        viewModelScope.launch(Dispatchers.IO) {
            reorderedArtists.forEachIndexed { index, artistWithSongs ->
                artistDao.updateArtistPosition(artistWithSongs.artist.artistId, index.toLong())
            }
        }
    }

    fun setSelectedView(view: String) {
        _selectedView.value = view
        preferencesManager.lastLibraryView = view
    }

    fun onSongSelected(selectedIndex: Int) {
        val songsToPlay = songs.value.map { it.song }
        if (songsToPlay.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(songsToPlay, selectedIndex)
            }
        }
    }

    fun shuffleFilteredSongs() {
        val songsToPlay = songs.value.map { it.song }
        if (songsToPlay.isNotEmpty()) {
            val finalShuffledList = songsToPlay.shuffled()
            viewModelScope.launch {
                musicServiceConnection.playSongList(finalShuffledList, 0)
            }
        }
    }

    fun shuffleUngroupedArtists() {
        viewModelScope.launch {
            val ungroupedArtists = if (activeLibraryGroupId.value == 0L) {
                artistDao.getAllArtistsSortedByCustom().first()
            } else {
                artistDao.getAllArtistsSortedByCustom(activeLibraryGroupId.value).first()
            }

            val allUngroupedSongs = ungroupedArtists.flatMap { it.songs }

            if (allUngroupedSongs.isNotEmpty()) {
                musicServiceConnection.playSongList(allUngroupedSongs.shuffled(), 0)
            }
        }
    }

    fun downloadSong(song: Song) {
        playlistManager.startDownload(song)
    }

    fun deleteSongDownload(song: Song) {
        viewModelScope.launch {
            val conflict = libraryRepository.checkForAutoDownloadConflict(song)
            if (conflict != null) {
                val message = when (conflict) {
                    is AutoDownloadConflict.Artist ->
                        "Cannot delete download. Auto-download is enabled for artist '${conflict.name}'."
                    is AutoDownloadConflict.Playlist ->
                        "Cannot delete download. Song is in auto-downloading playlist '${conflict.name}'."
                }
                _userMessage.emit(message)
            } else {
                libraryRepository.deleteDownloadedFileForSong(song)
            }
        }
    }

    fun setDownloadFilter(filter: DownloadFilter) { _downloadFilter.value = filter }
    fun setPlaylistFilter(filter: PlaylistFilter) { _playlistFilter.value = filter }
    fun setGroupingFilter(filter: GroupingFilter) { _groupingFilter.value = filter }


    fun deleteSong(song: Song) {
        viewModelScope.launch {
            libraryRepository.deleteSongFromDeviceAndDb(song)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            libraryRepository.deletePlaylist(playlistId)
        }
    }

    fun deleteArtistGroup(groupId: Long) {
        viewModelScope.launch {
            libraryRepository.deleteArtistGroup(groupId)
        }
    }

    fun playPlaylist(playlist: PlaylistWithSongs) {
        viewModelScope.launch {
            musicServiceConnection.playSongList(playlist.songs, 0)
        }
    }

    fun shufflePlaylist(playlist: PlaylistWithSongs) {
        val songsToPlay = playlist.songs
        if (songsToPlay.isNotEmpty()) {
            val finalShuffledList = songsToPlay.shuffled()
            viewModelScope.launch {
                musicServiceConnection.playSongList(finalShuffledList, 0)
            }
        }
    }

    fun playArtistGroup(group: ArtistGroup) {
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

    fun shuffleArtistGroup(group: ArtistGroup) {
        viewModelScope.launch {
            val allSongs = withContext(Dispatchers.IO) {
                val groupWithArtists = artistGroupDao.getGroupWithArtistsOnce(group.groupId) ?: return@withContext emptyList()
                groupWithArtists.artists.map { artist ->
                    async { artistDao.getSongsForArtistSortedByCustom(artist.artistId) }
                }.awaitAll().flatten()
            }

            if (allSongs.isNotEmpty()) {
                val shuffledList = allSongs.shuffled()
                musicServiceConnection.playSongList(shuffledList, 0)
            }
        }
    }

    fun toggleAutoDownload(playlistWithSongs: PlaylistWithSongs) {
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

    fun removeDownloadsForPlaylist(playlist: PlaylistWithSongs) {
        viewModelScope.launch(Dispatchers.IO) {
            if (playlist.playlist.downloadAutomatically) {
                playlistDao.updatePlaylist(playlist.playlist.copy(downloadAutomatically = false))
            }
            playlist.songs.forEach { song ->
                if (song.localFilePath != null) {
                    try {
                        val uri = song.localFilePath!!.toUri()
                        if (uri.scheme == "content") {
                            context.contentResolver.delete(uri, null, null)
                        } else {
                            File(song.localFilePath!!).delete()
                        }
                        songDao.updateSong(song.copy(localFilePath = null))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun playArtist(artist: Artist) {
        viewModelScope.launch {
            val songs = artistDao.getSongsForArtistSortedByCustom(artist.artistId)
            if (songs.isNotEmpty()) {
                musicServiceConnection.playSongList(songs, 0)
            }
        }
    }

    fun shuffleArtist(artist: Artist) {
        viewModelScope.launch {
            val songsToPlay = artistDao.getSongsForArtistSortedByCustom(artist.artistId)
            if (songsToPlay.isNotEmpty()) {
                val finalShuffledList = songsToPlay.shuffled()
                musicServiceConnection.playSongList(finalShuffledList, 0)
            }
        }
    }

    fun shuffleUngroupedSongsForArtist(artist: Artist) {
        viewModelScope.launch {
            val groupsMap = artistDao.getAllArtistSongGroupsWithSongsOrdered(artist.artistId).first()
            val groupedSongIds = groupsMap.values.flatten().map { it.songId }.toSet()

            val artistWithSongs = artistDao.getArtistWithLibrarySongs(artist.artistId).first()
            val allArtistSongs = artistWithSongs?.songs ?: emptyList()

            val ungroupedSongs = allArtistSongs.filter { it.songId !in groupedSongIds }

            if (ungroupedSongs.isNotEmpty()) {
                musicServiceConnection.playSongList(ungroupedSongs.shuffled(), 0)
            }
        }
    }

    fun toggleAutoDownloadForArtist(artist: Artist) {
        val isEnabling = !artist.downloadAutomatically
        viewModelScope.launch(Dispatchers.IO) {
            artistDao.updateArtist(artist.copy(downloadAutomatically = isEnabling))
            if (isEnabling) {
                val songs = artistDao.getSongsForArtistSortedByCustom(artist.artistId)
                songs.forEach { song ->
                    playlistManager.startDownload(song)
                }
            }
        }
    }

    fun hideArtist(artist: Artist) {
        viewModelScope.launch(Dispatchers.IO) {
            artistDao.hideArtist(artist.artistId)
        }
    }

    fun createArtistGroup(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            artistGroupDao.insertGroup(ArtistGroup(name = name.trim()))
        }
    }

    fun prepareToShowPlaylistSheet(item: Any) {
        handleAction(PendingAction.ShowAddToPlaylistSheet(item))
    }

    fun dismissAddToPlaylistSheet() {
        itemToAddToPlaylist = null
    }

    fun onPlaylistSelectedForAddition(playlistId: Long) {
        itemToAddToPlaylist?.let { item ->
            playlistManager.addItemToPlaylist(playlistId, item)
        }
        dismissAddToPlaylistSheet()
    }

    fun prepareToCreatePlaylistWithSong() {
        val item = itemToAddToPlaylist ?: return
        dismissAddToPlaylistSheet()
        handleAction(PendingAction.CreatePlaylistWithSong(item))
    }

    fun prepareToCreateEmptyPlaylist() {
        handleAction(PendingAction.CreateEmptyPlaylist)
    }

    fun handlePlaylistCreation(name: String) {
        val action = pendingAction ?: return
        val groupId = groupIdForPendingAction ?: return

        viewModelScope.launch {
            when (action) {
                is PendingAction.CreateEmptyPlaylist -> {
                    playlistManager.createEmptyPlaylist(name, groupId)
                }
                is PendingAction.CreatePlaylistWithSong -> {
                    val song = playlistManager.getSongForItem(action.item, groupId)
                    if(!song.isInLibrary) {
                        proceedWithAddingToLibrary(song, groupId)
                    }
                    playlistManager.createPlaylistAndAddItem(name, song, groupId)
                }
                else -> { /* Do nothing for other actions */ }
            }
            dismissCreatePlaylistDialog()
        }
    }

    fun dismissCreatePlaylistDialog() {
        showCreatePlaylistDialog = false
        pendingAction = null
        groupIdForPendingAction = null
    }

    fun dismissCreateArtistGroupDialog() {
        showCreateArtistGroupDialog = false
    }

    fun prepareToRenameGroup(group: ArtistGroup) {
        groupToRename = group
    }

    fun cancelRenameGroup() {
        groupToRename = null
    }

    fun confirmRenameGroup(newName: String) {
        groupToRename?.let { group ->
            viewModelScope.launch(Dispatchers.IO) {
                artistGroupDao.updateGroup(group.copy(name = newName.trim()))
            }
        }
        groupToRename = null
    }

    fun prepareToMoveArtist(artist: Artist) {
        artistToMove = artist
    }

    fun dismissMoveArtistSheet() {
        artistToMove = null
    }

    fun moveArtistToGroup(groupId: Long) {
        artistToMove?.let { artist ->
            viewModelScope.launch(Dispatchers.IO) {
                artistDao.moveArtistToGroup(artist.artistId, groupId)
            }
        }
        dismissMoveArtistSheet()
    }

    fun onPlaySongNext(song: Song) {
        musicServiceConnection.playNext(song)
    }

    fun onAddSongToQueue(song: Song) {
        musicServiceConnection.addToQueue(song)
    }

    fun onShuffleSong(song: Song) {
        val currentSongs = songs.value.map { it.song }
        val index = currentSongs.indexOf(song)
        if (index != -1) {
            viewModelScope.launch {
                musicServiceConnection.shuffleSongList(currentSongs, index)
            }
        }
    }

    fun onGoToArtist(song: Song) {
        viewModelScope.launch {
            val artist = artistDao.getArtistByName(song.artist)
            artist?.let {
                _navigateToArtist.emit(it.artistId)
            }
        }
    }

    fun prepareToCreateGroup() {
        showSelectGroupDialog = false
        showCreateGroupDialog = true
    }
}