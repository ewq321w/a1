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
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import com.example.m.data.database.DownloadQueueDao
import com.example.m.data.database.DownloadStatus
import com.example.m.data.database.Song
import com.example.m.data.database.SongDao
import com.example.m.data.repository.LibraryRepository
import com.example.m.download.DownloadService
import com.example.m.playback.MusicServiceConnection
import com.example.m.data.repository.YoutubeRepository
import com.example.m.managers.LibraryActionsManager
import com.example.m.managers.PlaylistActionsManager
import com.example.m.ui.search.SearchResult
import com.example.m.ui.search.SearchResultForList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    val isPlayerScreenVisible: Boolean = false
)

sealed interface MainEvent {
    object ShowPlayerScreen : MainEvent
    object HidePlayerScreen : MainEvent
    object TogglePlayPause : MainEvent
    object SkipToNext : MainEvent
    object SkipToPrevious : MainEvent
    data class SeekTo(val position: Long) : MainEvent
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
    val libraryActionsManager: LibraryActionsManager,
    val playlistActionsManager: PlaylistActionsManager
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

    private val _uiState = mutableStateOf(MainUiState())
    val uiState: State<MainUiState> = _uiState

    private val _isDoingMaintenance = mutableStateOf(false)
    val isDoingMaintenance: State<Boolean> = _isDoingMaintenance

    private val _maintenanceResult = mutableStateOf<String?>(null)
    val maintenanceResult: State<String?> = _maintenanceResult

    private val _randomGradientColors = mutableStateOf(Color.DarkGray to Color.Black)
    val randomGradientColors: State<Pair<Color, Color>> = _randomGradientColors

    private val _playerGradientColors = mutableStateOf(Color.DarkGray to Color.Black)
    val playerGradientColors: State<Pair<Color, Color>> = _playerGradientColors

    private val allLocalSongs: StateFlow<List<Song>> = songDao.getAllSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val commentCount = MutableStateFlow<Int?>(null)
    val isLoadingRelatedSongs = MutableStateFlow(false)

    private val _relatedStreamInfoItems = MutableStateFlow<List<StreamInfoItem>>(emptyList())
    val relatedSongs: StateFlow<List<SearchResultForList>> = combine(
        _relatedStreamInfoItems,
        allLocalSongs
    ) { relatedItems, localSongs ->
        mapStreamInfoToSearchResults(relatedItems, localSongs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


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

        // Load comment count when the current song changes
        viewModelScope.launch {
            currentMediaId.collect { mediaId ->
                if (mediaId != null && mediaId.startsWith("http")) {
                    loadCommentCount(mediaId)
                    loadRelatedSongs(mediaId)
                } else {
                    commentCount.value = null
                    _relatedStreamInfoItems.value = emptyList()
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
                    uid to song
                }
            }.collect { songsWithStableIds ->
                _queueSongs.value = songsWithStableIds
            }
        }
    }

    private fun mapStreamInfoToSearchResults(items: List<StreamInfoItem>, localSongs: List<Song>): List<SearchResultForList> {
        val localSongsByUrl = localSongs.associateBy { it.youtubeUrl }
        return items.map { streamInfo ->
            val normalizedUrl = streamInfo.url?.replace("music.youtube.com", "www.youtube.com")
            val localSong = localSongsByUrl[normalizedUrl]
            val searchResult = SearchResult(streamInfo, localSong?.isInLibrary ?: false, localSong?.localFilePath != null)
            SearchResultForList(searchResult, localSong)
        }
    }

    fun removeQueueItem(index: Int) = musicServiceConnection.removeQueueItem(index)

    fun moveQueueItem(from: Int, to: Int) {
        // Perform an immediate, optimistic update on the local state for a smooth UI.
        val currentQueue = _queueSongs.value.toMutableList()
        if (from >= 0 && from < currentQueue.size && to >= 0 && to < currentQueue.size) {
            val movedItem = currentQueue.removeAt(from)
            currentQueue.add(to, movedItem)
            _queueSongs.value = currentQueue
        }

        // Then, perform the actual move command. The subsequent update from the service
        // should result in the same state, making the change seamless.
        musicServiceConnection.moveQueueItem(from, to)
    }

    fun skipToQueueItem(index: Int) = musicServiceConnection.skipToQueueItem(index)

    fun playSingleSong(item: Any) {
        viewModelScope.launch {
            musicServiceConnection.playSingleSong(item)
        }
    }

    fun playRelatedSong(streamInfoItem: StreamInfoItem) {
        viewModelScope.launch {
            musicServiceConnection.playSingleSong(streamInfoItem)
        }
    }

    // Add this function to manually trigger related songs loading
    fun ensureRelatedSongsLoaded() {
        viewModelScope.launch {
            val mediaId = currentMediaId.value
            if (mediaId != null && mediaId.startsWith("http") && _relatedStreamInfoItems.value.isEmpty()) {
                loadRelatedSongs(mediaId)
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
            is MainEvent.SkipToNext -> musicServiceConnection.skipToNext()
            is MainEvent.SkipToPrevious -> musicServiceConnection.skipToPrevious()
            is MainEvent.SeekTo -> musicServiceConnection.seekTo(event.position)
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

    private suspend fun loadRelatedSongs(mediaId: String) {
        isLoadingRelatedSongs.value = true
        try {
            withContext(Dispatchers.IO) {
                val streamInfo = youtubeRepository.getStreamInfo(mediaId)

                // Try to get music-focused related content using YouTube Music search
                val musicRelatedItems = getMusicRelatedSongs(streamInfo)

                if (musicRelatedItems.isNotEmpty()) {
                    _relatedStreamInfoItems.value = musicRelatedItems
                } else {
                    // Fallback to filtered regular related items
                    val allRelated = streamInfo?.relatedItems?.filterIsInstance<StreamInfoItem>() ?: emptyList()
                    val musicFiltered = allRelated.filter { item ->
                        isMusicContent(item)
                    }.take(20)
                    _relatedStreamInfoItems.value = musicFiltered
                }
            }
        } catch (e: Exception) {
            _relatedStreamInfoItems.value = emptyList()
        }
        isLoadingRelatedSongs.value = false
    }

    private suspend fun getMusicRelatedSongs(streamInfo: org.schabi.newpipe.extractor.stream.StreamInfo?): List<StreamInfoItem> {
        if (streamInfo == null) return emptyList()

        return try {
            // Extract artist and title for YouTube Music search
            val title = streamInfo.name
            val uploader = streamInfo.uploaderName ?: ""

            // Create search queries for YouTube Music
            val searchQueries = buildMusicSearchQueries(title, uploader)

            // Search YouTube Music for related songs
            val allResults = mutableListOf<StreamInfoItem>()

            for (query in searchQueries.take(2)) { // Limit to avoid too many requests
                val musicResults = youtubeRepository.searchMusic(query)
                allResults.addAll(musicResults.songs.take(10)) // Take top 10 from each search
            }

            // Remove duplicates and limit results
            allResults.distinctBy { it.url }.take(20)

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

    private fun isMusicContent(item: StreamInfoItem): Boolean {
        val title = item.name.lowercase()
        val uploader = item.uploaderName?.lowercase() ?: ""
        val duration = item.duration

        // Filter out clearly non-music content
        val nonMusicKeywords = listOf(
            "podcast", "interview", "talk", "news", "review", "reaction",
            "gameplay", "tutorial", "how to", "vlog", "unboxing", "trailer",
            "documentary", "lecture", "sermon", "speech", "live stream"
        )

        // Check for non-music keywords in title
        if (nonMusicKeywords.any { keyword -> title.contains(keyword) }) {
            return false
        }

        // Filter by duration - music content is typically 1-15 minutes
        if (duration > 0) {
            val durationMinutes = duration / 60.0
            if (durationMinutes < 0.5 || durationMinutes > 15) {
                return false
            }
        }

        // Prefer content from music-related channels
        val musicChannelKeywords = listOf(
            "music", "records", "entertainment", "artist", "band", "singer",
            "official", "vevo", "sounds", "audio", "acoustic", "session"
        )

        val hasMusicChannelKeyword = musicChannelKeywords.any { keyword ->
            uploader.contains(keyword)
        }

        // Prefer music-related titles
        val musicTitleKeywords = listOf(
            "song", "music", "acoustic", "cover", "remix", "live", "session",
            "album", "single", "track", "audio", "instrumental", "karaoke",
            "unplugged", "concert", "performance", "studio", "version"
        )

        val hasMusicTitleKeyword = musicTitleKeywords.any { keyword ->
            title.contains(keyword)
        }

        // Include if it has music indicators or is from a music channel
        return hasMusicChannelKeyword || hasMusicTitleKeyword ||
                // Include if uploader is verified (likely official artists)
                item.isUploaderVerified() ||
                // Include shorter content that doesn't match exclusion criteria
                (duration > 0 && duration <= 600) // 10 minutes or less
    }
}