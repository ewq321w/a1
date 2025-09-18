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
import com.example.m.data.database.Song
import com.example.m.data.database.SongDao
import com.example.m.data.repository.LibraryRepository
import com.example.m.download.DownloadService
import com.example.m.playback.MusicServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    private val imageLoader: ImageLoader
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

        viewModelScope.launch(Dispatchers.IO) {
            musicServiceConnection.queue.collect { itemsWithUid ->
                if (itemsWithUid.isEmpty()) {
                    _queueSongs.value = emptyList()
                    return@collect
                }

                val mediaIds = itemsWithUid.map { it.second.mediaId }
                val urls = mediaIds.filter { it.startsWith("http") }
                val paths = mediaIds.filterNot { it.startsWith("http") }

                val songsFromUrls = if (urls.isNotEmpty()) songDao.getSongsByUrls(urls) else emptyList()
                val songsFromPaths = if (paths.isNotEmpty()) songDao.getSongsByFilePaths(paths) else emptyList()

                val songMap = (songsFromUrls.associateBy { it.youtubeUrl }) + (songsFromPaths.associateBy { it.localFilePath })

                val songsWithStableIds = itemsWithUid.map { (uid, mediaItem) ->
                    uid to songMap[mediaItem.mediaId]
                }
                _queueSongs.value = songsWithStableIds
            }
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
}