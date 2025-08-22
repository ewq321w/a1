package com.example.m.ui.main

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.database.DownloadQueueDao
import com.example.m.download.DownloadService
import com.example.m.playback.MusicServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val musicServiceConnection: MusicServiceConnection,
    private val downloadQueueDao: DownloadQueueDao, // <<< INJECT THE DAO
    @ApplicationContext private val context: Context // <<< INJECT THE CONTEXT
) : ViewModel() {
    val nowPlaying = musicServiceConnection.nowPlaying
    val isPlaying = musicServiceConnection.isPlaying
    val playbackState = musicServiceConnection.playbackState

    val isPlayerScreenVisible = mutableStateOf(false)

    /**
     * [NEW FUNCTION]
     * Checks if there are items in the download queue and starts the service if needed.
     */
    fun checkAndResumeDownloadQueue() {
        viewModelScope.launch {
            // We only need the next item to see if the queue is non-empty
            if (downloadQueueDao.getNextItem() != null) {
                val intent = Intent(context, DownloadService::class.java).apply {
                    action = DownloadService.ACTION_PROCESS_QUEUE
                }
                context.startService(intent)
            }
        }
    }

    fun showPlayerScreen() {
        isPlayerScreenVisible.value = true
    }

    fun hidePlayerScreen() {
        isPlayerScreenVisible.value = false
    }

    fun togglePlayPause() {
        musicServiceConnection.togglePlayPause()
    }

    fun skipToNext() {
        musicServiceConnection.skipToNext()
    }

    fun skipToPrevious() {
        musicServiceConnection.skipToPrevious()
    }

    fun seekTo(position: Long) {
        musicServiceConnection.seekTo(position.toLong())
    }
}