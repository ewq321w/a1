// file: com/example/m/ui/library/LibraryViewModel.kt
package com.example.m.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.PreferencesManager
import com.example.m.data.database.*
import com.example.m.data.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val selectedView: String = "Playlists",
    val showManageGroupsDialog: Boolean = false,
    val libraryGroups: List<LibraryGroup> = emptyList(),
    val activeLibraryGroupId: Long = 0L,
)

sealed interface LibraryEvent {
    data class SetSelectedView(val view: String) : LibraryEvent
    object ManageGroupsClicked : LibraryEvent
    object ManageGroupsDismissed : LibraryEvent
    data class AddLibraryGroup(val name: String) : LibraryEvent
    data class RenameLibraryGroup(val group: LibraryGroup, val newName: String) : LibraryEvent
    data class DeleteLibraryGroup(val group: LibraryGroup) : LibraryEvent
    data class SetActiveLibraryGroup(val groupId: Long) : LibraryEvent
}


@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val libraryRepository: LibraryRepository,
    private val libraryGroupDao: LibraryGroupDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(selectedView = preferencesManager.lastLibraryView) }

        viewModelScope.launch {
            preferencesManager.getActiveLibraryGroupIdFlow().collect { groupId ->
                _uiState.update { it.copy(activeLibraryGroupId = groupId) }
            }
        }

        viewModelScope.launch {
            libraryGroupDao.getAllGroups().collect { groups ->
                _uiState.update { it.copy(libraryGroups = groups) }
            }
        }
    }

    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.SetSelectedView -> setSelectedView(event.view)
            is LibraryEvent.ManageGroupsClicked -> _uiState.update { it.copy(showManageGroupsDialog = true) }
            is LibraryEvent.ManageGroupsDismissed -> _uiState.update { it.copy(showManageGroupsDialog = false) }
            is LibraryEvent.AddLibraryGroup -> addLibraryGroup(event.name)
            is LibraryEvent.RenameLibraryGroup -> renameLibraryGroup(event.group, event.newName)
            is LibraryEvent.DeleteLibraryGroup -> deleteLibraryGroup(event.group)
            is LibraryEvent.SetActiveLibraryGroup -> preferencesManager.activeLibraryGroupId = event.groupId
        }
    }

    private fun setSelectedView(view: String) {
        _uiState.update { it.copy(selectedView = view) }
        preferencesManager.lastLibraryView = view
    }

    private fun addLibraryGroup(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryGroupDao.insertGroup(LibraryGroup(name = name.trim()))
        }
    }

    private fun renameLibraryGroup(group: LibraryGroup, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryGroupDao.updateGroup(group.copy(name = newName.trim()))
        }
    }

    private fun deleteLibraryGroup(group: LibraryGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_uiState.value.activeLibraryGroupId == group.groupId) {
                preferencesManager.activeLibraryGroupId = 0L
            }
            libraryRepository.deleteLibraryGroupAndContents(group.groupId)
        }
    }
}