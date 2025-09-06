// file: com/example/m/playback/MusicService.kt
package com.example.m.playback

import android.app.Service
import android.content.Intent
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.m.data.database.PlaybackStateDao
import com.example.m.data.database.SongDao
import com.example.m.data.repository.YoutubeRepository
import com.example.m.managers.PlaylistManager
import com.example.m.managers.ThumbnailProcessor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MusicService : MediaSessionService() {
    companion object {
        val isRestoring = MutableStateFlow(false)
    }

    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var mediaSession: MediaSession

    @Inject
    lateinit var thumbnailProcessor: ThumbnailProcessor

    @Inject
    lateinit var playbackStateDao: PlaybackStateDao
    @Inject
    lateinit var songDao: SongDao
    @Inject
    lateinit var youtubeRepository: YoutubeRepository
    @Inject
    lateinit var playlistManager: PlaylistManager

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            if (mediaItem?.mediaMetadata?.artworkData == null) {
                updateArtworkForCurrentItem()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return Service.START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        player.setAudioAttributes(audioAttributes, true)
        player.addListener(playerListener)

        serviceScope.launch {
            restoreQueueFromDatabase()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    private suspend fun restoreQueueFromDatabase() {
        isRestoring.value = true

        withContext(Dispatchers.Main) {
            if (player.mediaItemCount > 0) {
                isRestoring.value = false
                return@withContext
            }
        }

        val savedState = playbackStateDao.getState()
        if (savedState == null || savedState.queue.isEmpty()) {
            playbackStateDao.clearState()
            isRestoring.value = false
            return
        }

        val startIndex = savedState.currentItemIndex.coerceIn(0, savedState.queue.size - 1)
        val currentItemIdentifier = savedState.queue[startIndex]
        val currentMediaItem = buildMediaItem(currentItemIdentifier, withArtworkData = true)

        if (currentMediaItem != null) {
            withContext(Dispatchers.Main) {
                if (player.mediaItemCount > 0) {
                    isRestoring.value = false
                    return@withContext
                }
                player.setMediaItem(currentMediaItem, savedState.currentPosition)
                player.prepare()
            }
        } else {
            playbackStateDao.clearState()
            isRestoring.value = false
            return
        }

        serviceScope.launch {
            try {
                val itemsAfterIdentifiers = savedState.queue.subList(startIndex + 1, savedState.queue.size)
                val itemsBeforeIdentifiers = savedState.queue.subList(0, startIndex)

                val mediaItemsAfter = itemsAfterIdentifiers.mapNotNull { buildMediaItem(it, withArtworkData = false) }
                val mediaItemsBefore = itemsBeforeIdentifiers.mapNotNull { buildMediaItem(it, withArtworkData = false) }

                withContext(Dispatchers.Main) {
                    if (player.currentMediaItem?.mediaId != currentItemIdentifier) {
                        return@withContext
                    }

                    player.addMediaItems(mediaItemsAfter)
                    player.addMediaItems(0, mediaItemsBefore)
                }
            } finally {
                isRestoring.value = false
            }
        }
    }

    private suspend fun buildMediaItem(identifier: String, withArtworkData: Boolean = false): MediaItem? {
        val song = songDao.getSongByUrl(identifier) ?: songDao.getSongByFilePath(identifier) ?: return null

        val mediaMetadataBuilder = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setArtworkUri(song.thumbnailUrl.toUri())

        if (withArtworkData) {
            val artworkData = thumbnailProcessor.getCroppedSquareBitmap(song.thumbnailUrl)
            if (artworkData != null) {
                mediaMetadataBuilder.setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
        }

        val mediaMetadata = mediaMetadataBuilder.build()

        val uri = if (song.localFilePath != null) {
            song.localFilePath!!.toUri()
        } else {
            val streamInfo = youtubeRepository.getStreamInfo(song.youtubeUrl)
            streamInfo?.audioStreams?.maxByOrNull { it.bitrate }?.url?.toUri()
        }

        return if (uri != null) {
            MediaItem.Builder()
                .setUri(uri)
                .setMediaId(identifier)
                .setMediaMetadata(mediaMetadata)
                .build()
        } else {
            null
        }
    }

    private fun updateArtworkForCurrentItem() {
        serviceScope.launch {
            val mediaItemToUpdate = withContext(Dispatchers.Main) {
                player.currentMediaItem
            } ?: return@launch

            val mediaId = mediaItemToUpdate.mediaId

            val song = songDao.getSongByUrl(mediaId) ?: songDao.getSongByFilePath(mediaId) ?: return@launch

            val artworkBytes = thumbnailProcessor.getCroppedSquareBitmap(song.thumbnailUrl)
            if (artworkBytes != null) {
                val newMetadata = mediaItemToUpdate.mediaMetadata.buildUpon()
                    .setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    .build()
                val newMediaItem = mediaItemToUpdate.buildUpon().setMediaMetadata(newMetadata).build()

                withContext(Dispatchers.Main) {
                    if (player.currentMediaItem?.mediaId == mediaId) {
                        player.replaceMediaItem(player.currentMediaItemIndex, newMediaItem)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        player.removeListener(playerListener)
        mediaSession.release()
        player.release()
        super.onDestroy()
    }
}