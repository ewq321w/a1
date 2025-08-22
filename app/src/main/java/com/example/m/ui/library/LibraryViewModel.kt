package com.example.m.ui.library

import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ImageRequest
import com.example.m.data.PreferencesManager
import com.example.m.data.database.*
import com.example.m.data.repository.LibraryRepository
import com.example.m.download.DownloadService
import com.example.m.managers.PlaylistManager
import com.example.m.playback.MusicServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject

data class ArtistForList(
    val artist: Artist,
    val finalThumbnailUrls: List<String>
)

data class PlaylistSummary(
    val playlistWithSongs: PlaylistWithSongs,
    val finalThumbnailUrls: List<String>
)

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

sealed interface LibraryArtistItem {
    data class ArtistItem(val artistForList: ArtistForList) : LibraryArtistItem
    data class GroupItem(val group: ArtistGroup, val thumbnailUrls: List<String>) : LibraryArtistItem
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val libraryRepository: LibraryRepository,
    private val musicServiceConnection: MusicServiceConnection,
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao,
    private val artistDao: ArtistDao,
    private val artistGroupDao: ArtistGroupDao,
    private val playlistManager: PlaylistManager
) : ViewModel() {

    private val _selectedView = MutableStateFlow(preferencesManager.lastLibraryView)
    val selectedView: StateFlow<String> = _selectedView

    private val _downloadFilter = MutableStateFlow(DownloadFilter.ALL)
    val downloadFilter: StateFlow<DownloadFilter> = _downloadFilter

    private val _playlistFilter = MutableStateFlow(PlaylistFilter.ALL)
    val playlistFilter: StateFlow<PlaylistFilter> = _playlistFilter

    val itemPendingDeletion = mutableStateOf<DeletableItem?>(null)

    var isDoingMaintenance by mutableStateOf(false)
        private set
    var maintenanceResult by mutableStateOf<String?>(null)
        private set

    private val _sortOrder = MutableStateFlow(preferencesManager.songsSortOrder)
    val sortOrder: StateFlow<SongSortOrder> = _sortOrder

    var itemToAddToPlaylist by mutableStateOf<Any?>(null)
        private set
    var showCreatePlaylistDialog by mutableStateOf(false)
    var showCreateArtistGroupDialog by mutableStateOf(false)
    private var pendingItem: Any? = null

    var artistToMove by mutableStateOf<Artist?>(null)
        private set

    var groupToRename by mutableStateOf<ArtistGroup?>(null)
        private set

    val allArtistGroups: StateFlow<List<ArtistGroup>> = artistGroupDao.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _navigateToArtist = MutableSharedFlow<Long>()
    val navigateToArtist: SharedFlow<Long> = _navigateToArtist.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val playlists: StateFlow<List<PlaylistSummary>> = libraryRepository.getPlaylistsWithSongs()
        .mapLatest { playlistsWithSongs ->
            coroutineScope {
                playlistsWithSongs.map { pws ->
                    async {
                        PlaylistSummary(
                            playlistWithSongs = pws,
                            finalThumbnailUrls = getFinalThumbnails(pws.songs.map { it.thumbnailUrl })
                        )
                    }
                }.awaitAll()
            }
        }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlaylists: StateFlow<List<Playlist>> = libraryRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val libraryArtistItems: StateFlow<List<LibraryArtistItem>> =
        combine(
            artistDao.getAllArtistsSortedByCustom(),
            artistGroupDao.getGroupsWithArtists()
        ) { artistsWithSongs, groupsWithArtists ->

            val artistItems = coroutineScope {
                artistsWithSongs.map { artistWithSongs ->
                    async {
                        val librarySongs = artistWithSongs.songs.filter { it.isInLibrary }
                        val thumbnailUrls = librarySongs.map { it.thumbnailUrl }
                        LibraryArtistItem.ArtistItem(
                            ArtistForList(
                                artist = artistWithSongs.artist,
                                finalThumbnailUrls = getFinalThumbnails(thumbnailUrls)
                            )
                        )
                    }
                }.awaitAll()
            }

            val groupItems = coroutineScope {
                val allArtistIdsInGroups = groupsWithArtists.flatMap { it.artists }.map { it.artistId }
                val songsForArtistsInGroups = if (allArtistIdsInGroups.isNotEmpty()) {
                    artistDao.getSongsGroupedByArtistId(allArtistIdsInGroups).groupBy { it.artistId }
                } else {
                    emptyMap()
                }

                groupsWithArtists.map { groupWithArtists ->
                    async {
                        val urls = groupWithArtists.artists.flatMap { artist ->
                            (songsForArtistsInGroups[artist.artistId] ?: emptyList())
                                .filter { it.song.isInLibrary }
                                .map { it.song.thumbnailUrl }
                        }
                        LibraryArtistItem.GroupItem(
                            group = groupWithArtists.group,
                            thumbnailUrls = getFinalThumbnails(urls)
                        )
                    }
                }.awaitAll()
            }
            (artistItems + groupItems)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val songs: StateFlow<List<Song>> = sortOrder.flatMapLatest { order ->
        val songsFlow = when (order) {
            SongSortOrder.ARTIST -> songDao.getLibrarySongsSortedByArtist()
            SongSortOrder.TITLE -> songDao.getLibrarySongsSortedByTitle()
            SongSortOrder.DATE_ADDED -> songDao.getLibrarySongsSortedByDateAdded()
            SongSortOrder.PLAY_COUNT -> songDao.getLibrarySongsSortedByPlayCount()
        }

        combine(
            songsFlow,
            libraryRepository.getSongsInPlaylists(),
            _downloadFilter,
            _playlistFilter
        ) { allSongs, songsInPlaylists, downloadFilter, playlistFilter ->
            val songsInPlaylistsIds = songsInPlaylists.map { it.songId }.toSet()
            allSongs.filter { song ->
                val downloadMatch = when (downloadFilter) {
                    DownloadFilter.ALL -> true
                    DownloadFilter.DOWNLOADED -> song.localFilePath != null
                }
                val playlistMatch = when (playlistFilter) {
                    PlaylistFilter.ALL -> true
                    PlaylistFilter.IN_PLAYLIST -> songsInPlaylistsIds.contains(song.songId)
                }
                downloadMatch && playlistMatch
            }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            songs.collect { songList ->
                launch(Dispatchers.IO) {
                    songList.forEach { song ->
                        val request = ImageRequest.Builder(context)
                            .data(song.thumbnailUrl)
                            .build()
                        imageLoader.enqueue(request)
                    }
                }
            }
        }
    }

    fun runLibraryMaintenance() {
        viewModelScope.launch {
            isDoingMaintenance = true
            val reQueuedCount: Int
            val cleanedCount: Int
            val fixedLinksCount: Int
            val cleanedArtistsCount: Int
            val cleanedSongsCount: Int

            withContext(Dispatchers.IO) {
                reQueuedCount = libraryRepository.verifyLibraryEntries()
                cleanedCount = libraryRepository.cleanOrphanedFiles()
                fixedLinksCount = libraryRepository.fixMissingArtistLinks()
                cleanedArtistsCount = libraryRepository.cleanOrphanedArtists()
                cleanedSongsCount = libraryRepository.cleanOrphanedSongs()
            }

            val results = listOfNotNull(
                if (reQueuedCount > 0) "$reQueuedCount songs re-queued" else null,
                if (cleanedCount > 0) "$cleanedCount orphaned files deleted" else null,
                if (fixedLinksCount > 0) "$fixedLinksCount artist links fixed" else null,
                if (cleanedArtistsCount > 0) "$cleanedArtistsCount orphaned artists removed" else null,
                if (cleanedSongsCount > 0) "$cleanedSongsCount cached songs removed" else null
            )

            maintenanceResult = if (results.isEmpty()) {
                "Library check complete. No issues found."
            } else {
                "Check complete: ${results.joinToString(", ")}."
            }


            if (reQueuedCount > 0) {
                val intent = Intent(context, DownloadService::class.java).apply {
                    action = DownloadService.ACTION_PROCESS_QUEUE
                }
                context.startService(intent)
            }
            isDoingMaintenance = false
        }
    }

    fun clearMaintenanceResult() {
        maintenanceResult = null
    }

    fun setSortOrder(order: SongSortOrder) {
        _sortOrder.value = order
        preferencesManager.songsSortOrder = order
    }

    fun saveCustomArtistOrder(reorderedArtists: List<ArtistForList>) {
        viewModelScope.launch(Dispatchers.IO) {
            reorderedArtists.forEachIndexed { index, artistForList ->
                artistDao.updateArtistPosition(artistForList.artist.artistId, index.toLong())
            }
        }
    }

    private suspend fun getFinalThumbnails(urls: List<String>): List<String> = coroutineScope {
        if (urls.isEmpty()) return@coroutineScope emptyList()

        val uniqueUrls = urls.filter { it.isNotBlank() }.distinct().take(20)
        if (uniqueUrls.size <= 2) return@coroutineScope uniqueUrls

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

    fun setSelectedView(view: String) {
        _selectedView.value = view
        preferencesManager.lastLibraryView = view
    }

    fun onSongSelected(selectedIndex: Int) {
        val songsToPlay = songs.value
        if (songsToPlay.isNotEmpty()) {
            viewModelScope.launch {
                musicServiceConnection.playSongList(songsToPlay, selectedIndex)
            }
        }
    }

    fun downloadSong(song: Song) {
        viewModelScope.launch {
            val librarySong = playlistManager.getSongForItem(song)
            playlistManager.startDownload(librarySong)
        }
    }

    fun setDownloadFilter(filter: DownloadFilter) { _downloadFilter.value = filter }
    fun setPlaylistFilter(filter: PlaylistFilter) { _playlistFilter.value = filter }

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
        val shuffledSongs = playlist.songs.shuffled()
        viewModelScope.launch {
            musicServiceConnection.playSongList(shuffledSongs, 0)
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
                        val uri = Uri.parse(song.localFilePath)
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
            val songs = artistDao.getSongsForArtistSortedByCustom(artist.artistId)
            if (songs.isNotEmpty()) {
                musicServiceConnection.playSongList(songs.shuffled(), 0)
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

    fun prepareToCreatePlaylistWithSong() {
        pendingItem = itemToAddToPlaylist
        dismissAddToPlaylistSheet()
        showCreatePlaylistDialog = true
    }

    fun prepareToCreateEmptyPlaylist() {
        pendingItem = null
        showCreatePlaylistDialog = true
    }

    fun handlePlaylistCreation(name: String) {
        pendingItem?.let { item ->
            playlistManager.createPlaylistAndAddItem(name, item)
            pendingItem = null
        } ?: run {
            playlistManager.createEmptyPlaylist(name)
        }
        dismissCreatePlaylistDialog()
    }

    fun dismissCreatePlaylistDialog() {
        showCreatePlaylistDialog = false
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
        val currentSongs = songs.value
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
}