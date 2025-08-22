package com.example.m.ui.library.details

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ImageRequest
import com.example.m.data.database.*
import com.example.m.data.repository.LibraryRepository
import com.example.m.managers.PlaylistManager
import com.example.m.playback.MusicServiceConnection
import com.example.m.ui.library.ArtistForList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.nio.ByteBuffer
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
    private val imageLoader: ImageLoader
) : ViewModel() {
    private val groupId: Long = checkNotNull(savedStateHandle["groupId"])

    val groupWithArtists: StateFlow<ArtistGroupWithArtists?> =
        artistGroupDao.getGroupWithArtists(groupId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allPlaylists: StateFlow<List<Playlist>> = libraryRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                            val thumbnailUrls = artistDao.getThumbnailsForArtist(artist.artistId)
                            ArtistForList(
                                artist = artist,
                                finalThumbnailUrls = getFinalThumbnails(thumbnailUrls)
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

    private suspend fun getFinalThumbnails(urls: List<String>): List<String> = coroutineScope {
        if (urls.isEmpty()) return@coroutineScope emptyList()

        val uniqueUrls = urls.filter { it.isNotBlank() }.distinct().take(20)
        if (uniqueUrls.size <= 1) return@coroutineScope uniqueUrls

        val urlToHashMap = uniqueUrls.map { url ->
            async(Dispatchers.IO) {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(50, 50)
                    .build()
                val bitmap = (imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
                val hash = bitmap?.let {
                    val byteBuffer = ByteBuffer.allocate(it.byteCount)
                    it.copyPixelsToBuffer(byteBuffer)
                    byteBuffer.array().contentHashCode()
                }
                url to hash
            }
        }.awaitAll()

        val trulyUniqueUrls = urlToHashMap
            .distinctBy { it.second }
            .map { it.first }

        return@coroutineScope when {
            trulyUniqueUrls.size <= 2 -> trulyUniqueUrls.take(1)
            trulyUniqueUrls.size == 3 -> trulyUniqueUrls + trulyUniqueUrls.first()
            else -> trulyUniqueUrls.take(4)
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