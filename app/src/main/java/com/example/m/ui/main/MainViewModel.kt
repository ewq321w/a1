// file: com/example/m/ui/main/MainViewModel.kt
package com.example.m.ui.main

import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import com.example.m.data.database.DownloadQueueDao
import com.example.m.data.database.DownloadQueueItem
import com.example.m.data.database.DownloadStatus
import com.example.m.data.database.Song
import com.example.m.data.database.SongDao
import com.example.m.data.repository.LibraryRepository
import com.example.m.download.DownloadService
import com.example.m.playback.MusicServiceConnection
import com.example.m.data.repository.YoutubeRepository
import com.example.m.data.repository.LyricsRepository
import com.example.m.managers.LibraryActionsManager
import com.example.m.managers.PlaylistActionsManager
import com.example.m.managers.SnackbarManager
import com.example.m.ui.search.SearchResult
import com.example.m.ui.search.SearchResultForList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import android.graphics.Color as AndroidColor

data class MainUiState(
    val isPlayerScreenVisible: Boolean = false,
    val itemPendingDeletion: Song? = null
)

enum class LoopMode {
    OFF,        // Normal playback
    ONE,        // Loop current song
    ALL         // Loop entire queue
}

sealed interface MainEvent {
    object ShowPlayerScreen : MainEvent
    object HidePlayerScreen : MainEvent
    object TogglePlayPause : MainEvent
    object SkipToNext : MainEvent
    object SkipToPrevious : MainEvent
    data class SeekTo(val position: Long) : MainEvent
    object ToggleLoop : MainEvent
    object ToggleShuffle : MainEvent
    object AddToLibrary : MainEvent
    object AddToPlaylist : MainEvent
    object DeleteFromLibrary : MainEvent
    object ConfirmDeletion : MainEvent
    object CancelDeletion : MainEvent
    object DownloadCurrentSong : MainEvent
    object DeleteCurrentSongDownload : MainEvent
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val musicServiceConnection: MusicServiceConnection,
    private val downloadQueueDao: DownloadQueueDao,
    private val libraryRepository: LibraryRepository,
    private val songDao: SongDao,
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
    private val youtubeRepository: YoutubeRepository,
    private val lyricsRepository: LyricsRepository,
    val libraryActionsManager: LibraryActionsManager,
    val playlistActionsManager: PlaylistActionsManager,
    val snackbarManager: SnackbarManager
) : ViewModel() {
    val nowPlaying = musicServiceConnection.nowPlaying
    val isPlaying = musicServiceConnection.isPlaying
    val isLoading = musicServiceConnection.isLoading
    val playerState = musicServiceConnection.playerState
    val playbackState = musicServiceConnection.playbackState
    val queue = musicServiceConnection.queue
    val currentMediaItemIndex = musicServiceConnection.currentMediaItemIndex
    val currentMediaId = musicServiceConnection.currentMediaId

    private val _queueSongs = MutableStateFlow<List<Pair<String, Song?>>>(emptyList())
    val queueSongs: StateFlow<List<Pair<String, Song?>>> = _queueSongs

    // Loop and shuffle state - moved here to be available for displayQueue
    private val _loopMode = MutableStateFlow(LoopMode.OFF)
    val loopMode: StateFlow<LoopMode> = _loopMode

    private val _isShuffled = MutableStateFlow(false)
    val isShuffled: StateFlow<Boolean> = _isShuffled

    private val _isShuffling = MutableStateFlow(false)
    val isShuffling: StateFlow<Boolean> = _isShuffling

    private val allLocalSongs: StateFlow<List<Song>> = songDao.getAllSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Use the normal queue - no complex shuffle display logic needed
    val displayQueue: StateFlow<List<Pair<String, Song?>>> = _queueSongs

    // Current song's position matches the actual player index
    val currentDisplayIndex: StateFlow<Int> = currentMediaItemIndex

    private val _uiState = mutableStateOf(MainUiState())
    val uiState: State<MainUiState> = _uiState

    private val _isDoingMaintenance = mutableStateOf(false)
    val isDoingMaintenance: State<Boolean> = _isDoingMaintenance

    private val _maintenanceResult = mutableStateOf<String?>(null)
    val maintenanceResult: State<String?> = _maintenanceResult

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private val _randomGradientColors = mutableStateOf(Color.DarkGray to Color.Black)
    val randomGradientColors: State<Pair<Color, Color>> = _randomGradientColors

    private val _playerGradientColors = mutableStateOf(Color.DarkGray to Color.Black)
    val playerGradientColors: State<Pair<Color, Color>> = _playerGradientColors

    val commentCount = MutableStateFlow<Int?>(null)

    // Track if current song is already in library
    private val _isCurrentSongInLibrary = MutableStateFlow(false)
    val isCurrentSongInLibrary: StateFlow<Boolean> = _isCurrentSongInLibrary

    // Track current song's download status
    private val _currentSongDownloadStatus = MutableStateFlow(DownloadStatus.NOT_DOWNLOADED)
    val currentSongDownloadStatus: StateFlow<DownloadStatus> = _currentSongDownloadStatus

    private val _currentSongDownloadProgress = MutableStateFlow(0)
    val currentSongDownloadProgress: StateFlow<Int> = _currentSongDownloadProgress

    // New data streams for testing unfiltered results
    private val _unfilteredMusicResults = MutableStateFlow<List<StreamInfoItem>>(emptyList())
    val unfilteredMusicResults: StateFlow<List<SearchResultForList>> = combine(
        _unfilteredMusicResults,
        allLocalSongs
    ) { musicItems, localSongs ->
        mapStreamInfoToSearchResults(musicItems, localSongs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _unfilteredRegularResults = MutableStateFlow<List<StreamInfoItem>>(emptyList())
    val unfilteredRegularResults: StateFlow<List<SearchResultForList>> = combine(
        _unfilteredRegularResults,
        allLocalSongs
    ) { regularItems, localSongs ->
        mapStreamInfoToSearchResults(regularItems, localSongs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _youtubeMixResults = MutableStateFlow<List<StreamInfoItem>>(emptyList())
    val youtubeMixResults: StateFlow<List<SearchResultForList>> = combine(
        _youtubeMixResults,
        allLocalSongs
    ) { mixItems, localSongs ->
        mapStreamInfoToSearchResults(mixItems, localSongs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isLoadingUnfilteredResults = MutableStateFlow(false)

    // Lyrics state
    val currentLyrics = MutableStateFlow<String?>(null)
    val isLoadingLyrics = MutableStateFlow(false)
    val lyricsError = MutableStateFlow<String?>(null)


    init {
        _randomGradientColors.value = generateHarmoniousVibrantColors()

        viewModelScope.launch {
            nowPlaying.map { it?.artworkUri?.toString() }.distinctUntilChanged().collect { uri ->
                if (uri != null) {
                    // Proactively load the image into memory cache
                    val request = ImageRequest.Builder(context)
                        .data(uri)
                        .build()
                    imageLoader.enqueue(request)
                }
                // Also update colors proactively
                updatePlayerColors(uri)
            }
        }

        // Sync shuffle state with Media3 player state periodically
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000) // Check every second
                val actualShuffleState = musicServiceConnection.isShuffleModeEnabled()
                if (_isShuffled.value != actualShuffleState) {
                    _isShuffled.value = actualShuffleState
                }

                // Sync repeat mode as well
                val actualRepeatMode = musicServiceConnection.getCurrentRepeatMode()
                val expectedLoopMode = when (actualRepeatMode) {
                    Player.REPEAT_MODE_OFF -> LoopMode.OFF
                    Player.REPEAT_MODE_ONE -> LoopMode.ONE
                    Player.REPEAT_MODE_ALL -> LoopMode.ALL
                    else -> LoopMode.OFF
                }
                if (_loopMode.value != expectedLoopMode) {
                    _loopMode.value = expectedLoopMode
                }
            }
        }

        // Load comment count, related songs, and lyrics when the current song changes
        viewModelScope.launch {
            currentMediaId.collect { mediaId ->
                if (mediaId != null && mediaId.startsWith("http")) {
                    // Clear previous states immediately to prevent flickering
                    currentLyrics.value = null
                    lyricsError.value = null
                    isLoadingLyrics.value = true // Set loading immediately for instant feedback

                    commentCount.value = null

                    // Check if current song is already in library
                    checkCurrentSongLibraryStatus(mediaId)

                    // DON'T clear all related results at once - let each load independently
                    // Only clear them individually when their specific loading starts

                    // Load new data independently - each section will clear itself when it starts loading
                    loadCommentCount(mediaId)
                    loadUnfilteredResults(mediaId)
                    loadYoutubeMixResults(mediaId)
                    loadLyrics(mediaId)
                } else {
                    commentCount.value = null
                    _isCurrentSongInLibrary.value = false
                    _unfilteredMusicResults.value = emptyList()
                    _unfilteredRegularResults.value = emptyList()
                    _youtubeMixResults.value = emptyList()
                    currentLyrics.value = null
                    lyricsError.value = null
                    isLoadingLyrics.value = false
                    isLoadingUnfilteredResults.value = false
                }
            }
        }

        viewModelScope.launch {
            combine(musicServiceConnection.queue, allLocalSongs) { queueItems, localSongs ->
                val songMapByUrl = localSongs.associateBy { it.youtubeUrl }
                val songMapByPath = localSongs.filter { it.localFilePath != null }.associateBy { it.localFilePath!! }

                queueItems.map { (uid, mediaItem) ->
                    val song = if (mediaItem.mediaId.startsWith("http")) {
                        songMapByUrl[mediaItem.mediaId]
                    } else {
                        songMapByPath[mediaItem.mediaId]
                    }
                    mediaItem.mediaId to song
                }
            }.collect { songsWithStableIds ->
                _queueSongs.value = songsWithStableIds
            }
        }

        // Add reactive listener for library status updates
        viewModelScope.launch {
            combine(currentMediaId, allLocalSongs) { mediaId, localSongs ->
                if (mediaId != null && mediaId.startsWith("http")) {
                    val normalizedUrl = mediaId.replace("music.youtube.com", "www.youtube.com")
                    val existingSong = localSongs.find { it.youtubeUrl == normalizedUrl }
                    existingSong?.isInLibrary ?: false
                } else {
                    false
                }
            }.collect { isInLibrary ->
                _isCurrentSongInLibrary.value = isInLibrary
            }
        }

        // Add reactive listener for download status updates
        viewModelScope.launch {
            combine(currentMediaId, allLocalSongs) { mediaId, localSongs ->
                if (mediaId != null && mediaId.startsWith("http")) {
                    val normalizedUrl = mediaId.replace("music.youtube.com", "www.youtube.com")
                    val existingSong = localSongs.find { it.youtubeUrl == normalizedUrl }
                    existingSong?.downloadStatus ?: DownloadStatus.NOT_DOWNLOADED
                } else {
                    DownloadStatus.NOT_DOWNLOADED
                }
            }.collect { downloadStatus ->
                _currentSongDownloadStatus.value = downloadStatus
            }
        }

        // Add reactive listener for download progress updates
        viewModelScope.launch {
            combine(currentMediaId, allLocalSongs) { mediaId, localSongs ->
                if (mediaId != null && mediaId.startsWith("http")) {
                    val normalizedUrl = mediaId.replace("music.youtube.com", "www.youtube.com")
                    val existingSong = localSongs.find { it.youtubeUrl == normalizedUrl }
                    existingSong?.downloadProgress ?: 0
                } else {
                    0
                }
            }.collect { downloadProgress ->
                _currentSongDownloadProgress.value = downloadProgress
            }
        }

        viewModelScope.launch {
            // Ensure comment count is loaded for the initial song
            ensureCommentCountLoaded()
        }
    }

    private fun mapStreamInfoToSearchResults(items: List<StreamInfoItem>, localSongs: List<Song>): List<SearchResultForList> {
        val localSongsByUrl = localSongs.associateBy { it.youtubeUrl }
        return items.map { streamInfo ->
            val normalizedUrl = streamInfo.getUrl()?.replace("music.youtube.com", "www.youtube.com")
            val localSong = localSongsByUrl[normalizedUrl]
            val searchResult = SearchResult(streamInfo, localSong?.isInLibrary ?: false, localSong?.localFilePath != null)
            SearchResultForList(searchResult, localSong)
        }
    }

    fun removeQueueItem(index: Int) = musicServiceConnection.removeQueueItem(index)

    fun moveQueueItem(from: Int, to: Int) {
        // Remove optimistic update to prevent jumping back
        musicServiceConnection.moveQueueItem(from, to)
    }

    fun skipToQueueItem(index: Int) = musicServiceConnection.skipToQueueItem(index)

    fun playSingleSong(item: Any) {
        viewModelScope.launch {
            musicServiceConnection.playSingleSong(item)
        }
    }

    fun playRelatedSongWithYoutubeMix(streamInfoItem: StreamInfoItem) {
        viewModelScope.launch {
            val currentYoutubeMixResults = _youtubeMixResults.value
            if (currentYoutubeMixResults.isNotEmpty()) {
                // Create a list of songs starting with the selected song, followed by YouTube Mix
                val songsToPlay = mutableListOf<Any>()

                // Add the selected song first
                songsToPlay.add(streamInfoItem)

                // Add YouTube Mix songs (excluding the selected song if it's already in the mix)
                val selectedSongUrl = streamInfoItem.getUrl()?.replace("music.youtube.com", "www.youtube.com")
                currentYoutubeMixResults.forEach { mixResult ->
                    val mixSongUrl = mixResult.getUrl()?.replace("music.youtube.com", "www.youtube.com")
                    // Only add if it's not the same as the selected song
                    if (mixSongUrl != selectedSongUrl) {
                        songsToPlay.add(mixResult)
                    }
                }

                // Play the queue starting with the selected song
                musicServiceConnection.playSongList(songsToPlay, 0)
            } else {
                // Fallback to single song if no YouTube Mix available
                musicServiceConnection.playSingleSong(streamInfoItem)
            }
        }
    }

    // Add this function to manually trigger comment count loading
    fun ensureCommentCountLoaded() {
        viewModelScope.launch {
            val mediaId = currentMediaId.value
            if (mediaId != null && mediaId.startsWith("http") && commentCount.value == null) {
                loadCommentCount(mediaId)
            }
        }
    }

    fun addToQueueNext(streamInfoItem: StreamInfoItem) {
        musicServiceConnection.playNext(streamInfoItem)
    }

    fun addToQueue(streamInfoItem: StreamInfoItem) {
        musicServiceConnection.addToQueue(streamInfoItem)
    }

    private fun addCurrentSongToLibrary() {
        viewModelScope.launch {
            val mediaId = currentMediaId.value
            if (mediaId != null && mediaId.startsWith("http")) {
                try {
                    withContext(Dispatchers.IO) {
                        val streamInfo = youtubeRepository.getStreamInfo(mediaId)
                        if (streamInfo != null) {
                            libraryActionsManager.addToLibrary(streamInfo)
                        }
                    }
                } catch (e: Exception) {
                    // Handle error silently or show a toast
                }
            }
        }
    }

    private fun generateHarmoniousVibrantColors(): Pair<Color, Color> {
        val random = java.util.Random()
        val hue1 = random.nextFloat() * 360f
        val saturation1 = 0.7f + random.nextFloat() * 0.3f
        val lightness1 = 0.5f
        val color1 = Color.hsl(hue1, saturation1, lightness1)
        var hueOffset = 100f + random.nextFloat() * 40f
        if (random.nextBoolean()) {
            hueOffset *= -1
        }
        val hue2 = (hue1 + hueOffset + 360f) % 360f
        val saturation2 = 0.7f + random.nextFloat() * 0.3f
        val lightness2 = 0.5f
        val color2 = Color.hsl(hue2, saturation2, lightness2)
        return color1 to color2
    }

    fun onEvent(event: MainEvent) {
        when(event) {
            is MainEvent.ShowPlayerScreen -> _uiState.value = _uiState.value.copy(isPlayerScreenVisible = true)
            is MainEvent.HidePlayerScreen -> _uiState.value = _uiState.value.copy(isPlayerScreenVisible = false)
            is MainEvent.TogglePlayPause -> musicServiceConnection.togglePlayPause()
            is MainEvent.SkipToNext -> {
                // If loop mode is ONE and user manually skips, reset to OFF
                if (_loopMode.value == LoopMode.ONE) {
                    _loopMode.value = LoopMode.OFF
                    musicServiceConnection.setRepeatMode(Player.REPEAT_MODE_OFF)
                }
                musicServiceConnection.skipToNext()
            }
            is MainEvent.SkipToPrevious -> {
                // If loop mode is ONE and user manually skips, reset to OFF
                if (_loopMode.value == LoopMode.ONE) {
                    _loopMode.value = LoopMode.OFF
                    musicServiceConnection.setRepeatMode(Player.REPEAT_MODE_OFF)
                }
                musicServiceConnection.skipToPrevious()
            }
            is MainEvent.SeekTo -> musicServiceConnection.seekTo(event.position)
            is MainEvent.ToggleLoop -> toggleLoopMode()
            is MainEvent.ToggleShuffle -> toggleShuffle()
            is MainEvent.AddToLibrary -> addCurrentSongToLibrary()
            is MainEvent.AddToPlaylist -> addCurrentSongToPlaylist()
            is MainEvent.DeleteFromLibrary -> {
                // Set the deletion state with the current song
                _uiState.value = _uiState.value.copy(itemPendingDeletion = getCurrentSong())
            }
            is MainEvent.ConfirmDeletion -> {
                // Perform the deletion action
                deleteCurrentSongFromLibrary()
                // Clear the deletion state
                _uiState.value = _uiState.value.copy(itemPendingDeletion = null)
            }
            is MainEvent.CancelDeletion -> {
                // Clear the deletion state
                _uiState.value = _uiState.value.copy(itemPendingDeletion = null)
            }
            is MainEvent.DownloadCurrentSong -> {
                // Start the download service for the current song
                startDownloadForCurrentSong()
            }
            is MainEvent.DeleteCurrentSongDownload -> {
                // Delete the download for the current song
                deleteDownloadForCurrentSong()
            }
        }
    }

    private fun addCurrentSongToPlaylist() {
        viewModelScope.launch {
            val mediaId = currentMediaId.value
            if (mediaId != null && mediaId.startsWith("http")) {
                try {
                    withContext(Dispatchers.IO) {
                        val streamInfo = youtubeRepository.getStreamInfo(mediaId)
                        if (streamInfo != null) {
                            playlistActionsManager.selectItem(streamInfo)
                        }
                    }
                } catch (e: Exception) {
                    // Handle error silently or show a toast
                }
            }
        }
    }

    private fun toggleLoopMode() {
        _loopMode.value = when (_loopMode.value) {
            LoopMode.OFF -> LoopMode.ONE
            LoopMode.ONE -> LoopMode.ALL
            LoopMode.ALL -> LoopMode.OFF
        }

        // Apply the loop mode to the actual media player
        val repeatMode = when (_loopMode.value) {
            LoopMode.OFF -> Player.REPEAT_MODE_OFF
            LoopMode.ONE -> Player.REPEAT_MODE_ONE
            LoopMode.ALL -> Player.REPEAT_MODE_ALL
        }
        musicServiceConnection.setRepeatMode(repeatMode)
    }

    private fun toggleShuffle() {
        // Simple shuffle like library 3-dot menu: create new shuffled queue
        val currentQueue = displayQueue.value
        val currentIndex = currentDisplayIndex.value

        if (currentQueue.isNotEmpty() && currentIndex in 0 until currentQueue.size) {
            viewModelScope.launch {
                // Set shuffling state to true immediately
                _isShuffling.value = true

                try {
                    // Get all songs from the current queue, creating Song objects for items that don't have them
                    val originalQueueItems = musicServiceConnection.queue.value
                    val songsForShuffle = mutableListOf<Song>()

                    originalQueueItems.forEachIndexed { index, (_, mediaItem) ->
                        val existingSong = currentQueue.getOrNull(index)?.second

                        if (existingSong != null) {
                            // Use the existing Song object
                            songsForShuffle.add(existingSong)
                        } else {
                            // Create a temporary Song object for YouTube streams not yet downloaded
                            val tempSong = if (mediaItem.mediaId.startsWith("http")) {
                                Song(
                                    songId = 0, // Temporary ID
                                    videoId = null,
                                    youtubeUrl = mediaItem.mediaId,
                                    title = mediaItem.mediaMetadata.title?.toString() ?: "Unknown",
                                    artist = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown",
                                    duration = 0, // Will be updated when played
                                    thumbnailUrl = mediaItem.mediaMetadata.artworkUri?.toString() ?: "",
                                    localFilePath = null,
                                    isInLibrary = false
                                )
                            } else {
                                // For local files, try to reconstruct from mediaId
                                Song(
                                    songId = 0,
                                    videoId = null,
                                    youtubeUrl = mediaItem.mediaId, // Use mediaId as URL for local files
                                    title = mediaItem.mediaMetadata.title?.toString() ?: "Unknown",
                                    artist = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown",
                                    duration = 0,
                                    thumbnailUrl = mediaItem.mediaMetadata.artworkUri?.toString() ?: "",
                                    localFilePath = mediaItem.mediaId,
                                    isInLibrary = true
                                )
                            }
                            songsForShuffle.add(tempSong)
                        }
                    }

                    if (songsForShuffle.isNotEmpty()) {
                        // Use the new shuffle method that preserves playback state
                        musicServiceConnection.shuffleQueuePreservingState(songsForShuffle, currentIndex)

                        // Update shuffle state for UI
                        _isShuffled.value = true
                    }
                } finally {
                    // Always reset shuffling state when done
                    _isShuffling.value = false
                }
            }
        }
    }

    private fun saturateColor(color: Color, factor: Float): Color {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(color.toArgb(), hsv)
        hsv[1] = (hsv[1] * factor).coerceIn(0f, 1f) // Index 1 is Saturation
        return Color(AndroidColor.HSVToColor(hsv))
    }

    private fun updatePlayerColors(artworkUri: String?) {
        val defaultColor1 = Color.DarkGray
        val defaultColor2 = Color.Black

        if (artworkUri.isNullOrBlank()) {
            _playerGradientColors.value = defaultColor1 to defaultColor2
            return
        }

        viewModelScope.launch {
            try {
                val request = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .allowHardware(false)
                    .build()

                val resultBitmap = (imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
                if (resultBitmap != null) {
                    val palette = withContext(Dispatchers.Default) {
                        Palette.from(resultBitmap).generate()
                    }
                    val vibrant = palette.vibrantSwatch?.rgb?.let { Color(it) } ?: defaultColor1
                    val darkMuted = palette.darkMutedSwatch?.rgb?.let { Color(it) } ?: defaultColor2

                    val saturationFactor = 1.4f
                    val saturatedVibrant = saturateColor(vibrant, saturationFactor)
                    val saturatedDarkMuted = saturateColor(darkMuted, saturationFactor)

                    _playerGradientColors.value = saturatedVibrant to saturatedDarkMuted
                } else {
                    _playerGradientColors.value = defaultColor1 to defaultColor2
                }
            } catch (e: Exception) {
                _playerGradientColors.value = defaultColor1 to defaultColor2
            }
        }
    }

    fun checkAndResumeDownloadQueue() {
        viewModelScope.launch {
            if (downloadQueueDao.getNextItem() != null) {
                val intent = Intent(context, DownloadService::class.java).apply {
                    action = DownloadService.ACTION_PROCESS_QUEUE
                }
                context.startService(intent)
            }
        }
    }

    fun runLibraryMaintenance() {
        viewModelScope.launch {
            _isDoingMaintenance.value = true
            val (reQueuedCount, cleanedCount, fixedLinksCount, cleanedArtistsCount, cleanedSongsCount) = withContext(Dispatchers.IO) {
                listOf(
                    async { libraryRepository.verifyLibraryEntries() },
                    async { libraryRepository.cleanOrphanedFiles() },
                    async { libraryRepository.fixMissingArtistLinks() },
                    async { libraryRepository.cleanOrphanedArtists() },
                    async { libraryRepository.cleanOrphanedSongs() }
                ).awaitAll()
            }

            val results = listOfNotNull(
                if (reQueuedCount > 0) "$reQueuedCount songs re-queued" else null,
                if (cleanedCount > 0) "$cleanedCount orphaned files deleted" else null,
                if (fixedLinksCount > 0) "$fixedLinksCount artist links fixed" else null,
                if (cleanedArtistsCount > 0) "$cleanedArtistsCount orphaned artists removed" else null,
                if (cleanedSongsCount > 0) "$cleanedSongsCount cached songs removed" else null
            )

            _maintenanceResult.value = if (results.isEmpty()) "Library check complete. No issues found."
            else "Check complete: ${results.joinToString(", ")}."

            if (reQueuedCount > 0) {
                val intent = Intent(context, DownloadService::class.java).apply { action = DownloadService.ACTION_PROCESS_QUEUE }
                context.startService(intent)
            }
            _isDoingMaintenance.value = false
        }
    }

    fun clearMaintenanceResult() {
        _maintenanceResult.value = null
    }

    fun showSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarMessage.emit(message)
        }
    }

    private suspend fun checkCurrentSongLibraryStatus(mediaId: String) {
        try {
            withContext(Dispatchers.IO) {
                val normalizedUrl = mediaId.replace("music.youtube.com", "www.youtube.com")
                val existingSong = songDao.getSongByUrl(normalizedUrl)
                _isCurrentSongInLibrary.value = existingSong?.isInLibrary ?: false
            }
        } catch (e: Exception) {
            _isCurrentSongInLibrary.value = false
        }
    }

    private suspend fun loadCommentCount(mediaId: String) {
        try {
            withContext(Dispatchers.IO) {
                // Use the new method to get total comment count directly from StreamInfo
                val totalCommentCount = youtubeRepository.getStreamCommentCount(mediaId)
                commentCount.value = totalCommentCount
            }
        } catch (e: Exception) {
            // If we can't load comment count, show null (no count displayed)
            commentCount.value = null
        }
    }

    private suspend fun loadLyrics(mediaId: String) {
        // Set loading state immediately for UI feedback
        isLoadingLyrics.value = true
        lyricsError.value = null

        try {
            withContext(Dispatchers.IO) {
                val streamInfo = youtubeRepository.getStreamInfo(mediaId)
                if (streamInfo != null) {
                    val artist = streamInfo.getUploaderName() ?: "Unknown Artist"
                    val title = streamInfo.getName() ?: "Unknown Title"

                    val result = lyricsRepository.getLyrics(artist, title)

                    if (result.isSuccessful && result.lyrics != null) {
                        currentLyrics.value = result.lyrics
                    } else {
                        currentLyrics.value = null
                        lyricsError.value = "Lyrics not found"
                    }
                } else {
                    currentLyrics.value = null
                    lyricsError.value = "Could not load song information"
                }
            }
        } catch (e: Exception) {
            currentLyrics.value = null
            lyricsError.value = "Failed to load lyrics: ${e.localizedMessage}"
        } finally {
            // Always clear loading state
            isLoadingLyrics.value = false
        }
    }


    // Manual lyrics loading for user-initiated retries and immediate triggers
    fun ensureLyricsLoaded() {
        viewModelScope.launch {
            val mediaId = currentMediaId.value
            if (mediaId != null && mediaId.startsWith("http")) {
                // Set loading state immediately for instant UI feedback
                isLoadingLyrics.value = true
                currentLyrics.value = null
                lyricsError.value = null

                // Start loading immediately without any delays
                loadLyrics(mediaId)
            }
        }
    }

    private suspend fun loadUnfilteredResults(mediaId: String) {
        isLoadingUnfilteredResults.value = true
        // Clear only these sections when starting to load
        _unfilteredMusicResults.value = emptyList()
        _unfilteredRegularResults.value = emptyList()

        try {
            withContext(Dispatchers.IO) {
                val streamInfo = youtubeRepository.getStreamInfo(mediaId)

                if (streamInfo != null) {
                    // Get unfiltered music search results (no filtering applied)
                    val unfilteredMusicItems = getUnfilteredMusicSearch(streamInfo)
                    _unfilteredMusicResults.value = unfilteredMusicItems

                    // Get unfiltered regular related items (YouTube's native suggestions)
                    val unfilteredRegularItems = streamInfo.relatedItems?.filterIsInstance<StreamInfoItem>()?.take(20) ?: emptyList()
                    _unfilteredRegularResults.value = unfilteredRegularItems
                } else {
                    _unfilteredMusicResults.value = emptyList()
                    _unfilteredRegularResults.value = emptyList()
                }
            }
        } catch (e: Exception) {
            _unfilteredMusicResults.value = emptyList()
            _unfilteredRegularResults.value = emptyList()
        }
        isLoadingUnfilteredResults.value = false
    }

    private suspend fun getUnfilteredMusicSearch(streamInfo: org.schabi.newpipe.extractor.stream.StreamInfo): List<StreamInfoItem> {
        return try {
            // Extract artist and title for YouTube Music search
            val title = streamInfo.getName()
            val uploader = streamInfo.getUploaderName() ?: ""

            // Create search queries for YouTube Music (same as filtered version)
            val searchQueries = buildMusicSearchQueries(title, uploader)

            // Search YouTube Music for related songs - but WITHOUT any filtering
            val allResults = mutableListOf<StreamInfoItem>()

            for (query in searchQueries.take(2)) { // Limit to avoid too many requests
                val musicResults = youtubeRepository.searchMusic(query)
                // Take all results without any music content filtering
                allResults.addAll(musicResults.songs.take(10))
            }

            // Remove duplicates and limit results - no content filtering applied
            allResults.distinctBy { it.getUrl() }.take(20)

        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildMusicSearchQueries(title: String, uploader: String): List<String> {
        val queries = mutableListOf<String>()

        // Clean up title - remove common noise words
        val cleanTitle = title.replace(Regex("\\(.*?\\)|\\[.*?\\]|\\|.*"), "")
            .replace(Regex("(official|video|audio|lyric|HD|4K|music|song)", RegexOption.IGNORE_CASE), "")
            .trim()

        // Extract likely artist name from uploader
        val cleanUploader = uploader.replace(Regex("(official|channel|music|records|entertainment|VEVO)", RegexOption.IGNORE_CASE), "")
            .trim()

        // Search by artist if we have one
        if (cleanUploader.isNotBlank() && cleanUploader.length > 2) {
            queries.add(cleanUploader)
        }

        // Search by title
        if (cleanTitle.isNotBlank()) {
            queries.add(cleanTitle)
        }

        // Search by artist + partial title
        if (cleanUploader.isNotBlank() && cleanTitle.isNotBlank()) {
            val titleWords = cleanTitle.split(" ").filter { it.length > 2 }
            if (titleWords.isNotEmpty()) {
                queries.add("$cleanUploader ${titleWords.first()}")
            }
        }

        return queries.filter { it.isNotBlank() }
    }

    private suspend fun loadYoutubeMixResults(mediaId: String) {
        // Clear only this section when starting to load
        _youtubeMixResults.value = emptyList()

        try {
            withContext(Dispatchers.IO) {
                // Extract video ID from the YouTube URL
                val videoId = extractVideoIdFromUrl(mediaId)
                if (videoId != null) {
                    // Try to get YouTube Mix playlist based on the current video
                    val mixResults = youtubeRepository.getYoutubeMixPlaylist(videoId)
                    _youtubeMixResults.value = mixResults.take(20)
                } else {
                    _youtubeMixResults.value = emptyList()
                }
            }
        } catch (e: Exception) {
            _youtubeMixResults.value = emptyList()
        }
    }

    private suspend fun loadYoutubeMixPlaylist(mediaId: String) {
        // Delegate to the main method to avoid code duplication
        loadYoutubeMixResults(mediaId)
    }

    private fun extractVideoIdFromUrl(url: String): String? {
        return try {
            // Extract video ID from various YouTube URL formats
            when {
                url.contains("youtube.com/watch?v=") -> {
                    val regex = Regex("v=([a-zA-Z0-9_-]{11})")
                    regex.find(url)?.groupValues?.get(1)
                }
                url.contains("youtu.be/") -> {
                    val regex = Regex("youtu.be/([a-zA-Z0-9_-]{11})")
                    regex.find(url)?.groupValues?.get(1)
                }
                url.contains("music.youtube.com/watch?v=") -> {
                    val regex = Regex("v=([a-zA-Z0-9_-]{11})")
                    regex.find(url)?.groupValues?.get(1)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getCurrentSong(): Song? {
        val mediaId = currentMediaId.value ?: return null
        val songList = queueSongs.value
        val currentIndex = songList.indexOfFirst { it.first == mediaId }
        return if (currentIndex != -1) {
            songList[currentIndex].second
        } else {
            null
        }
    }

    private fun deleteCurrentSongFromLibrary() {
        viewModelScope.launch {
            val songToDelete = getCurrentSong()
            if (songToDelete != null) {
                try {
                    withContext(Dispatchers.IO) {
                        // Remove from library while keeping cached data for playback
                        libraryRepository.removeSongFromLibraryKeepCached(songToDelete)
                    }
                } catch (e: Exception) {
                    // Handle error silently or show a toast
                }
            }
        }
    }

    private fun startDownloadForCurrentSong() {
        viewModelScope.launch {
            val song = getCurrentSong()
            if (song != null && song.youtubeUrl.startsWith("http") && song.isInLibrary) {
                try {
                    withContext(Dispatchers.IO) {
                        // Set status to QUEUED immediately for instant UI feedback
                        songDao.updateDownloadStatus(song.songId, DownloadStatus.QUEUED)

                        // Add to download queue
                        downloadQueueDao.addItem(DownloadQueueItem(songId = song.songId))

                        // Start the download service
                        val intent = Intent(context, DownloadService::class.java).apply {
                            action = DownloadService.ACTION_PROCESS_QUEUE
                        }
                        context.startService(intent)
                    }
                } catch (e: Exception) {
                    // Handle error silently or show a toast
                }
            }
        }
    }

    private fun deleteDownloadForCurrentSong() {
        viewModelScope.launch {
            val song = getCurrentSong()
            if (song != null && song.youtubeUrl.startsWith("http")) {
                try {
                    // Check for auto-download conflicts before deleting
                    val conflict = libraryRepository.checkForAutoDownloadConflict(song)
                    if (conflict != null) {
                        val message = when (conflict) {
                            is com.example.m.data.repository.AutoDownloadConflict.Artist ->
                                "Cannot delete download. Auto-download is enabled for artist '${conflict.name}'."
                            is com.example.m.data.repository.AutoDownloadConflict.Playlist ->
                                "Cannot delete download. Song is in auto-downloading playlist '${conflict.name}'."
                        }
                        snackbarManager.showMessage(message)
                    } else {
                        withContext(Dispatchers.IO) {
                            // Remove from download queue and use the existing library method to delete the file
                            downloadQueueDao.deleteItem(song.songId)
                            libraryRepository.deleteDownloadedFileForSong(song)
                        }
                    }
                } catch (e: Exception) {
                    // Handle error silently or show a toast
                }
            }
        }
    }
}
