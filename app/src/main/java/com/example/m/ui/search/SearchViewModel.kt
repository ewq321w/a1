// file: com/example/m/ui/search/SearchViewModel.kt
package com.example.m.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.example.m.data.database.*
import com.example.m.data.repository.LibraryRepository
import com.example.m.data.repository.YoutubeRepository
import com.example.m.managers.*
import com.example.m.playback.MusicServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject

enum class SearchCategory {
    SONGS, ALBUMS, ARTISTS, VIDEOS, CHANNELS, PLAYLISTS
}

data class SearchResult(
    val streamInfo: StreamInfoItem,
    val isInLibrary: Boolean = false,
    val isDownloaded: Boolean = false
)

data class SearchResultForList(
    val result: SearchResult,
    val localSong: Song?
)

data class AlbumResult(
    val albumInfo: PlaylistInfoItem
)

data class ArtistResult(
    val artistInfo: ChannelInfoItem
)

data class SearchUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val query: String = "",
    val songs: List<SearchResult> = emptyList(),
    val albums: List<AlbumResult> = emptyList(),
    val playlists: List<AlbumResult> = emptyList(),
    val artists: List<ArtistResult> = emptyList(),
    val videoChannels: List<ArtistResult> = emptyList(),
    val videoStreams: List<SearchResult> = emptyList(),
    val detailedViewCategory: SearchCategory? = null,
    val selectedFilter: String = "music_songs",
    val songsNextPage: Page? = null,
    val albumsNextPage: Page? = null,
    val playlistsNextPage: Page? = null,
    val artistsNextPage: Page? = null,
    val videoStreamsNextPage: Page? = null,
    val videoChannelsNextPage: Page? = null,
    val songsHandler: SearchQueryHandler? = null,
    val albumsHandler: SearchQueryHandler? = null,
    val playlistsHandler: SearchQueryHandler? = null,
    val artistsHandler: SearchQueryHandler? = null,
    val videoStreamsHandler: SearchQueryHandler? = null,
    val videoChannelsHandler: SearchQueryHandler? = null
)

sealed interface SearchEvent {
    data class QueryChange(val query: String) : SearchEvent
    data class FilterChange(val filter: String) : SearchEvent
    object Search : SearchEvent
    object LoadMore : SearchEvent
    data class SongSelected(val index: Int) : SearchEvent
    data class ShowDetailedView(val category: SearchCategory) : SearchEvent
    object HideDetailedView : SearchEvent
    data class AddToLibrary(val result: SearchResult) : SearchEvent
    data class AddToPlaylist(val result: SearchResult) : SearchEvent
    data class PlayNext(val result: SearchResult) : SearchEvent
    data class AddToQueue(val result: SearchResult) : SearchEvent
    data class ShuffleAlbum(val album: AlbumResult) : SearchEvent
    object RequestCreateGroup : SearchEvent
    data class CreateLibraryGroup(val name: String) : SearchEvent
    data class SelectGroup(val groupId: Long) : SearchEvent
    object ResolveConflict : SearchEvent
    object DismissDialog : SearchEvent
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    val imageLoader: ImageLoader,
    private val youtubeRepository: YoutubeRepository,
    private val musicServiceConnection: MusicServiceConnection,
    private val playbackListManager: PlaybackListManager,
    private val libraryActionsManager: LibraryActionsManager,
    private val playlistActionsManager: PlaylistActionsManager,
    private val songDao: SongDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val dialogState: StateFlow<DialogState> = libraryActionsManager.dialogState
    val playlistActionState: StateFlow<PlaylistActionState> = playlistActionsManager.state

    private val allLocalSongs: StateFlow<List<Song>> = songDao.getAllSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val songsWithStatus: StateFlow<List<SearchResultForList>> =
        combine(_uiState.map { it.songs }.distinctUntilChanged(), allLocalSongs) { results, localSongs ->
            val localSongsByUrl = localSongs.associateBy { it.youtubeUrl }
            results.map { result ->
                val normalizedUrl = result.streamInfo.url?.replace("music.youtube.com", "www.youtube.com")
                SearchResultForList(result, localSongsByUrl[normalizedUrl])
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videoStreamsWithStatus: StateFlow<List<SearchResultForList>> =
        combine(_uiState.map { it.videoStreams }.distinctUntilChanged(), allLocalSongs) { results, localSongs ->
            val localSongsByUrl = localSongs.associateBy { it.youtubeUrl }
            results.map { result ->
                val normalizedUrl = result.streamInfo.url?.replace("music.youtube.com", "www.youtube.com")
                SearchResultForList(result, localSongsByUrl[normalizedUrl])
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    init {
        viewModelScope.launch {
            allLocalSongs.drop(1).collect {
                refreshResultStatuses()
            }
        }
    }

    fun onEvent(event: SearchEvent) {
        when(event) {
            is SearchEvent.QueryChange -> _uiState.update { it.copy(query = event.query) }
            is SearchEvent.FilterChange -> onFilterChange(event.filter)
            is SearchEvent.Search -> search()
            is SearchEvent.LoadMore -> loadMoreResults()
            is SearchEvent.SongSelected -> onSongSelected(event.index)
            is SearchEvent.ShowDetailedView -> _uiState.update { it.copy(detailedViewCategory = event.category) }
            is SearchEvent.HideDetailedView -> _uiState.update { it.copy(detailedViewCategory = null) }
            is SearchEvent.AddToLibrary -> libraryActionsManager.addToLibrary(event.result.streamInfo)
            is SearchEvent.AddToPlaylist -> playlistActionsManager.selectItem(event.result.streamInfo)
            is SearchEvent.PlayNext -> musicServiceConnection.playNext(event.result.streamInfo)
            is SearchEvent.AddToQueue -> musicServiceConnection.addToQueue(event.result.streamInfo)
            is SearchEvent.ShuffleAlbum -> onShuffleAlbum(event.album)
            is SearchEvent.RequestCreateGroup -> libraryActionsManager.requestCreateGroup()
            is SearchEvent.CreateLibraryGroup -> libraryActionsManager.onCreateGroup(event.name)
            is SearchEvent.SelectGroup -> libraryActionsManager.onGroupSelected(event.groupId)
            is SearchEvent.ResolveConflict -> libraryActionsManager.onResolveConflict()
            is SearchEvent.DismissDialog -> libraryActionsManager.dismissDialog()
        }
    }

    fun onPlaylistActionEvent(event: PlaylistActionState) {
        when (event) {
            is PlaylistActionState.CreatePlaylist -> playlistActionsManager.prepareToCreatePlaylist()
            is PlaylistActionState.Hidden -> playlistActionsManager.dismiss()
            else -> {}
        }
    }

    fun onPlaylistCreateConfirm(name: String) {
        playlistActionsManager.onCreatePlaylist(name)
    }

    fun onPlaylistSelected(playlistId: Long) {
        playlistActionsManager.onPlaylistSelected(playlistId)
    }


    private fun refreshResultStatuses() {
        val currentState = _uiState.value
        if (currentState.songs.isEmpty() && currentState.videoStreams.isEmpty()) return

        val localSongsByUrl = allLocalSongs.value.associateBy { it.youtubeUrl }
        val updatedSongs = currentState.songs.map { searchResult ->
            val normalizedUrl = searchResult.streamInfo.url?.replace("music.youtube.com", "www.youtube.com") ?: ""
            searchResult.copy(isInLibrary = localSongsByUrl[normalizedUrl]?.isInLibrary ?: false, isDownloaded = localSongsByUrl[normalizedUrl]?.localFilePath != null)
        }
        val updatedVideoStreams = currentState.videoStreams.map { searchResult ->
            val normalizedUrl = searchResult.streamInfo.url?.replace("music.youtube.com", "www.youtube.com") ?: ""
            searchResult.copy(isInLibrary = localSongsByUrl[normalizedUrl]?.isInLibrary ?: false, isDownloaded = localSongsByUrl[normalizedUrl]?.localFilePath != null)
        }
        _uiState.update { it.copy(songs = updatedSongs, videoStreams = updatedVideoStreams) }
    }

    private fun onFilterChange(newFilter: String) {
        _uiState.update { it.copy(selectedFilter = newFilter) }
        search()
    }

    private fun search() {
        val query = uiState.value.query
        if (query.isBlank()) return
        _uiState.update { it.copy(isLoading = true, songs = emptyList(), albums = emptyList(), artists = emptyList(), videoStreams = emptyList(), playlists = emptyList(), videoChannels = emptyList()) }

        viewModelScope.launch {
            if (uiState.value.selectedFilter == "music_songs") {
                val songsResult = async(Dispatchers.IO) { youtubeRepository.search(query, "music_songs") }.await()
                val albumsResult = async(Dispatchers.IO) { youtubeRepository.search(query, "music_albums") }.await()
                val playlistsResult = async(Dispatchers.IO) { youtubeRepository.search(query, "playlists") }.await()
                val artistsResult = async(Dispatchers.IO) { youtubeRepository.search(query, "channels") }.await()
                updateMusicSearchResults(songsResult?.items ?: emptyList(), albumsResult?.items ?: emptyList(), playlistsResult?.items ?: emptyList(), artistsResult?.items ?: emptyList())
                _uiState.update { it.copy(songsNextPage = songsResult?.nextPage, albumsNextPage = albumsResult?.nextPage, playlistsNextPage = playlistsResult?.nextPage, artistsNextPage = artistsResult?.nextPage, songsHandler = songsResult?.queryHandler, albumsHandler = albumsResult?.queryHandler, playlistsHandler = playlistsResult?.queryHandler, artistsHandler = artistsResult?.queryHandler, isLoading = false) }
            } else {
                val videosResult = async(Dispatchers.IO) { youtubeRepository.search(query, "all") }.await()
                val playlistsResult = async(Dispatchers.IO) { youtubeRepository.search(query, "playlists") }.await()
                val channelsResult = async(Dispatchers.IO) { youtubeRepository.search(query, "channels") }.await()
                updateVideoSearchResults(videosResult?.items ?: emptyList(), playlistsResult?.items ?: emptyList(), channelsResult?.items ?: emptyList())
                _uiState.update { it.copy(videoStreamsNextPage = videosResult?.nextPage, playlistsNextPage = playlistsResult?.nextPage, videoChannelsNextPage = channelsResult?.nextPage, videoStreamsHandler = videosResult?.queryHandler, playlistsHandler = playlistsResult?.queryHandler, videoChannelsHandler = channelsResult?.queryHandler, isLoading = false) }
            }
        }
    }

    private fun loadMoreResults() {
        val currentState = uiState.value
        if (currentState.isLoadingMore || currentState.detailedViewCategory == null) return
        val (pageToLoad, handler) = when (currentState.detailedViewCategory) {
            SearchCategory.SONGS -> currentState.songsNextPage to currentState.songsHandler
            SearchCategory.ALBUMS -> currentState.albumsNextPage to currentState.albumsHandler
            SearchCategory.PLAYLISTS -> currentState.playlistsNextPage to currentState.playlistsHandler
            SearchCategory.ARTISTS -> currentState.artistsNextPage to currentState.artistsHandler
            SearchCategory.VIDEOS -> currentState.videoStreamsNextPage to currentState.videoStreamsHandler
            SearchCategory.CHANNELS -> currentState.videoChannelsNextPage to currentState.videoChannelsHandler
        }
        if (pageToLoad == null || handler == null) return
        _uiState.update { it.copy(isLoadingMore = true) }

        viewModelScope.launch {
            val resultPage = youtubeRepository.getMoreSearchResults(handler, pageToLoad)
            if (resultPage != null) {
                appendSearchResults(currentState.detailedViewCategory!!, resultPage.items)
                _uiState.update {
                    when (currentState.detailedViewCategory) {
                        SearchCategory.SONGS -> it.copy(songsNextPage = resultPage.nextPage)
                        SearchCategory.ALBUMS -> it.copy(albumsNextPage = resultPage.nextPage)
                        SearchCategory.PLAYLISTS -> it.copy(playlistsNextPage = resultPage.nextPage)
                        SearchCategory.ARTISTS -> it.copy(artistsNextPage = resultPage.nextPage)
                        SearchCategory.VIDEOS -> it.copy(videoStreamsNextPage = resultPage.nextPage)
                        SearchCategory.CHANNELS -> it.copy(videoChannelsNextPage = resultPage.nextPage)
                    }
                }
            }
            _uiState.update { it.copy(isLoadingMore = false) }
        }
    }

    private suspend fun appendSearchResults(category: SearchCategory, newItems: List<InfoItem>) {
        val localSongsByUrl = allLocalSongs.value.associateBy { it.youtubeUrl }
        when (category) {
            SearchCategory.SONGS, SearchCategory.VIDEOS -> {
                val songs = newItems.filterIsInstance<StreamInfoItem>().map {
                    val normalizedUrl = it.url?.replace("music.youtube.com", "www.youtube.com") ?: ""
                    SearchResult(it, localSongsByUrl[normalizedUrl]?.isInLibrary ?: false, localSongsByUrl[normalizedUrl]?.localFilePath != null)
                }
                if (category == SearchCategory.SONGS) _uiState.update { it.copy(songs = it.songs + songs) }
                else _uiState.update { it.copy(videoStreams = it.videoStreams + songs) }
            }
            SearchCategory.ALBUMS -> _uiState.update { it.copy(albums = it.albums + newItems.filterIsInstance<PlaylistInfoItem>().map { AlbumResult(it) }) }
            SearchCategory.PLAYLISTS -> _uiState.update { it.copy(playlists = it.playlists + newItems.filterIsInstance<PlaylistInfoItem>().map { AlbumResult(it) }) }
            SearchCategory.ARTISTS, SearchCategory.CHANNELS -> {
                val artists = newItems.filterIsInstance<ChannelInfoItem>().map { ArtistResult(it) }
                if (uiState.value.selectedFilter == "music_songs") _uiState.update { it.copy(artists = it.artists + artists) }
                else _uiState.update { it.copy(videoChannels = it.videoChannels + artists) }
            }
        }
    }

    private suspend fun updateMusicSearchResults(songItems: List<InfoItem>, albumItems: List<InfoItem>, playlistItems: List<InfoItem>, artistItems: List<InfoItem>) {
        val localSongsByUrl = allLocalSongs.value.associateBy { it.youtubeUrl }
        val songs = songItems.filterIsInstance<StreamInfoItem>()
            .distinctBy { "${it.name?.lowercase()?.trim()}::${it.uploaderName?.lowercase()?.trim()}" }
            .map {
                val normalizedUrl = it.url?.replace("music.youtube.com", "www.youtube.com") ?: ""
                SearchResult(it, localSongsByUrl[normalizedUrl]?.isInLibrary ?: false, localSongsByUrl[normalizedUrl]?.localFilePath != null)
            }
        _uiState.update { it.copy(isLoading = false, songs = songs, albums = albumItems.filterIsInstance<PlaylistInfoItem>().map { AlbumResult(it) }, playlists = playlistItems.filterIsInstance<PlaylistInfoItem>().map { AlbumResult(it) }, artists = artistItems.filterIsInstance<ChannelInfoItem>().map { ArtistResult(it) }) }
    }

    private suspend fun updateVideoSearchResults(videoItems: List<InfoItem>, playlistItems: List<InfoItem>, channelItems: List<InfoItem>) {
        val localSongsByUrl = allLocalSongs.value.associateBy { it.youtubeUrl }
        val videos = videoItems.filterIsInstance<StreamInfoItem>().map {
            val normalizedUrl = it.url?.replace("music.youtube.com", "www.youtube.com") ?: ""
            SearchResult(it, localSongsByUrl[normalizedUrl]?.isInLibrary ?: false, localSongsByUrl[normalizedUrl]?.localFilePath != null)
        }
        _uiState.update { it.copy(isLoading = false, videoStreams = videos, playlists = playlistItems.filterIsInstance<PlaylistInfoItem>().map { AlbumResult(it) }, videoChannels = channelItems.filterIsInstance<ChannelInfoItem>().map { ArtistResult(it) }) }
    }

    private fun onSongSelected(selectedIndex: Int) {
        val allItems = if (uiState.value.selectedFilter == "music_songs") uiState.value.songs else uiState.value.videoStreams
        val fullListToPlay = allItems.map { it.streamInfo }
        if (fullListToPlay.isNotEmpty()) {
            viewModelScope.launch {
                val (handler, page) = if (uiState.value.selectedFilter == "music_songs") uiState.value.songsHandler to uiState.value.songsNextPage else uiState.value.videoStreamsHandler to uiState.value.videoStreamsNextPage
                playbackListManager.setCurrentListContext(handler, page)
                musicServiceConnection.playSongList(fullListToPlay, selectedIndex)
            }
        }
    }

    private fun onShuffleAlbum(album: AlbumResult) {
        viewModelScope.launch {
            youtubeRepository.getPlaylistDetails(album.albumInfo.url!!)?.let {
                musicServiceConnection.shuffleSongList(it.playlistInfo.relatedItems.filterIsInstance<StreamInfoItem>(), 0)
            }
        }
    }
}