// file: com/example/m/ui/settings/BackupRestoreViewModel.kt
package com.example.m.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.database.DatabaseBackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupRestoreUiState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val exportSuccess: Boolean? = null,
    val importSuccess: Boolean? = null,
    val errorMessage: String? = null,
    val databaseSize: String = "Calculating..."
)

sealed interface BackupRestoreEvent {
    data class ExportDatabase(val destinationUri: Uri) : BackupRestoreEvent
    data class ImportDatabase(val sourceUri: Uri) : BackupRestoreEvent
    data object DismissMessage : BackupRestoreEvent
    data object RefreshDatabaseSize : BackupRestoreEvent
}

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    private val backupManager: DatabaseBackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupRestoreUiState())
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    init {
        refreshDatabaseSize()
    }

    fun onEvent(event: BackupRestoreEvent) {
        when (event) {
            is BackupRestoreEvent.ExportDatabase -> exportDatabase(event.destinationUri)
            is BackupRestoreEvent.ImportDatabase -> importDatabase(event.sourceUri)
            is BackupRestoreEvent.DismissMessage -> dismissMessage()
            is BackupRestoreEvent.RefreshDatabaseSize -> refreshDatabaseSize()
        }
    }

    private fun exportDatabase(destinationUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportSuccess = null, errorMessage = null) }

            val result = backupManager.exportDatabaseToUri(destinationUri)

            _uiState.update {
                it.copy(
                    isExporting = false,
                    exportSuccess = result.isSuccess,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    private fun importDatabase(sourceUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importSuccess = null, errorMessage = null) }

            val result = backupManager.importDatabaseFromUri(sourceUri)

            _uiState.update {
                it.copy(
                    isImporting = false,
                    importSuccess = result.isSuccess,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }

            if (result.isSuccess) {
                refreshDatabaseSize()
            }
        }
    }

    private fun dismissMessage() {
        _uiState.update {
            it.copy(
                exportSuccess = null,
                importSuccess = null,
                errorMessage = null
            )
        }
    }

    private fun refreshDatabaseSize() {
        viewModelScope.launch {
            val size = backupManager.getFormattedDatabaseSize()
            _uiState.update { it.copy(databaseSize = size) }
        }
    }

    fun getSuggestedBackupFileName(): String {
        return backupManager.getSuggestedBackupFileName()
    }
}

