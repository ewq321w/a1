// file: com/example/m/ui/library/details/ArtistDetailViewModel.kt
package com.example.m.ui.library.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.PreferencesManager
import com.example.m.data.database.*
import com.example.m.data.repository.AutoDownloadConflict
import com.example.m.data.repository.LibraryRepository
import com.example.m.managers.PlaylistManager
import com.example.m.playback.MusicServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ArtistSortOrder {
    CUSTOM, TITLE, DATE_ADDED, PLAY_COUNT
}

data class GroupHeaderData(
    val group: ArtistSongGroup,
    val thumbnailUrls: List<String>,
    val songCount: Int
)

sealed interface ArtistDetailListItem {
    data class GroupHeader(val data: GroupHeaderData) : ArtistDetailListItem
    data class SongItem(val song: Song) : ArtistDetailListItem
}

data class ArtistDetailUiState(
    val artist: Artist? = null,
    val displayList: List<ArtistDetailListItem> = emptyList(),
    val artistSongGroups: List<ArtistSongGroup> = emptyList(),
    val allPlaylists: List<Playlist> = emptyList(),
    val sortOrder: ArtistSortOrder = ArtistSortOrder.CUSTOM,
    val itemPendingDeletion: Any? = null,
    val itemToAddToPlaylist: Any? = null,
    val songToAddToGroup: Song? = null,
    val showCreatePlaylistDialog: Boolean = false,
    val showCreateSongGroupDialog: Boolean = false,
    val pendingItemForPlaylist: Any? = null,
    val pendingSongForNewGroup: Song? = null,
    val groupToRename: ArtistSongGroup? = null,
    val groupToDelete: ArtistSongGroup? = null,
    val showRemoveDownloadsConfirm: Boolean = false
)

sealed interface ArtistDetailEvent {
    data class SongSelected(val song: Song) : ArtistDetailEvent
    data class DownloadSong(val song: Song) : ArtistDetailEvent
    data class DeleteDownload(val song: Song) : ArtistDetailEvent
    object PlayAll : ArtistDetailEvent
    object ShuffleAll : ArtistDetailEvent
    object ShuffleUngrouped : ArtistDetailEvent
    data class PlayGroup(val groupId: Long) : ArtistDetailEvent
    data class ShuffleGroup(val groupId: Long) : ArtistDetailEvent
    object ToggleAutoDownload : ArtistDetailEvent
    object ShowRemoveDownloadsConfirm : ArtistDetailEvent
    object HideRemoveDownloadsConfirm : ArtistDetailEvent
    object ConfirmRemoveDownloads : ArtistDetailEvent
    data class SetSortOrder(val order: ArtistSortOrder) : ArtistDetailEvent
    data class SetItemForDeletion(val song: Song) : ArtistDetailEvent
    object ClearItemForDeletion : ArtistDetailEvent
    object ConfirmDeletion : ArtistDetailEvent
    data class SelectItemForPlaylist(val item: Any) : ArtistDetailEvent
    object DismissAddToPlaylistSheet : ArtistDetailEvent
    data class PlaylistSelectedForAddition(val playlistId: Long) : ArtistDetailEvent
    data class SelectSongToAddToGroup(val song: Song) : ArtistDetailEvent
    object DismissAddToGroupSheet : ArtistDetailEvent
    data class AddSongToGroup(val groupId: Long) : ArtistDetailEvent
    object PrepareToCreatePlaylist : ArtistDetailEvent
    data class CreatePlaylist(val name: String) : ArtistDetailEvent
    object DismissCreatePlaylistDialog : ArtistDetailEvent
    object PrepareToCreateSongGroup : ArtistDetailEvent
    object PrepareToCreateGroupWithSong : ArtistDetailEvent
    object DismissCreateSongGroupDialog : ArtistDetailEvent
    data class CreateGroupAndAddSong(val name: String) : ArtistDetailEvent
    data class PrepareToRenameGroup(val group: ArtistSongGroup) : ArtistDetailEvent
    object CancelRenameGroup : ArtistDetailEvent
    data class ConfirmRenameGroup(val newName: String) : ArtistDetailEvent
    data class PrepareToDeleteGroup(val group: ArtistSongGroup) : ArtistDetailEvent
    object CancelDeleteGroup : ArtistDetailEvent
    object ConfirmDeleteGroup : ArtistDetailEvent
    data class PlayNext(val song: Song) : ArtistDetailEvent
    data class AddToQueue(val song: Song) : ArtistDetailEvent
    data class ShuffleSong(val song: Song) : ArtistDetailEvent
    data class GoToArtist(val song: Song) : ArtistDetailEvent
}


@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val musicServiceConnection: MusicServiceConnection,
    private val songDao: SongDao,
    private val artistDao: ArtistDao,
    private val playlistManager: PlaylistManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    private val artistId: Long = checkNotNull(savedStateHandle["artistId"])

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    private val _navigateToArtist = MutableSharedFlow<Long>()
    val navigateToArtist: SharedFlow<Long> = _navigateToArtist.asSharedFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    init {
        _uiState.update { it.copy(sortOrder = preferencesManager.artistSortOrder) }

        viewModelScope.launch {
            artistDao.getArtistById(artistId).collect { artist ->
                _uiState.update { it.copy(artist = artist) }
            }
        }

        val allSongsForArtistFlow = artistDao.getArtistWithLibrarySongs(artistId).map { it?.songs ?: emptyList() }
        val songGroupsForArtistFlow = artistDao.getAllArtistSongGroupsWithSongsOrdered(artistId)
            .map { map ->
                map.entries.groupBy { it.key.groupId }
                    .map { (_, entries) ->
                        val group = entries.first().key
                        val songs = entries.flatMap { it.value }.filter { it.songId != 0L }.distinctBy { it.songId }
                        ArtistSongGroupWithSongs(group = group, songs = songs)
                    }
            }

        viewModelScope.launch {
            songGroupsForArtistFlow.map { list -> list.map { it.group } }.collect { groups ->
                _uiState.update { it.copy(artistSongGroups = groups) }
            }
        }

        viewModelScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            val displayListFlow = combine(
                allSongsForArtistFlow,
                songGroupsForArtistFlow,
                _uiState.map { it.sortOrder }.distinctUntilChanged()
            ) { allSongs, groups, order ->
                val groupedSongIds = groups.flatMap { it.songs }.map { it.songId }.toSet()
                val ungroupedSongs = allSongs.filter { it.songId !in groupedSongIds }

                val sortedUngrouped = when (order) {
                    ArtistSortOrder.CUSTOM -> ungroupedSongs
                    ArtistSortOrder.TITLE -> ungroupedSongs.sortedBy { it.title }
                    ArtistSortOrder.DATE_ADDED -> ungroupedSongs.sortedBy { it.dateAddedTimestamp }
                    ArtistSortOrder.PLAY_COUNT -> ungroupedSongs.sortedByDescending { it.playCount }
                }.map { song ->
                    ArtistDetailListItem.SongItem(song)
                }

                val groupItems = groups.map { groupWithSongs ->
                    val urls = groupWithSongs.songs.take(4).map { it.thumbnailUrl }
                    val headerData = GroupHeaderData(
                        group = groupWithSongs.group,
                        thumbnailUrls = urls,
                        songCount = groupWithSongs.songs.size
                    )
                    ArtistDetailListItem.GroupHeader(headerData)
                }

                groupItems + sortedUngrouped
            }

            displayListFlow.collect { list ->
                _uiState.update { it.copy(displayList = list) }
            }
        }

        viewModelScope.launch {
            libraryRepository.getAllPlaylists().collect { playlists ->
                _uiState.update { it.copy(allPlaylists = playlists) }
            }
        }
    }

    fun onEvent(event: ArtistDetailEvent) {
        when (event) {
            is ArtistDetailEvent.SongSelected -> onSongSelected(event.song)
            is ArtistDetailEvent.DownloadSong -> playlistManager.startDownload(event.song)
            is ArtistDetailEvent.DeleteDownload -> deleteSongDownload(event.song)
            is ArtistDetailEvent.PlayAll -> playAll()
            is ArtistDetailEvent.ShuffleAll -> shuffleAll()
            is ArtistDetailEvent.ShuffleUngrouped -> shuffleUngrouped()
            is ArtistDetailEvent.PlayGroup -> playGroup(event.groupId)
            is ArtistDetailEvent.ShuffleGroup -> shuffleGroup(event.groupId)
            is ArtistDetailEvent.ToggleAutoDownload -> toggleAutoDownload()
            is ArtistDetailEvent.ShowRemoveDownloadsConfirm -> _uiState.update { it.copy(showRemoveDownloadsConfirm = true) }
            is ArtistDetailEvent.HideRemoveDownloadsConfirm -> _uiState.update { it.copy(showRemoveDownloadsConfirm = false) }
            is ArtistDetailEvent.ConfirmRemoveDownloads -> removeDownloadsForArtist()
            is ArtistDetailEvent.SetSortOrder -> setSortOrder(event.order)
            is ArtistDetailEvent.SetItemForDeletion -> _uiState.update { it.copy(itemPendingDeletion = event.song) }
            is ArtistDetailEvent.ClearItemForDeletion -> _uiState.update { it.copy(itemPendingDeletion = null) }
            is ArtistDetailEvent.ConfirmDeletion -> deleteSong()
            is ArtistDetailEvent.SelectItemForPlaylist -> _uiState.update { it.copy(itemToAddToPlaylist = event.item) }
            is ArtistDetailEvent.DismissAddToPlaylistSheet -> _uiState.update { it.copy(itemToAddToPlaylist = null) }
            is ArtistDetailEvent.PlaylistSelectedForAddition -> onPlaylistSelectedForAddition(event.playlistId)
            is ArtistDetailEvent.SelectSongToAddToGroup -> _uiState.update { it.copy(songToAddToGroup = event.song) }
            is ArtistDetailEvent.DismissAddToGroupSheet -> _uiState.update { it.copy(songToAddToGroup = null) }
            is ArtistDetailEvent.AddSongToGroup -> addSongToGroup(event.groupId)
            is ArtistDetailEvent.PrepareToCreatePlaylist -> _uiState.update { it.copy(showCreatePlaylistDialog = true, pendingItemForPlaylist = it.itemToAddToPlaylist, itemToAddToPlaylist = null) }
            is ArtistDetailEvent.CreatePlaylist -> createPlaylistAndAddPendingItem(event.name)
            is ArtistDetailEvent.DismissCreatePlaylistDialog -> _uiState.update { it.copy(showCreatePlaylistDialog = false, pendingItemForPlaylist = null) }
            is ArtistDetailEvent.PrepareToCreateSongGroup -> _uiState.update { it.copy(showCreateSongGroupDialog = true, pendingSongForNewGroup = null) }
            is ArtistDetailEvent.PrepareToCreateGroupWithSong -> _uiState.update { it.copy(showCreateSongGroupDialog = true, pendingSongForNewGroup = it.songToAddToGroup, songToAddToGroup = null) }
            is ArtistDetailEvent.DismissCreateSongGroupDialog -> _uiState.update { it.copy(showCreateSongGroupDialog = false) }
            is ArtistDetailEvent.CreateGroupAndAddSong -> createGroupAndAddSong(event.name)
            is ArtistDetailEvent.PrepareToRenameGroup -> _uiState.update { it.copy(groupToRename = event.group) }
            is ArtistDetailEvent.CancelRenameGroup -> _uiState.update { it.copy(groupToRename = null) }
            is ArtistDetailEvent.ConfirmRenameGroup -> confirmRenameGroup(event.newName)
            is ArtistDetailEvent.PrepareToDeleteGroup -> _uiState.update { it.copy(groupToDelete = event.group) }
            is ArtistDetailEvent.CancelDeleteGroup -> _uiState.update { it.copy(groupToDelete = null) }
            is ArtistDetailEvent.ConfirmDeleteGroup -> confirmDeleteGroup()
            is ArtistDetailEvent.PlayNext -> musicServiceConnection.playNext(event.song)
            is ArtistDetailEvent.AddToQueue -> musicServiceConnection.addToQueue(event.song)
            is ArtistDetailEvent.ShuffleSong -> onShuffleSong(event.song)
            is ArtistDetailEvent.GoToArtist -> onGoToArtist(event.song)
        }
    }

    private fun onSongSelected(clickedSong: Song) {
        val ungroupedSongs = _uiState.value.displayList
            .filterIsInstance<ArtistDetailListItem.SongItem>()
            .map { it.song }
        val selectedIndex = ungroupedSongs.indexOf(clickedSong)
        if (selectedIndex != -1) {
            viewModelScope.launch { musicServiceConnection.playSongList(ungroupedSongs, selectedIndex) }
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

    private fun playAll() {
        viewModelScope.launch {
            val flatSongList = _uiState.value.displayList
                .mapNotNull { if (it is ArtistDetailListItem.SongItem) it.song else null }
            if (flatSongList.isNotEmpty()) {
                musicServiceConnection.playSongList(flatSongList, 0)
            }
        }
    }

    private fun shuffleAll() {
        val allSongs = _uiState.value.displayList.mapNotNull {
            (it as? ArtistDetailListItem.SongItem)?.song
        }
        if (allSongs.isNotEmpty()) {
            viewModelScope.launch { musicServiceConnection.playSongList(allSongs.shuffled(), 0) }
        }
    }

    private fun shuffleUngrouped() {
        val ungroupedSongs = _uiState.value.displayList
            .filterIsInstance<ArtistDetailListItem.SongItem>()
            .map { it.song }
        if (ungroupedSongs.isNotEmpty()) {
            viewModelScope.launch { musicServiceConnection.playSongList(ungroupedSongs.shuffled(), 0) }
        }
    }

    private fun playGroup(groupId: Long) {
        viewModelScope.launch {
            val songsInGroup = _uiState.value.displayList
                .filterIsInstance<ArtistDetailListItem.GroupHeader>()
                .find { it.data.group.groupId == groupId }
                ?.let { header ->
                    val groupSongsFlow = artistDao.getArtistSongGroupWithSongsOrdered(groupId)
                        .map { map -> map.values.flatten().filter { it.songId != 0L } }
                    groupSongsFlow.firstOrNull()
                }
            if (!songsInGroup.isNullOrEmpty()) {
                musicServiceConnection.playSongList(songsInGroup, 0)
            }
        }
    }

    private fun shuffleGroup(groupId: Long) {
        viewModelScope.launch {
            val songsInGroup = _uiState.value.displayList
                .filterIsInstance<ArtistDetailListItem.GroupHeader>()
                .find { it.data.group.groupId == groupId }
                ?.let {
                    val groupSongsFlow = artistDao.getArtistSongGroupWithSongsOrdered(groupId)
                        .map { map -> map.values.flatten().filter { it.songId != 0L } }
                    groupSongsFlow.firstOrNull()
                }

            if (!songsInGroup.isNullOrEmpty()) {
                musicServiceConnection.playSongList(songsInGroup.shuffled(), 0)
            }
        }
    }

    private fun toggleAutoDownload() {
        _uiState.value.artist?.let { artist ->
            viewModelScope.launch(Dispatchers.IO) {
                val isEnabling = !artist.downloadAutomatically
                artistDao.updateArtist(artist.copy(downloadAutomatically = isEnabling))
                if (isEnabling) {
                    val songsToDownload = _uiState.value.displayList
                        .mapNotNull { (it as? ArtistDetailListItem.SongItem)?.song }
                    songsToDownload.forEach { playlistManager.startDownload(it) }
                }
            }
        }
    }

    private fun removeDownloadsForArtist() {
        _uiState.value.artist?.let { artist ->
            viewModelScope.launch(Dispatchers.IO) {
                libraryRepository.removeDownloadsForArtist(artist)
            }
        }
        _uiState.update { it.copy(showRemoveDownloadsConfirm = false) }
    }

    private fun setSortOrder(order: ArtistSortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
        preferencesManager.artistSortOrder = order
    }

    private fun deleteSong() {
        (_uiState.value.itemPendingDeletion as? Song)?.let { song ->
            viewModelScope.launch {
                libraryRepository.deleteSongFromDeviceAndDb(song)
                _uiState.update { it.copy(itemPendingDeletion = null) }
            }
        }
    }

    private fun onPlaylistSelectedForAddition(playlistId: Long) {
        _uiState.value.itemToAddToPlaylist?.let { item ->
            playlistManager.addItemToPlaylist(playlistId, item)
        }
        _uiState.update { it.copy(itemToAddToPlaylist = null) }
    }

    private fun addSongToGroup(groupId: Long) {
        _uiState.value.songToAddToGroup?.let { song ->
            viewModelScope.launch {
                libraryRepository.addSongToArtistGroup(groupId, song.songId)
            }
        }
        _uiState.update { it.copy(songToAddToGroup = null) }
    }

    private fun createPlaylistAndAddPendingItem(name: String) {
        val item = _uiState.value.pendingItemForPlaylist ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val activeGroupId = _uiState.value.artist?.let { art ->
                songDao.getArtistLibrarySong(art.name)?.libraryGroupId
            }
            if (activeGroupId != null) {
                playlistManager.createPlaylistAndAddItem(name, item, activeGroupId)
            }
        }
        _uiState.update { it.copy(showCreatePlaylistDialog = false, pendingItemForPlaylist = null) }
    }

    private fun createGroupAndAddSong(name: String) {
        viewModelScope.launch {
            val newGroupId = artistDao.insertArtistSongGroup(ArtistSongGroup(artistId = artistId, name = name.trim()))
            _uiState.value.pendingSongForNewGroup?.let { songToAdd ->
                libraryRepository.addSongToArtistGroup(newGroupId, songToAdd.songId)
            }
            _uiState.update { it.copy(pendingSongForNewGroup = null, showCreateSongGroupDialog = false) }
        }
    }

    private fun confirmRenameGroup(newName: String) {
        _uiState.value.groupToRename?.let { group ->
            viewModelScope.launch {
                libraryRepository.renameArtistSongGroup(group, newName)
            }
        }
        _uiState.update { it.copy(groupToRename = null) }
    }

    private fun confirmDeleteGroup() {
        _uiState.value.groupToDelete?.let { group ->
            viewModelScope.launch {
                libraryRepository.deleteArtistSongGroup(group.groupId)
            }
        }
        _uiState.update { it.copy(groupToDelete = null) }
    }

    private fun onShuffleSong(song: Song) {
        viewModelScope.launch {
            val allSongs = _uiState.value.displayList
                .mapNotNull { (it as? ArtistDetailListItem.SongItem)?.song }
            val index = allSongs.indexOf(song)
            if (index != -1) {
                musicServiceConnection.shuffleSongList(allSongs, index)
            }
        }
    }

    private fun onGoToArtist(song: Song) {
        viewModelScope.launch {
            val artist = artistDao.getArtistByName(song.artist)
            artist?.let { _navigateToArtist.emit(it.artistId) }
        }
    }
}