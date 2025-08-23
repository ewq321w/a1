package com.example.m.ui.main

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.database.DownloadQueueDao
import com.example.m.data.repository.LibraryRepository
import com.example.m.download.DownloadService
import com.example.m.playback.MusicServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val musicServiceConnection: MusicServiceConnection,
    private val downloadQueueDao: DownloadQueueDao,
    private val libraryRepository: LibraryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val nowPlaying = musicServiceConnection.nowPlaying
    val isPlaying = musicServiceConnection.isPlaying
    val playbackState = musicServiceConnection.playbackState

    val isPlayerScreenVisible = mutableStateOf(false)

    private val _isDoingMaintenance = mutableStateOf(false)
    val isDoingMaintenance: State<Boolean> = _isDoingMaintenance

    private val _maintenanceResult = mutableStateOf<String?>(null)
    val maintenanceResult: State<String?> = _maintenanceResult

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
            val reQueuedCount: Int
            val cleanedCount: Int
            val fixedLinksCount: Int
            val cleanedArtistsCount: Int
            val cleanedSongsCount: Int

            withContext(Dispatchers.IO) {
                reQueuedCount = libraryRepository.verifyLibraryEntries()
                cleanedCount = libraryRepository.cleanOrphanedFiles()
                fixedLinksCount = libraryRepository.fixMissingArtistLinks()
                cleanedArtistsCount = libraryRepository.cleanOrphanedArtists()
                cleanedSongsCount = libraryRepository.cleanOrphanedSongs()
            }

            val results = listOfNotNull(
                if (reQueuedCount > 0) "$reQueuedCount songs re-queued" else null,
                if (cleanedCount > 0) "$cleanedCount orphaned files deleted" else null,
                if (fixedLinksCount > 0) "$fixedLinksCount artist links fixed" else null,
                if (cleanedArtistsCount > 0) "$cleanedArtistsCount orphaned artists removed" else null,
                if (cleanedSongsCount > 0) "$cleanedSongsCount cached songs removed" else null
            )

            _maintenanceResult.value = if (results.isEmpty()) {
                "Library check complete. No issues found."
            } else {
                "Check complete: ${results.joinToString(", ")}."
            }

            if (reQueuedCount > 0) {
                val intent = Intent(context, DownloadService::class.java).apply {
                    action = DownloadService.ACTION_PROCESS_QUEUE
                }
                context.startService(intent)
            }
            _isDoingMaintenance.value = false
        }
    }

    fun clearMaintenanceResult() {
        _maintenanceResult.value = null
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