package com.example.m.ui.library.details

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.example.m.data.database.*
import com.example.m.data.repository.LibraryRepository
import com.example.m.managers.PlaylistManager
import com.example.m.managers.ThumbnailProcessor
import com.example.m.playback.MusicServiceConnection
import com.example.m.ui.library.ArtistForList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject

@HiltViewModel
class ArtistGroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val artistGroupDao: ArtistGroupDao,
    private val artistDao: ArtistDao,
    private val musicServiceConnection: MusicServiceConnection,
    val playlistManager: PlaylistManager,
    private val libraryRepository: LibraryRepository,
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
    private val thumbnailProcessor: ThumbnailProcessor
) : ViewModel() {
    private val groupId: Long = checkNotNull(savedStateHandle["groupId"])

    val groupWithArtists: StateFlow<ArtistGroupWithArtists?> =
        artistGroupDao.getGroupWithArtists(groupId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allPlaylists: StateFlow<List<Playlist>> = libraryRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun processThumbnails(urls: List<String>) = thumbnailProcessor.process(urls)

    @OptIn(ExperimentalCoroutinesApi::class)
    val artistsForList: StateFlow<List<ArtistForList>> = groupWithArtists.flatMapLatest { group ->
        val artists = group?.artists ?: emptyList()
        if (artists.isEmpty()) {
            flowOf(emptyList())
        } else {
            flow {
                val artistForListData = coroutineScope {
                    artists.map { artist ->
                        async {
                            val orderedSongs = artistDao.getSongsForArtistSortedByCustom(artist.artistId)
                            ArtistForList(
                                artist = artist,
                                allThumbnailUrls = orderedSongs.map { it.thumbnailUrl }
                            )
                        }
                    }.awaitAll()
                }
                emit(artistForListData)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var itemToAddToPlaylist by mutableStateOf<Any?>(null)
        private set
    var showCreatePlaylistDialog by mutableStateOf(false)
        private set
    private var pendingItem: Any? = null

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
            val songs = artistDao.getSongsForArtistSortedByCustom(artist.artistId).shuffled()
            if (songs.isNotEmpty()) {
                musicServiceConnection.playSongList(songs, 0)
            }
        }
    }

    fun removeArtistFromGroup(artist: Artist) {
        viewModelScope.launch(Dispatchers.IO) {
            artistDao.removeArtistFromGroup(artist.artistId)
        }
    }

    fun hideArtist(artist: Artist) {
        viewModelScope.launch(Dispatchers.IO) {
            artistDao.hideArtist(artist.artistId)
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
}