package com.example.m.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MusicService : MediaSessionService() {
    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var mediaSession: MediaSession

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

    override fun onCreate() {
        super.onCreate()
        // Configure ExoPlayer audio attributes for music playback.
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        player.setAudioAttributes(audioAttributes, true)
    }

    // Remember to release the player and session when the service is destroyed.
    override fun onDestroy() {
        mediaSession.release()
        player.release()
        super.onDestroy()
    }
}