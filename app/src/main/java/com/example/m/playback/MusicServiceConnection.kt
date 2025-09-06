// file: com/example/m/playback/MusicServiceConnection.kt
package com.example.m.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.example.m.data.database.*
import com.example.m.data.repository.YoutubeRepository
import com.example.m.managers.PlaybackListManager
import com.example.m.managers.PlaylistManager
import com.example.m.managers.ThumbnailProcessor
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.schabi.newpipe.extractor.stream.StreamInfoItem
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

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState

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
                if (browser.isPlaying) startProgressUpdates()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to MediaBrowser", e)
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
                    Log.e(TAG, "Failed to execute command; MediaBrowser connection failed", e)
                }
            }, MoreExecutors.directExecutor())
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            _nowPlaying.value = mediaMetadata
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)

            val mediaId = mediaItem?.mediaId ?: return
            Log.d(TAG, "Media item changed. Media ID from player: '$mediaId'")

            isCurrentSongLogged = false
            prefetchNextTrackStream()
            savePlaybackState()

            coroutineScope.launch(Dispatchers.IO) {
                val song = if (mediaId.startsWith("http")) songDao.getSongByUrl(mediaId) else songDao.getSongByFilePath(mediaId)
                currentSongId = song?.songId
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
                                    Log.d(TAG, "Added ${newMediaItems.size} prefetched items to the queue.")
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
            if (isPlaying) {
                startProgressUpdates()
                prefetchNextTrackStream()
            } else {
                stopProgressUpdates()
                savePlaybackState()
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
                        Log.d(TAG, "Playback state saved.")
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
        playbackListManager.clearCurrentListContext()
        val mediaItem = withContext(Dispatchers.IO) { createMediaItemForItem(item) } ?: return

        hasCheckedForRestoredSession = false // Reset for new playback
        isCurrentSongLogged = false
        runPlayerCommand { browser ->
            browser.setMediaItem(mediaItem)
            browser.prepare()
            browser.play()
            savePlaybackState()
        }
    }

    suspend fun playSongList(items: List<Any>, startIndex: Int) {
        if (items.isEmpty()) return

        val firstItem = items.getOrNull(startIndex) ?: return
        val firstMediaItem = withContext(Dispatchers.IO) { createMediaItemForItem(firstItem) } ?: return

        hasCheckedForRestoredSession = false // Reset for new playback
        isCurrentSongLogged = false

        runPlayerCommand { browser ->
            browser.setMediaItem(firstMediaItem)
            browser.prepare()
            browser.play()

            coroutineScope.launch(Dispatchers.IO) {
                playbackListManager.clearCurrentListContext()

                val windowSize = 10
                val chunkSize = 20

                val initialItemsAfter = items.subList(
                    fromIndex = startIndex + 1,
                    toIndex = minOf(items.size, startIndex + 1 + windowSize)
                )
                val initialItemsBefore = items.subList(
                    fromIndex = maxOf(0, startIndex - windowSize),
                    toIndex = startIndex
                )

                val mediaItemsAfter = initialItemsAfter.mapNotNull { createMediaItemForItem(it) }
                val mediaItemsBefore = initialItemsBefore.mapNotNull { createMediaItemForItem(it) }

                withContext(Dispatchers.Main) {
                    browser.addMediaItems(0, mediaItemsBefore)
                    browser.addMediaItems(mediaItemsAfter)
                }
                savePlaybackState()

                val remainingItemsAfter = items.subList(
                    fromIndex = minOf(items.size, startIndex + 1 + windowSize),
                    toIndex = items.size
                )
                val remainingItemsBefore = items.subList(
                    fromIndex = 0,
                    toIndex = maxOf(0, startIndex - windowSize)
                )

                remainingItemsAfter.chunked(chunkSize).forEach { chunk ->
                    val mediaItemsChunk = chunk.mapNotNull { createMediaItemForItem(it) }
                    if (mediaItemsChunk.isNotEmpty()) {
                        withContext(Dispatchers.Main) { browser.addMediaItems(mediaItemsChunk) }
                    }
                    delay(200)
                }

                remainingItemsBefore.chunked(chunkSize).reversed().forEach { chunk ->
                    val mediaItemsChunk = chunk.mapNotNull { createMediaItemForItem(it) }
                    if (mediaItemsChunk.isNotEmpty()) {
                        withContext(Dispatchers.Main) { browser.addMediaItems(0, mediaItemsChunk) }
                    }
                    delay(200)
                }
                savePlaybackState()
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

    fun playNext(item: Any) {
        coroutineScope.launch {
            val mediaItem = createMediaItemForItem(item) ?: return@launch
            runPlayerCommand { browser ->
                val nextIndex = if (browser.mediaItemCount == 0) 0 else browser.currentMediaItemIndex + 1
                browser.addMediaItem(nextIndex, mediaItem)
                Log.d(TAG, "Added to play next: ${mediaItem.mediaMetadata.title}")
            }
        }
    }

    fun addToQueue(item: Any) {
        coroutineScope.launch {
            val mediaItem = createMediaItemForItem(item) ?: return@launch
            runPlayerCommand { browser ->
                browser.addMediaItem(mediaItem)
                Log.d(TAG, "Added to end of queue: ${mediaItem.mediaMetadata.title}")
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
                    return@withContext createLocalMediaItem(song, uri)
                }
            }
            return@withContext createStreamingMediaItem(song)
        }
    }

    private suspend fun createMediaMetadata(song: Song): MediaMetadata {
        val artworkBytes = thumbnailProcessor.getCroppedSquareBitmap(song.thumbnailUrl)

        val builder = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setArtworkUri(song.thumbnailUrl.toUri())

        artworkBytes?.let {
            builder.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        return builder.build()
    }

    private suspend fun createLocalMediaItem(song: Song, uri: Uri): MediaItem {
        val mediaMetadata = createMediaMetadata(song)
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(uri.toString())
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
                        Log.d(TAG, "LOGGING PLAY for song ID: $songId")
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