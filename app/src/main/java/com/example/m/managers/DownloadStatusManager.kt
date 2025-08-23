package com.example.m.managers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DownloadStatus {
    data object Queued : DownloadStatus
    data class Downloading(val progress: Int) : DownloadStatus // Progress 0-100
    data object Failed : DownloadStatus
}

@Singleton
class DownloadStatusManager @Inject constructor() {
    private val _statuses = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, DownloadStatus>> = _statuses.asStateFlow()

    fun setQueued(url: String) {
        _statuses.update { currentStatuses ->
            currentStatuses.toMutableMap().apply { this[url] = DownloadStatus.Queued }
        }
    }

    fun setDownloading(url: String, progress: Int) {
        _statuses.update { currentStatuses ->
            currentStatuses.toMutableMap().apply { this[url] = DownloadStatus.Downloading(progress) }
        }
    }

    fun setFailed(url: String) {
        _statuses.update { currentStatuses ->
            currentStatuses.toMutableMap().apply { this[url] = DownloadStatus.Failed }
        }
    }

    fun removeStatus(url: String) {
        _statuses.update { currentStatuses ->
            currentStatuses.toMutableMap().apply { remove(url) }
        }
    }
}