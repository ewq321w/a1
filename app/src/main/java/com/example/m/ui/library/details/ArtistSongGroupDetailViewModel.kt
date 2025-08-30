package com.example.m.ui.library.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.database.*
import com.example.m.data.repository.LibraryRepository
import com.example.m.managers.DownloadStatusManager
import com.example.m.managers.PlaylistManager
import com.example.m.playback.MusicServiceConnection
import com.example.m.ui.library.SongForList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject

@HiltViewModel
class ArtistSongGroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicServiceConnection: MusicServiceConnection,
    private val artistDao: ArtistDao,
    private val downloadStatusManager: DownloadStatusManager,
    private val libraryRepository: LibraryRepository,
    private val playlistManager: PlaylistManager
) : ViewModel() {
    private val groupId: Long = checkNotNull(savedStateHandle["groupId"])

    val groupWithSongs: StateFlow<ArtistSongGroupWithSongs?> =
        artistDao.getArtistSongGroupWithSongsOrdered(groupId)
            .map { map ->
                map.entries.firstOrNull()?.let { entry ->
                    ArtistSongGroupWithSongs(
                        group = entry.key,
                        songs = entry.value.filter { it.songId != 0L }
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val songs: StateFlow<List<SongForList>> = combine(
        groupWithSongs,
        downloadStatusManager.statuses
    ) { groupWithSongs, statuses ->
        val songList = groupWithSongs?.songs ?: emptyList()
        // Songs in a group are currently ordered by their custom position from the DB
        songList.map { song ->
            SongForList(song, statuses[song.youtubeUrl])
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlaylists: StateFlow<List<Playlist>> = libraryRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _navigateToArtist = MutableSharedFlow<Long>()
    val navigateToArtist: SharedFlow<Long> = _navigateToArtist.asSharedFlow()

    var itemToAddToPlaylist by mutableStateOf<Any?>(null)
        private set
    var showCreatePlaylistDialog by mutableStateOf(false)
        private set
    private var pendingItem: Any? = null
    var groupToRename by mutableStateOf<ArtistSongGroup?>(null)
        private set
    var groupToDelete by mutableStateOf<ArtistSongGroup?>(null)
        private set
    var songPendingRemoval by mutableStateOf<Song?>(null)
        private set

    fun onSongSelected(selectedIndex: Int) {
        val currentSongs = songs.value.map { it.song }
        if (currentSongs.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(currentSongs, selectedIndex)
            }
        }
    }

    fun shuffleGroup() {
        val currentSongs = songs.value.map { it.song }
        if (currentSongs.isNotEmpty()) {
            val (downloaded, remote) = currentSongs.partition { it.localFilePath != null }
            val finalShuffledList = downloaded.shuffled() + remote.shuffled()
            viewModelScope.launch {
                musicServiceConnection.playSongList(finalShuffledList, 0)
            }
        }
    }

    fun prepareToRemoveSongFromGroup(song: Song) {
        songPendingRemoval = song
    }

    fun confirmRemoveSongFromGroup() {
        songPendingRemoval?.let { song ->
            viewModelScope.launch {
                libraryRepository.removeSongFromArtistGroup(groupId, song.songId)
            }
        }
        songPendingRemoval = null
    }

    fun cancelRemoveSongFromGroup() {
        songPendingRemoval = null
    }

    fun onPlayNext(song: Song) {
        musicServiceConnection.playNext(song)
    }

    fun onAddToQueue(song: Song) {
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

    fun prepareToRenameGroup() {
        groupToRename = groupWithSongs.value?.group
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

    fun prepareToDeleteGroup() {
        groupToDelete = groupWithSongs.value?.group
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
}