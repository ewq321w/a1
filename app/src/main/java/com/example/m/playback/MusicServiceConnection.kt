// file: com/example/m/playback/MusicServiceConnection.kt
package com.example.m.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.example.m.data.database.*
import com.example.m.data.repository.YoutubeRepository
import com.example.m.managers.PlaybackListManager
import com.example.m.managers.PlaylistManager
import com.example.m.managers.ThumbnailProcessor
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MusicServiceConnection"

@Singleton
class MusicServiceConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youtubeRepository: YoutubeRepository,
    private val songDao: SongDao,
    private val listeningHistoryDao: ListeningHistoryDao,
    private val playbackStateDao: PlaybackStateDao,
    private val playlistManager: PlaylistManager,
    private val playbackListManager: PlaybackListManager,
    private val thumbnailProcessor: ThumbnailProcessor
) {
    private val serviceComponent = ComponentName(context, MusicService::class.java)
    private var mediaBrowserFuture: ListenableFuture<MediaBrowser>

    private var mediaBrowser: MediaBrowser? = null

    private val _nowPlaying = MutableStateFlow<MediaMetadata?>(null)
    val nowPlaying: StateFlow<MediaMetadata?> = _nowPlaying

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _playerState = MutableStateFlow(Player.STATE_IDLE)
    val playerState: StateFlow<Int> = _playerState

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState

    private val _queue = MutableStateFlow<List<Pair<String, MediaItem>>>(emptyList())
    val queue: StateFlow<List<Pair<String, MediaItem>>> = _queue

    private val _currentMediaItemIndex = MutableStateFlow(0)
    val currentMediaItemIndex: StateFlow<Int> = _currentMediaItemIndex

    private val _currentMediaId = MutableStateFlow<String?>(null)
    val currentMediaId: StateFlow<String?> = _currentMediaId

    private var progressUpdateJob: Job? = null
    private var coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var currentSongId: Long? = null
    private var isCurrentSongLogged = false
    private val isFetchingNextPage = AtomicBoolean(false)
    private var hasCheckedForRestoredSession = false

    init {
        mediaBrowserFuture = MediaBrowser.Builder(context, SessionToken(context, serviceComponent)).buildAsync()
        mediaBrowserFuture.addListener({
            try {
                val browser = mediaBrowserFuture.get()
                this.mediaBrowser = browser
                browser.addListener(playerListener)
                _nowPlaying.value = browser.mediaMetadata
                _isPlaying.value = browser.isPlaying
                _isLoading.value = browser.isLoading
                _playerState.value = browser.playbackState
                _currentMediaItemIndex.value = browser.currentMediaItemIndex
                _currentMediaId.value = browser.currentMediaItem?.mediaId
                updateQueue()
                if (browser.playbackState == Player.STATE_READY || browser.isPlaying) {
                    startProgressUpdates()
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to connect to MediaBrowser")
            }
        }, MoreExecutors.directExecutor())
    }

    private fun runPlayerCommand(command: (browser: MediaBrowser) -> Unit) {
        val browser = this.mediaBrowser
        if (browser != null && browser.isConnected) {
            command(browser)
        } else {
            mediaBrowserFuture.addListener({
                try {
                    val connectedBrowser = mediaBrowserFuture.get()
                    command(connectedBrowser)
                } catch (e: Exception) {
                    Timber.tag(TAG)
                        .e(e, "Failed to execute command; MediaBrowser connection failed")
                }
            }, MoreExecutors.directExecutor())
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            _nowPlaying.value = mediaMetadata
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            updateQueue()
        }

        override fun onIsLoadingChanged(isLoading: Boolean) {
            _isLoading.value = isLoading
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _playerState.value = playbackState
            if (playbackState == Player.STATE_READY) {
                startProgressUpdates()
            } else if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                stopProgressUpdates()
                _playbackState.value = PlaybackState()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)

            val mediaId = mediaItem?.mediaId
            _currentMediaId.value = mediaId
            Timber.tag(TAG).d("Media item changed. Media ID from player: '$mediaId'")

            _currentMediaItemIndex.value = mediaBrowser?.currentMediaItemIndex ?: 0
            isCurrentSongLogged = false
            prefetchNextTrackStream()
            savePlaybackState()

            if (mediaId != null) {
                coroutineScope.launch(Dispatchers.IO) {
                    val song = if (mediaId.startsWith("http")) songDao.getSongByUrl(mediaId) else songDao.getSongByFilePath(mediaId)
                    currentSongId = song?.songId
                }
            }


            val currentQueueSize = mediaBrowser?.mediaItemCount ?: 0
            val currentIndex = mediaBrowser?.currentMediaItemIndex ?: 0
            if (currentQueueSize > 0 && currentIndex >= currentQueueSize - 3) {
                coroutineScope.launch {
                    if (isFetchingNextPage.compareAndSet(false, true)) {
                        val newStreamItems = playbackListManager.fetchNextPageStreamInfoItems()
                        if (!newStreamItems.isNullOrEmpty()) {
                            val newMediaItems = newStreamItems.mapNotNull { createMediaItemForItem(it) }
                            withContext(Dispatchers.Main) {
                                runPlayerCommand { browser ->
                                    browser.addMediaItems(newMediaItems)
                                    Timber.tag(TAG)
                                        .d("Added ${newMediaItems.size} prefetched items to the queue.")
                                }
                            }
                        }
                        isFetchingNextPage.set(false)
                    }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (!isPlaying) {
                savePlaybackState()
            }
        }
    }

    private fun updateQueue() {
        runPlayerCommand { browser ->
            val timeline = browser.currentTimeline
            if (timeline.isEmpty) {
                _queue.value = emptyList()
            } else {
                val window = Timeline.Window()
                val itemsWithUid = (0 until timeline.windowCount).map { i ->
                    timeline.getWindow(i, window)
                    window.uid.toString() to browser.getMediaItemAt(i)
                }
                _queue.value = itemsWithUid
            }
            _currentMediaItemIndex.value = browser.currentMediaItemIndex
        }
    }

    fun removeQueueItem(index: Int) {
        runPlayerCommand { browser ->
            if (index >= 0 && index < browser.mediaItemCount) {
                browser.removeMediaItem(index)
            }
        }
    }

    fun moveQueueItem(from: Int, to: Int) {
        runPlayerCommand { browser ->
            if (from >= 0 && from < browser.mediaItemCount && to >= 0 && to < browser.mediaItemCount) {
                browser.moveMediaItem(from, to)
            }
        }
    }

    fun skipToQueueItem(index: Int) {
        runPlayerCommand { browser ->
            if (index >= 0 && index < browser.mediaItemCount) {
                if (index != browser.currentMediaItemIndex) {
                    browser.seekTo(index, C.TIME_UNSET)
                }
                browser.play()
            }
        }
    }

    private fun savePlaybackState() {
        if (MusicService.isRestoring.value) {
            return
        }

        runPlayerCommand { browser ->
            coroutineScope.launch {
                if (browser.mediaItemCount == 0) {
                    withContext(Dispatchers.IO) {
                        playbackStateDao.clearState()
                    }
                    return@launch
                }

                val queue = (0 until browser.mediaItemCount).mapNotNull { i ->
                    browser.getMediaItemAt(i).mediaId
                }
                val currentIndex = browser.currentMediaItemIndex
                val currentPosition = if (browser.isPlaying) browser.currentPosition else browser.currentPosition.coerceAtLeast(0)
                val playing = browser.isPlaying

                if (queue.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        val state = PlaybackStateEntity(
                            queue = queue,
                            currentItemIndex = currentIndex,
                            currentPosition = currentPosition,
                            isPlaying = playing
                        )
                        playbackStateDao.saveState(state)
                        Timber.tag(TAG).d("Playback state saved.")
                    }
                }
            }
        }
    }

    private fun prefetchNextTrackStream() {
        runPlayerCommand { browser ->
            if (browser.mediaItemCount <= 1) return@runPlayerCommand

            val nextIndex = browser.nextMediaItemIndex
            if (nextIndex == C.INDEX_UNSET || nextIndex == browser.currentMediaItemIndex) return@runPlayerCommand

            val nextMediaItem = browser.getMediaItemAt(nextIndex)
            val nextMediaId = nextMediaItem.mediaId

            if (nextMediaId.startsWith("http")) {
                coroutineScope.launch(Dispatchers.IO) {
                    youtubeRepository.getStreamInfo(nextMediaId)
                }
            }
        }
    }

    suspend fun playSingleSong(item: Any) {
        val mediaItem = withContext(Dispatchers.IO) { createMediaItemForItem(item) } ?: return

        runPlayerCommand { browser ->
            val currentMediaId = browser.currentMediaItem?.mediaId
            val newMediaId = mediaItem.mediaId
            val isSameSong = newMediaId == currentMediaId

            // Debug logging to track the comparison
            Timber.tag(TAG).d("playSingleSong - Current: '$currentMediaId', New: '$newMediaId', Same: $isSameSong")

            if (isSameSong) {
                // Same song is already playing, just ensure it's playing and update queue display
                Timber.tag(TAG).d("Same song detected, not restarting playback")
                if (!browser.isPlaying) {
                    browser.play()
                }
                savePlaybackState()
            } else {
                // Different song, replace queue and start playback
                Timber.tag(TAG).d("Different song, starting new playback")
                playbackListManager.clearCurrentListContext()
                browser.setMediaItem(mediaItem)
                browser.prepare()
                hasCheckedForRestoredSession = false // Reset for new playback
                isCurrentSongLogged = false
                browser.play()
                savePlaybackState()
            }
        }
        updateQueue()
    }

    suspend fun playSongList(items: List<Any>, startIndex: Int) {
        if (items.isEmpty()) return

        // First, create only the starting song's media item for immediate playback
        val startingSong = items.getOrNull(startIndex) ?: return
        val startingMediaItem = withContext(Dispatchers.IO) {
            createMediaItemForItem(startingSong)
        } ?: return

        runPlayerCommand { browser ->
            val oldCurrentMediaId = browser.currentMediaItem?.mediaId
            val currentMediaId = startingMediaItem.mediaId
            val isSameSong = currentMediaId == oldCurrentMediaId

            // Debug logging to track the comparison
            Timber.tag(TAG).d("playSongList - Current: '$oldCurrentMediaId', New: '$currentMediaId', Same: $isSameSong")

            if (isSameSong) {
                // Same song is already playing, update queue without interrupting playback
                Timber.tag(TAG).d("Same song detected in playSongList, updating queue without restarting playback")

                // Ensure it's playing if it was playing before
                if (!browser.isPlaying) {
                    browser.play()
                }
            } else {
                // Different song, start playback immediately with just the starting song
                Timber.tag(TAG).d("Different song in playSongList, starting new playback immediately")
                browser.setMediaItem(startingMediaItem)
                browser.prepare()
                hasCheckedForRestoredSession = false // Reset for new playback
                isCurrentSongLogged = false
                browser.play()
            }
        }

        // Update queue immediately with just the starting song
        updateQueue()

        // Now build the full queue in the background (after playback has started)
        coroutineScope.launch(Dispatchers.IO) {
            // Create all media items
            val allMediaItems = items.mapNotNull { createMediaItemForItem(it) }
            if (allMediaItems.isEmpty()) return@launch

            withContext(Dispatchers.Main) {
                runPlayerCommand { browser ->
                    val currentMediaId = browser.currentMediaItem?.mediaId
                    val targetMediaId = allMediaItems.getOrNull(startIndex)?.mediaId
                    val isSameSong = currentMediaId == targetMediaId

                    if (isSameSong) {
                        // Get current queue size
                        val currentQueueSize = browser.mediaItemCount
                        val currentIndex = browser.currentMediaItemIndex

                        // Calculate items to add
                        val itemsToAddAfter = allMediaItems.drop(startIndex + 1)
                        val itemsToAddBefore = allMediaItems.take(startIndex)

                        // Remove all items except the current one (from the end to avoid index shifts)
                        for (i in (currentQueueSize - 1) downTo 0) {
                            if (i != currentIndex) {
                                browser.removeMediaItem(i)
                            }
                        }

                        // Add items before the current song
                        if (itemsToAddBefore.isNotEmpty()) {
                            browser.addMediaItems(0, itemsToAddBefore)
                        }

                        // Add items after the current song (at the end)
                        if (itemsToAddAfter.isNotEmpty()) {
                            browser.addMediaItems(itemsToAddAfter)
                        }

                        Timber.tag(TAG).d("Queue updated with ${allMediaItems.size} items")
                    } else {
                        // Rare case: song changed between starting playback and building queue
                        // Just add remaining items to queue
                        val itemsToAdd = allMediaItems.filterNot { it.mediaId == currentMediaId }
                        if (itemsToAdd.isNotEmpty()) {
                            browser.addMediaItems(itemsToAdd)
                            Timber.tag(TAG).d("Added ${itemsToAdd.size} remaining items to queue")
                        }
                    }

                    // Update queue after the full queue is built
                    updateQueue()
                }
            }
        }
    }

    suspend fun shuffleSongList(items: List<Any>, startIndex: Int) {
        playbackListManager.clearCurrentListContext()
        if (items.isEmpty() || startIndex !in items.indices) return

        val selectedItem = items[startIndex]
        val remainingItems = items.toMutableList().apply { removeAt(startIndex) }.shuffled()
        val shuffledList = listOf(selectedItem) + remainingItems

        playSongList(shuffledList, 0)
    }

    suspend fun shuffleQueuePreservingState(items: List<Any>, startIndex: Int) {
        playbackListManager.clearCurrentListContext()
        if (items.isEmpty() || startIndex !in items.indices) return

        val selectedItem = items[startIndex]
        val remainingItems = items.toMutableList().apply { removeAt(startIndex) }.shuffled()
        val shuffledList = listOf(selectedItem) + remainingItems

        // Create all media items
        val allMediaItems = withContext(Dispatchers.IO) {
            shuffledList.mapNotNull { createMediaItemForItem(it) }
        }
        if (allMediaItems.isEmpty()) return

        runPlayerCommand { browser ->
            val currentMediaId = browser.currentMediaItem?.mediaId
            val wasPlaying = browser.isPlaying
            val currentPosition = browser.currentPosition

            // Find the current song in the shuffled list
            val currentSongIndexInShuffled = allMediaItems.indexOfFirst { it.mediaId == currentMediaId }

            if (currentSongIndexInShuffled >= 0) {
                // Current song is in the shuffled list - do in-place shuffle without restarting playback

                // First, remove all items except the currently playing one (from end to start to avoid index shifts)
                val currentIndex = browser.currentMediaItemIndex
                for (i in (browser.mediaItemCount - 1) downTo 0) {
                    if (i != currentIndex) {
                        browser.removeMediaItem(i)
                    }
                }

                // Now add the shuffled items around the current song
                // Add items that should come before the current song
                val itemsBefore = allMediaItems.take(currentSongIndexInShuffled)
                if (itemsBefore.isNotEmpty()) {
                    browser.addMediaItems(0, itemsBefore)
                }

                // Add items that should come after the current song (at the end)
                val itemsAfter = allMediaItems.drop(currentSongIndexInShuffled + 1)
                if (itemsAfter.isNotEmpty()) {
                    browser.addMediaItems(itemsAfter)
                }

                // No need to restore playing state since we never interrupted it
            } else {
                // Fallback: current song not found in shuffled list, use the old method
                browser.setMediaItems(allMediaItems, 0, currentPosition)
                browser.prepare()

                if (wasPlaying) {
                    browser.play()
                } else {
                    browser.pause()
                }
            }
        }

        // Update the queue state
        updateQueue()
    }

    fun playNext(item: Any) {
        coroutineScope.launch {
            val mediaItem = createMediaItemForItem(item) ?: return@launch
            runPlayerCommand { browser ->
                val nextIndex = if (browser.mediaItemCount == 0) 0 else browser.currentMediaItemIndex + 1
                browser.addMediaItem(nextIndex, mediaItem)
                Timber.tag(TAG).d("Added to play next: ${mediaItem.mediaMetadata.title}")
            }
        }
    }

    fun addToQueue(item: Any) {
        coroutineScope.launch {
            val mediaItem = createMediaItemForItem(item) ?: return@launch
            runPlayerCommand { browser ->
                browser.addMediaItem(mediaItem)
                Timber.tag(TAG).d("Added to end of queue: ${mediaItem.mediaMetadata.title}")
            }
        }
    }

    private fun isUriValid(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.close() }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun createMediaItemForItem(item: Any): MediaItem? {
        return withContext(Dispatchers.IO) {
            val song = when (item) {
                is Song -> item
                is StreamInfoItem -> playlistManager.cacheAndGetSong(item)
                else -> null
            } ?: return@withContext null

            if (song.localFilePath != null) {
                val uri = song.localFilePath!!.toUri()
                if (isUriValid(uri)) {
                    return@withContext createLocalMediaItem(song)
                }
            }
            return@withContext createStreamingMediaItem(song)
        }
    }

    private suspend fun createMediaMetadata(song: Song): MediaMetadata {
        // Use high-quality thumbnail URL for better resolution in player
        val highQualityThumbnailUrl = getHighQualityThumbnailUrl(song.videoId)
        val artworkUri = if (highQualityThumbnailUrl.isNotEmpty()) {
            highQualityThumbnailUrl.toUri()
        } else {
            song.thumbnailUrl.toUri()
        }

        val builder = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setArtworkUri(artworkUri)

        return builder.build()
    }

    private suspend fun createLocalMediaItem(song: Song): MediaItem {
        val mediaMetadata = createMediaMetadata(song)
        val normalizedUrl = song.youtubeUrl.replace("music.youtube.com", "www.youtube.com")
        return MediaItem.Builder()
            .setUri(song.localFilePath!!.toUri())
            .setMediaId(normalizedUrl) // Use normalized URL for consistency
            .setMediaMetadata(mediaMetadata)
            .build()
    }

    private suspend fun createStreamingMediaItem(song: Song): MediaItem? {
        val normalizedUrl = song.youtubeUrl.replace("music.youtube.com", "www.youtube.com")
        val streamInfo = youtubeRepository.getStreamInfo(normalizedUrl)
        val audioStream = streamInfo?.audioStreams?.maxByOrNull { it.bitrate }

        return if (audioStream?.url != null) {
            val mediaMetadata = createMediaMetadata(song)
            MediaItem.Builder()
                .setUri(audioStream.url.toString())
                .setMediaId(normalizedUrl)
                .setMediaMetadata(mediaMetadata)
                .build()
        } else {
            null
        }
    }

    fun togglePlayPause() {
        runPlayerCommand { browser ->
            if (browser.isPlaying) browser.pause() else browser.play()
        }
    }

    fun skipToNext() {
        runPlayerCommand { it.seekToNext() }
    }

    fun skipToPrevious() {
        runPlayerCommand { it.seekToPrevious() }
    }

    fun seekTo(position: Long) {
        runPlayerCommand { it.seekTo(position) }
    }

    fun setRepeatMode(@Player.RepeatMode repeatMode: Int) {
        runPlayerCommand { browser ->
            browser.repeatMode = repeatMode
        }
    }

    fun setShuffleModeEnabled(shuffleEnabled: Boolean) {
        runPlayerCommand { browser ->
            browser.shuffleModeEnabled = shuffleEnabled
        }
    }

    fun getCurrentRepeatMode(): Int {
        return mediaBrowser?.repeatMode ?: Player.REPEAT_MODE_OFF
    }

    fun isShuffleModeEnabled(): Boolean {
        return mediaBrowser?.shuffleModeEnabled ?: false
    }

    // Get the current timeline order (which reflects shuffle if enabled)
    fun getCurrentTimelineOrder(): List<Pair<String, MediaItem>> {
        return mediaBrowser?.let { browser ->
            val timeline = browser.currentTimeline
            if (timeline.isEmpty) {
                emptyList()
            } else {
                val window = Timeline.Window()
                (0 until timeline.windowCount).map { i ->
                    timeline.getWindow(i, window)
                    window.uid.toString() to browser.getMediaItemAt(i)
                }
            }
        } ?: emptyList()
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()

        if (!hasCheckedForRestoredSession) {
            val browser = mediaBrowser
            if (browser != null && browser.currentPosition > 1000) {
                if (browser.currentPosition >= 30000) {
                    isCurrentSongLogged = true
                }
            }
            hasCheckedForRestoredSession = true
        }

        progressUpdateJob = coroutineScope.launch {
            var saveCounter = 0
            while (true) {
                val currentPosition = mediaBrowser?.currentPosition ?: 0
                val totalDuration = mediaBrowser?.duration?.takeIf { it > 0 } ?: 0
                _playbackState.value = PlaybackState(currentPosition, totalDuration)

                if (!isCurrentSongLogged && currentPosition >= 30000) {
                    currentSongId?.let { songId ->
                        Timber.tag(TAG).d("LOGGING PLAY for song ID: $songId")
                        withContext(Dispatchers.IO) {
                            listeningHistoryDao.insertPlayLog(
                                ListeningHistory(songId = songId, timestamp = System.currentTimeMillis())
                            )
                            songDao.incrementPlayCount(songId)
                        }
                        isCurrentSongLogged = true
                    }
                }

                saveCounter++
                if (saveCounter >= 5) {
                    savePlaybackState()
                    saveCounter = 0
                }

                delay(1000)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

}

data class PlaybackState(
    val currentPosition: Long = 0,
    val totalDuration: Long = 0
)