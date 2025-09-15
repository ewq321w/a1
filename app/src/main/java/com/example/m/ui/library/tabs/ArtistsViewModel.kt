// file: com/example/m/ui/library/tabs/ArtistsViewModel.kt
package com.example.m.ui.library.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.PreferencesManager
import com.example.m.data.database.*
import com.example.m.data.repository.LibraryRepository
import com.example.m.managers.PlaylistActionState
import com.example.m.managers.PlaylistActionsManager
import com.example.m.managers.PlaylistManager
import com.example.m.managers.ThumbnailProcessor
import com.example.m.playback.MusicServiceConnection
import com.example.m.ui.library.DeletableItem
import com.example.m.ui.library.LibraryArtistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ArtistsUiState(
    val libraryArtistItems: List<LibraryArtistItem> = emptyList(),
    val itemPendingDeletion: DeletableItem.DeletableArtistGroup? = null,
    val artistPendingDisableAutoDownload: Artist? = null,
    val showCreateArtistGroupDialog: Boolean = false,
    val groupToRename: ArtistGroup? = null,
    val artistToMove: Artist? = null,
    val allArtistGroups: List<ArtistGroup> = emptyList()
)

sealed interface ArtistTabEvent {
    object ShuffleUngroupedArtists : ArtistTabEvent
    data class PlayArtistGroup(val group: ArtistGroup) : ArtistTabEvent
    data class ShuffleArtistGroup(val group: ArtistGroup) : ArtistTabEvent
    data class PlayArtist(val artist: Artist) : ArtistTabEvent
    data class ShuffleArtist(val artist: Artist) : ArtistTabEvent
    data class ShuffleUngroupedSongsForArtist(val artist: Artist) : ArtistTabEvent
    data class PrepareToToggleAutoDownloadArtist(val artist: Artist) : ArtistTabEvent
    object DismissDisableAutoDownloadDialog : ArtistTabEvent
    data class DisableAutoDownloadForArtist(val removeFiles: Boolean) : ArtistTabEvent
    data class HideArtist(val artist: Artist) : ArtistTabEvent
    data class AddToPlaylist(val item: Any) : ArtistTabEvent
    object ShowCreateArtistGroupDialog : ArtistTabEvent
    object DismissCreateArtistGroupDialog : ArtistTabEvent
    data class CreateArtistGroup(val name: String) : ArtistTabEvent
    data class PrepareToRenameGroup(val group: ArtistGroup) : ArtistTabEvent
    object CancelRenameGroup : ArtistTabEvent
    data class ConfirmRenameGroup(val newName: String) : ArtistTabEvent
    data class PrepareToMoveArtist(val artist: Artist) : ArtistTabEvent
    object DismissMoveArtistSheet : ArtistTabEvent
    data class MoveArtistToGroup(val groupId: Long) : ArtistTabEvent
    data class GoToArtist(val song: Song) : ArtistTabEvent
    data class SetItemForDeletion(val group: ArtistGroup) : ArtistTabEvent
    object ClearItemForDeletion : ArtistTabEvent
    object ConfirmDeletion : ArtistTabEvent
}

@HiltViewModel
class ArtistsViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val musicServiceConnection: MusicServiceConnection,
    private val playlistManager: PlaylistManager,
    private val artistDao: ArtistDao,
    private val artistGroupDao: ArtistGroupDao,
    private val playlistActionsManager: PlaylistActionsManager,
    private val preferencesManager: PreferencesManager,
    val thumbnailProcessor: ThumbnailProcessor
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistsUiState())
    val uiState: StateFlow<ArtistsUiState> = _uiState.asStateFlow()

    val playlistActionState: StateFlow<PlaylistActionState> = playlistActionsManager.state

    private val _navigateToArtist = MutableSharedFlow<Long>()
    val navigateToArtist: SharedFlow<Long> = _navigateToArtist.asSharedFlow()

    init {
        preferencesManager.getActiveLibraryGroupIdFlow()
            .flatMapLatest { groupId ->
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
            }
            .flowOn(Dispatchers.Default)
            .onEach { items ->
                _uiState.update { it.copy(libraryArtistItems = items) }
            }
            .launchIn(viewModelScope)

        artistGroupDao.getAllGroups().onEach { groups ->
            _uiState.update { it.copy(allArtistGroups = groups) }
        }.launchIn(viewModelScope)
    }

    fun onEvent(event: ArtistTabEvent) {
        when(event) {
            is ArtistTabEvent.ShuffleUngroupedArtists -> shuffleUngroupedArtists()
            is ArtistTabEvent.PlayArtistGroup -> playArtistGroup(event.group)
            is ArtistTabEvent.ShuffleArtistGroup -> shuffleArtistGroup(event.group)
            is ArtistTabEvent.PlayArtist -> playArtist(event.artist)
            is ArtistTabEvent.ShuffleArtist -> shuffleArtist(event.artist)
            is ArtistTabEvent.ShuffleUngroupedSongsForArtist -> shuffleUngroupedSongsForArtist(event.artist)
            is ArtistTabEvent.PrepareToToggleAutoDownloadArtist -> prepareToToggleAutoDownload(event.artist)
            is ArtistTabEvent.DismissDisableAutoDownloadDialog -> _uiState.update { it.copy(artistPendingDisableAutoDownload = null) }
            is ArtistTabEvent.DisableAutoDownloadForArtist -> disableAutoDownload(event.removeFiles)
            is ArtistTabEvent.HideArtist -> hideArtist(event.artist)
            is ArtistTabEvent.AddToPlaylist -> playlistActionsManager.selectItem(event.item)
            is ArtistTabEvent.ShowCreateArtistGroupDialog -> _uiState.update { it.copy(showCreateArtistGroupDialog = true) }
            is ArtistTabEvent.DismissCreateArtistGroupDialog -> _uiState.update { it.copy(showCreateArtistGroupDialog = false) }
            is ArtistTabEvent.CreateArtistGroup -> createArtistGroup(event.name)
            is ArtistTabEvent.PrepareToRenameGroup -> _uiState.update { it.copy(groupToRename = event.group) }
            is ArtistTabEvent.CancelRenameGroup -> _uiState.update { it.copy(groupToRename = null) }
            is ArtistTabEvent.ConfirmRenameGroup -> confirmRenameGroup(event.newName)
            is ArtistTabEvent.PrepareToMoveArtist -> _uiState.update { it.copy(artistToMove = event.artist) }
            is ArtistTabEvent.DismissMoveArtistSheet -> _uiState.update { it.copy(artistToMove = null) }
            is ArtistTabEvent.MoveArtistToGroup -> moveArtistToGroup(event.groupId)
            is ArtistTabEvent.GoToArtist -> onGoToArtist(event.song)
            is ArtistTabEvent.SetItemForDeletion -> _uiState.update { it.copy(itemPendingDeletion = DeletableItem.DeletableArtistGroup(event.group)) }
            is ArtistTabEvent.ClearItemForDeletion -> _uiState.update { it.copy(itemPendingDeletion = null) }
            is ArtistTabEvent.ConfirmDeletion -> confirmDeletion()
        }
    }

    fun onPlaylistSelected(playlistId: Long) = playlistActionsManager.onPlaylistSelected(playlistId)
    fun onPlaylistActionDismiss() = playlistActionsManager.dismiss()
    fun onPrepareToCreatePlaylist() = playlistActionsManager.prepareToCreatePlaylist()
    fun onCreatePlaylist(name: String) = playlistActionsManager.onCreatePlaylist(name)
    fun onGroupSelectedForNewPlaylist(groupId: Long) = playlistActionsManager.onGroupSelectedForNewPlaylist(groupId)

    private fun shuffleUngroupedArtists() {
        viewModelScope.launch {
            val ungroupedArtists = _uiState.value.libraryArtistItems
                .filterIsInstance<LibraryArtistItem.ArtistItem>()
                .map { it.artistWithSongs }

            val allUngroupedSongs = ungroupedArtists.flatMap { it.songs }
            if (allUngroupedSongs.isNotEmpty()) {
                musicServiceConnection.playSongList(allUngroupedSongs.shuffled(), 0)
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

    private fun prepareToToggleAutoDownload(artist: Artist) {
        if (artist.downloadAutomatically) {
            _uiState.update { it.copy(artistPendingDisableAutoDownload = artist) }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                artistDao.updateArtist(artist.copy(downloadAutomatically = true))
                artistDao.getSongsForArtistSortedByCustom(artist.artistId).forEach { song ->
                    libraryRepository.startDownload(song)
                }
            }
        }
    }

    private fun disableAutoDownload(removeFiles: Boolean) {
        val artist = _uiState.value.artistPendingDisableAutoDownload ?: return
        viewModelScope.launch(Dispatchers.IO) {
            artistDao.updateArtist(artist.copy(downloadAutomatically = false))
            if (removeFiles) {
                libraryRepository.removeDownloadsForArtist(artist)
            }
            _uiState.update { it.copy(artistPendingDisableAutoDownload = null) }
        }
    }

    private fun hideArtist(artist: Artist) {
        viewModelScope.launch(Dispatchers.IO) {
            artistDao.hideArtist(artist.artistId)
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

    private fun onGoToArtist(song: Song) {
        viewModelScope.launch {
            val artist = artistDao.getArtistByName(song.artist)
            artist?.let { _navigateToArtist.emit(it.artistId) }
        }
    }

    private fun confirmDeletion() {
        _uiState.value.itemPendingDeletion?.let { item ->
            viewModelScope.launch {
                libraryRepository.deleteArtistGroup(item.group.groupId)
                _uiState.update { it.copy(itemPendingDeletion = null) }
            }
        }
    }
}