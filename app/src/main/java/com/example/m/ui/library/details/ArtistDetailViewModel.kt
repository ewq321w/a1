package com.example.m.ui.library.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.PreferencesManager
import com.example.m.data.database.*
import com.example.m.data.repository.LibraryRepository
import com.example.m.managers.DownloadStatusManager
import com.example.m.managers.PlaylistManager
import com.example.m.managers.ThumbnailProcessor
import com.example.m.playback.MusicServiceConnection
import com.example.m.ui.library.SongForList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfoItem
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
    data class SongItem(val songForList: SongForList) : ArtistDetailListItem
}

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val musicServiceConnection: MusicServiceConnection,
    private val songDao: SongDao,
    private val artistDao: ArtistDao,
    private val playlistManager: PlaylistManager,
    private val preferencesManager: PreferencesManager,
    private val downloadStatusManager: DownloadStatusManager,
    private val thumbnailProcessor: ThumbnailProcessor
) : ViewModel() {
    private val artistId: Long = checkNotNull(savedStateHandle["artistId"])

    private val _sortOrder = MutableStateFlow(preferencesManager.artistSortOrder)
    val sortOrder: StateFlow<ArtistSortOrder> = _sortOrder

    val itemPendingDeletion = mutableStateOf<Any?>(null)

    suspend fun processThumbnails(urls: List<String>) = thumbnailProcessor.process(urls)

    val artist: StateFlow<Artist?> = artistDao.getArtistById(artistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // FIX: Use the new DAO function that fetches only library songs for the artist.
    private val allSongsForArtistFlow: Flow<List<Song>> = artistDao.getArtistWithLibrarySongs(artistId).map { it?.songs ?: emptyList() }

    private val songGroupsForArtistFlow: Flow<List<ArtistSongGroupWithSongs>> = artistDao.getAllArtistSongGroupsWithSongsOrdered(artistId)
        .map { map ->
            map.entries.groupBy { it.key.groupId }
                .map { (_, entries) ->
                    val group = entries.first().key
                    val songs = entries.flatMap { it.value }.filter { it.songId != 0L }.distinctBy { it.songId }
                    ArtistSongGroupWithSongs(group = group, songs = songs)
                }
        }


    val artistSongGroups: StateFlow<List<ArtistSongGroup>> = songGroupsForArtistFlow
        .map { list -> list.map { it.group } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val displayList: StateFlow<List<ArtistDetailListItem>> = combine(
        allSongsForArtistFlow,
        songGroupsForArtistFlow,
        sortOrder,
        downloadStatusManager.statuses
    ) { allSongs, groups, order, statuses ->
        val groupedSongIds = groups.flatMap { it.songs }.map { it.songId }.toSet()
        val ungroupedSongs = allSongs.filter { it.songId !in groupedSongIds }

        val sortedUngrouped = when (order) {
            ArtistSortOrder.CUSTOM -> ungroupedSongs // This assumes allSongs is already sorted by custom
            ArtistSortOrder.TITLE -> ungroupedSongs.sortedBy { it.title }
            ArtistSortOrder.DATE_ADDED -> ungroupedSongs.sortedBy { it.dateAddedTimestamp }
            ArtistSortOrder.PLAY_COUNT -> ungroupedSongs.sortedByDescending { it.playCount }
        }.map { song ->
            ArtistDetailListItem.SongItem(SongForList(song, statuses[song.youtubeUrl]))
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    val allPlaylists: StateFlow<List<Playlist>> = libraryRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var itemToAddToPlaylist by mutableStateOf<Any?>(null)
        private set
    var songToAddToGroup by mutableStateOf<Song?>(null)
        private set
    var showCreatePlaylistDialog by mutableStateOf(false)
        private set
    var showCreateSongGroupDialog by mutableStateOf(false)
        private set
    private var pendingItem: Any? = null
    private var pendingSongForNewGroup: Song? = null

    var groupToRename by mutableStateOf<ArtistSongGroup?>(null)
        private set
    var groupToDelete by mutableStateOf<ArtistSongGroup?>(null)
        private set

    private val _navigateToArtist = MutableSharedFlow<Long>()
    val navigateToArtist: SharedFlow<Long> = _navigateToArtist.asSharedFlow()

    fun onSongSelected(clickedSong: Song) {
        viewModelScope.launch {
            // When a song is selected, we only want to play the list of UNGROUPED songs
            val allSongs = allSongsForArtistFlow.first()
            val groups = songGroupsForArtistFlow.first()
            val groupedSongIds = groups.flatMap { it.songs }.map { it.songId }.toSet()
            val ungroupedSongs = allSongs.filter { it.songId !in groupedSongIds }

            val selectedIndex = ungroupedSongs.indexOf(clickedSong)
            if (selectedIndex != -1) {
                musicServiceConnection.playSongList(ungroupedSongs, selectedIndex)
            }
        }
    }

    fun downloadSong(song: Song) {
        playlistManager.startDownload(song)
    }

    fun playAll() {
        viewModelScope.launch {
            val flatSongList = displayList.first()
                .mapNotNull { if (it is ArtistDetailListItem.SongItem) it.songForList.song else null }
            if (flatSongList.isNotEmpty()) {
                musicServiceConnection.playSongList(flatSongList, 0)
            }
        }
    }

    fun shuffleAll() {
        viewModelScope.launch {
            val allSongs = allSongsForArtistFlow.first()
            if (allSongs.isNotEmpty()) {
                val (downloaded, remote) = allSongs.partition { it.localFilePath != null }
                val finalShuffledList = downloaded.shuffled() + remote.shuffled()
                musicServiceConnection.playSongList(finalShuffledList, 0)
            }
        }
    }

    fun shuffleUngrouped() {
        viewModelScope.launch {
            val allSongs = allSongsForArtistFlow.first()
            val groups = songGroupsForArtistFlow.first()
            val groupedSongIds = groups.flatMap { it.songs }.map { it.songId }.toSet()
            val ungroupedSongs = allSongs.filter { it.songId !in groupedSongIds }

            if (ungroupedSongs.isNotEmpty()) {
                val (downloaded, remote) = ungroupedSongs.partition { it.localFilePath != null }
                val finalShuffledList = downloaded.shuffled() + remote.shuffled()
                musicServiceConnection.playSongList(finalShuffledList, 0)
            }
        }
    }

    fun playGroup(groupId: Long) {
        viewModelScope.launch {
            val groups = songGroupsForArtistFlow.first()
            val songsInGroup = groups.find { it.group.groupId == groupId }?.songs
            if (!songsInGroup.isNullOrEmpty()) {
                musicServiceConnection.playSongList(songsInGroup, 0)
            }
        }
    }

    fun shuffleGroup(groupId: Long) {
        viewModelScope.launch {
            val groups = songGroupsForArtistFlow.first()
            val songsInGroup = groups.find { it.group.groupId == groupId }?.songs
            if (!songsInGroup.isNullOrEmpty()) {
                val (downloaded, remote) = songsInGroup.partition { it.localFilePath != null }
                val finalShuffledList = downloaded.shuffled() + remote.shuffled()
                musicServiceConnection.playSongList(finalShuffledList, 0)
            }
        }
    }


    fun toggleAutoDownload() {
        val artistValue = artist.value ?: return
        val isEnabling = !artistValue.downloadAutomatically
        viewModelScope.launch(Dispatchers.IO) {
            artistDao.updateArtist(artistValue.copy(downloadAutomatically = isEnabling))
            if (isEnabling) {
                val songsToDownload = allSongsForArtistFlow.first()
                songsToDownload.forEach { song ->
                    playlistManager.startDownload(song)
                }
            }
        }
    }

    fun setSortOrder(order: ArtistSortOrder) {
        _sortOrder.value = order
        preferencesManager.artistSortOrder = order
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            libraryRepository.deleteSongFromDeviceAndDb(song)
        }
    }

    fun selectItemForPlaylist(item: Any) {
        if (item is Song || item is StreamInfoItem) {
            itemToAddToPlaylist = item
        }
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

    fun selectSongToAddToGroup(song: Song) {
        songToAddToGroup = song
    }

    fun dismissAddToGroupSheet() {
        songToAddToGroup = null
    }

    fun addSongToGroup(groupId: Long) {
        songToAddToGroup?.let { song ->
            viewModelScope.launch {
                libraryRepository.addSongToArtistGroup(groupId, song.songId)
            }
        }
        dismissAddToGroupSheet()
    }

    fun prepareToCreatePlaylist() {
        pendingItem = itemToAddToPlaylist
        dismissAddToPlaylistSheet()
        showCreatePlaylistDialog = true
    }

    fun createPlaylistAndAddPendingItem(name: String) {
        pendingItem?.let { item ->
            playlistManager.createPlaylistAndAddItem(name, item)
            pendingItem = null
        }
        dismissCreatePlaylistDialog()
    }

    fun dismissCreatePlaylistDialog() {
        showCreatePlaylistDialog = false
    }

    fun prepareToCreateSongGroup() {
        pendingSongForNewGroup = null
        showCreateSongGroupDialog = true
    }

    fun prepareToCreateGroupWithSong() {
        pendingSongForNewGroup = songToAddToGroup
        dismissAddToGroupSheet()
        showCreateSongGroupDialog = true
    }

    fun dismissCreateSongGroupDialog() {
        showCreateSongGroupDialog = false
    }

    fun createGroupAndAddSong(name: String) {
        viewModelScope.launch {
            val newGroupId = artistDao.insertArtistSongGroup(ArtistSongGroup(artistId = artistId, name = name.trim()))
            pendingSongForNewGroup?.let { songToAdd ->
                libraryRepository.addSongToArtistGroup(newGroupId, songToAdd.songId)
            }
            pendingSongForNewGroup = null
            dismissCreateSongGroupDialog()
        }
    }

    fun prepareToRenameGroup(group: ArtistSongGroup) {
        groupToRename = group
    }

    fun cancelRenameGroup() {
        groupToRename = null
    }

    fun confirmRenameGroup(newName: String) {
        groupToRename?.let { group ->
            viewModelScope.launch {
                libraryRepository.renameArtistSongGroup(group, newName)
            }
        }
        groupToRename = null
    }

    fun prepareToDeleteGroup(group: ArtistSongGroup) {
        groupToDelete = group
    }

    fun cancelDeleteGroup() {
        groupToDelete = null
    }

    fun confirmDeleteGroup() {
        groupToDelete?.let { group ->
            viewModelScope.launch {
                libraryRepository.deleteArtistSongGroup(group.groupId)
            }
        }
        groupToDelete = null
    }


    fun onPlaySongNext(song: Song) {
        musicServiceConnection.playNext(song)
    }

    fun onAddSongToQueue(song: Song) {
        musicServiceConnection.addToQueue(song)
    }

    fun onShuffleSong(song: Song) {
        viewModelScope.launch {
            val allSongs = allSongsForArtistFlow.first()
            val index = allSongs.indexOf(song)
            if (index != -1) {
                musicServiceConnection.shuffleSongList(allSongs, index)
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
}