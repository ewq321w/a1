package com.example.m.playback

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import coil.ImageLoader
import coil.request.ImageRequest
import com.example.m.data.database.*
import com.example.m.data.repository.YoutubeRepository
import com.example.m.managers.PlaylistManager
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

private const val TAG = "MusicServiceConnection"

@Singleton
class MusicServiceConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youtubeRepository: YoutubeRepository,
    private val songDao: SongDao,
    private val listeningHistoryDao: ListeningHistoryDao,
    private val playbackStateDao: PlaybackStateDao,
    private val imageLoader: ImageLoader,
    private val playlistManager: PlaylistManager
) {
    private val serviceComponent = ComponentName(context, MusicService::class.java)
    private var mediaBrowserFuture: ListenableFuture<MediaBrowser> =
        MediaBrowser.Builder(context, SessionToken(context, serviceComponent)).buildAsync()

    private val mediaBrowser: MediaBrowser?
        get() = if (mediaBrowserFuture.isDone) mediaBrowserFuture.get() else null

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
    private var isRestored = false
    private var restoreJob: Job? = null

    init {
        mediaBrowserFuture.addListener({
            val browser = this.mediaBrowser ?: return@addListener

            if (!isRestored) {
                restoreJob = coroutineScope.launch {
                    restoreQueue()
                    isRestored = true
                }
            }

            browser.addListener(object : Player.Listener {
                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    _nowPlaying.value = mediaMetadata
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    super.onMediaItemTransition(mediaItem, reason)
                    val mediaId = mediaItem?.mediaId ?: return
                    Log.d(TAG, "Media item changed. Media ID from player: '$mediaId'")

                    isCurrentSongLogged = false
                    prefetchNextTrack()
                    savePlaybackState()

                    coroutineScope.launch(Dispatchers.IO) {
                        val song = if (mediaId.startsWith("http")) songDao.getSongByUrl(mediaId) else songDao.getSongByFilePath(mediaId)
                        currentSongId = song?.songId
                    }

                    if (mediaItem != null && mediaItem.mediaMetadata.artworkData == null) {
                        coroutineScope.launch {
                            updateArtworkData(mediaItem)
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    if (isPlaying) {
                        startProgressUpdates()
                        prefetchNextTrack()
                    } else {
                        stopProgressUpdates()
                        savePlaybackState()
                    }
                }
            })
        }, MoreExecutors.directExecutor())
    }

    private suspend fun updateArtworkData(mediaItem: MediaItem) {
        val browser = this.mediaBrowser ?: return
        val itemIndex = browser.currentMediaItemIndex
        if (itemIndex == C.INDEX_UNSET) return

        if (browser.getMediaItemAt(itemIndex).mediaId != mediaItem.mediaId) return

        val artworkUri = mediaItem.mediaMetadata.artworkUri?.toString()
        val artworkData = getCroppedSquareBitmap(artworkUri)

        if (artworkData != null) {
            val newMetadata = mediaItem.mediaMetadata.buildUpon()
                .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                .build()
            val newItem = mediaItem.buildUpon().setMediaMetadata(newMetadata).build()

            withContext(Dispatchers.Main) {
                if (browser.currentMediaItemIndex == itemIndex) {
                    browser.replaceMediaItem(itemIndex, newItem)
                    Log.d(TAG, "Artwork data updated for notification.")
                }
            }
        }
    }

    private suspend fun restoreQueue() {
        yield()
        val browser = this.mediaBrowser ?: return
        if (browser.mediaItemCount > 0) return

        val savedState = playbackStateDao.getState() ?: return
        if (savedState.queue.isEmpty()) {
            playbackStateDao.clearState()
            return
        }
        Log.d(TAG, "Restoring queue...")

        val startIndex = savedState.currentItemIndex.coerceIn(0, savedState.queue.size - 1)
        val currentItemUrl = savedState.queue[startIndex]
        val currentItemObject = songDao.getSongByUrl(currentItemUrl) ?: songDao.getSongByFilePath(currentItemUrl)
        ?: StreamInfoItem(0, currentItemUrl, currentItemUrl, StreamType.AUDIO_STREAM)

        val currentMediaItem = withContext(Dispatchers.IO) { createMediaItemForItem(currentItemObject) }
        if (currentMediaItem != null) {
            browser.setMediaItem(currentMediaItem, savedState.currentPosition)
            browser.prepare()
            Log.d(TAG, "Restored current song. Loading rest of queue in background.")
        } else {
            Log.e(TAG, "Failed to restore current song, clearing state.")
            playbackStateDao.clearState()
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            val itemsBefore = savedState.queue.subList(0, startIndex)
            val itemsAfter = savedState.queue.subList(startIndex + 1, savedState.queue.size)

            val mediaItemsBefore = itemsBefore.mapNotNull { url ->
                val item = songDao.getSongByUrl(url) ?: songDao.getSongByFilePath(url)
                ?: StreamInfoItem(0, url, url, StreamType.AUDIO_STREAM)
                createMediaItemForItem(item)
            }

            val mediaItemsAfter = itemsAfter.mapNotNull { url ->
                val item = songDao.getSongByUrl(url) ?: songDao.getSongByFilePath(url)
                ?: StreamInfoItem(0, url, url, StreamType.AUDIO_STREAM)
                createMediaItemForItem(item)
            }

            withContext(Dispatchers.Main) {
                browser.addMediaItems(0, mediaItemsBefore)
                browser.addMediaItems(mediaItemsAfter)
                Log.d(TAG, "Finished restoring rest of queue.")
            }
            savePlaybackState()
        }
    }

    private fun savePlaybackState() {
        val browser = this.mediaBrowser
        coroutineScope.launch {
            if (browser == null || browser.mediaItemCount == 0) {
                withContext(Dispatchers.IO) {
                    playbackStateDao.clearState()
                }
                return@launch
            }

            val queue = (0 until browser.mediaItemCount).mapNotNull { i ->
                browser.getMediaItemAt(i).mediaId
            }
            val currentIndex = browser.currentMediaItemIndex
            val currentPosition = browser.currentPosition
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

    private fun prefetchNextTrack() {
        val browser = this.mediaBrowser ?: return
        if (browser.mediaItemCount <= 1) return

        val nextIndex = browser.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET || nextIndex == browser.currentMediaItemIndex) return

        val nextMediaItem = browser.getMediaItemAt(nextIndex)
        val nextMediaId = nextMediaItem.mediaId

        if (nextMediaId.startsWith("http")) {
            coroutineScope.launch(Dispatchers.IO) {
                youtubeRepository.getStreamInfo(nextMediaId)
            }
        }
    }

    suspend fun playSingleSong(item: Any) {
        if (restoreJob?.isActive == true) {
            restoreJob?.cancel()
            Log.d(TAG, "Cancelling restore job due to new playback request.")
        }
        val browser = this.mediaBrowser ?: return
        val mediaItem = withContext(Dispatchers.IO) { createMediaItemForItem(item) } ?: return

        browser.setMediaItem(mediaItem)
        browser.prepare()
        browser.play()
        savePlaybackState()
    }

    suspend fun playSongList(items: List<Any>, startIndex: Int) {
        if (restoreJob?.isActive == true) {
            restoreJob?.cancel()
            Log.d(TAG, "Cancelling restore job due to new playback request.")
        }
        val browser = this.mediaBrowser ?: return
        if (items.isEmpty()) return

        val firstItem = items.getOrNull(startIndex) ?: return
        val firstMediaItem = withContext(Dispatchers.IO) { createMediaItemForItem(firstItem) } ?: return

        browser.setMediaItem(firstMediaItem)
        browser.prepare()
        browser.play()

        coroutineScope.launch(Dispatchers.IO) {
            val itemsAfter = items.subList(startIndex + 1, items.size)
            val itemsBefore = items.subList(0, startIndex)

            val mediaItemsAfter = itemsAfter.mapNotNull { createMediaItemForItem(it) }
            val mediaItemsBefore = itemsBefore.mapNotNull { createMediaItemForItem(it) }

            withContext(Dispatchers.Main) {
                browser.addMediaItems(0, mediaItemsBefore)
                browser.addMediaItems(mediaItemsAfter)
            }
            savePlaybackState()
        }
    }

    suspend fun shuffleSongList(items: List<Any>, startIndex: Int) {
        if (items.isEmpty() || startIndex !in items.indices) return

        val selectedItem = items[startIndex]
        val remainingItems = items.toMutableList().apply { removeAt(startIndex) }.shuffled()
        val shuffledList = listOf(selectedItem) + remainingItems

        playSongList(shuffledList, 0)
    }

    fun playNext(item: Any) {
        val browser = this.mediaBrowser ?: return
        coroutineScope.launch {
            val mediaItem = createMediaItemForItem(item) ?: return@launch
            val nextIndex = if (browser.mediaItemCount == 0) 0 else browser.currentMediaItemIndex + 1
            browser.addMediaItem(nextIndex, mediaItem)
            Log.d(TAG, "Added to play next: ${mediaItem.mediaMetadata.title}")
        }
    }

    fun addToQueue(item: Any) {
        val browser = this.mediaBrowser ?: return
        coroutineScope.launch {
            val mediaItem = createMediaItemForItem(item) ?: return@launch
            browser.addMediaItem(mediaItem)
            Log.d(TAG, "Added to end of queue: ${mediaItem.mediaMetadata.title}")
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

    private suspend fun createMediaItemForItem(item: Any): MediaItem? {
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
            // Fallback to streaming if local file path is null or invalid
            return@withContext createStreamingMediaItem(song)
        }
    }

    private fun createMediaMetadata(song: Song): MediaMetadata {
        return MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setArtworkUri(song.thumbnailUrl.toUri())
            .build()
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

    private suspend fun getCroppedSquareBitmap(imageUrl: String?): ByteArray? {
        if (imageUrl.isNullOrBlank()) return null
        return try {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .build()
            val result = (imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap ?: return null
            val width = result.width
            val height = result.height
            val size = min(width, height)
            val x = (width - size) / 2
            val y = (height - size) / 2
            val squaredBitmap = Bitmap.createBitmap(result, x, y, size, size)
            val stream = ByteArrayOutputStream()
            squaredBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun togglePlayPause() {
        mediaBrowser?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun skipToNext() {
        mediaBrowser?.seekToNext()
        mediaBrowser?.play()
    }

    fun skipToPrevious() {
        mediaBrowser?.seekToPrevious()
        mediaBrowser?.play()
    }

    fun seekTo(position: Long) {
        mediaBrowser?.seekTo(position)
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
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