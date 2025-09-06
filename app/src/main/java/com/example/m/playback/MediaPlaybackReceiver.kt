// file: com/example/m/playback/MediaPlaybackReceiver.kt
package com.example.m.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver

/**
 * A receiver for handling media button events.
 *
 * This receiver is declared in the AndroidManifest.xml to allow the system to start the
 * MusicService and resume playback when the app is not running. The parent class
 * implementation handles all the necessary logic.
 */
@OptIn(UnstableApi::class)
class MediaPlaybackReceiver : MediaButtonReceiver() {
    // No implementation is needed here. The parent class handles everything.
}