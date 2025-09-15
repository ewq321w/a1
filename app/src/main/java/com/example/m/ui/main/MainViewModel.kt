// file: com/example/m/ui/main/MainViewModel.kt
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

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
    @ApplicationContext private val context: Context
) : ViewModel() {
    val nowPlaying = musicServiceConnection.nowPlaying
    val isPlaying = musicServiceConnection.isPlaying
    val isLoading = musicServiceConnection.isLoading
    val playerState = musicServiceConnection.playerState
    val playbackState = musicServiceConnection.playbackState

    private val _uiState = mutableStateOf(MainUiState())
    val uiState: State<MainUiState> = _uiState

    private val _isDoingMaintenance = mutableStateOf(false)
    val isDoingMaintenance: State<Boolean> = _isDoingMaintenance

    private val _maintenanceResult = mutableStateOf<String?>(null)
    val maintenanceResult: State<String?> = _maintenanceResult

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