// file: com/example/m/managers/LibraryActionsManager.kt
package com.example.m.managers

import com.example.m.data.PreferencesManager
import com.example.m.data.database.LibraryGroup
import com.example.m.data.database.LibraryGroupDao
import com.example.m.data.database.Song
import com.example.m.data.database.SongDao
import com.example.m.data.repository.ArtistGroupConflict
import com.example.m.data.repository.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DialogState {
    object Hidden : DialogState
    data class Conflict(
        val song: Song,
        val targetGroupId: Long, // FIX: Added target group ID
        val targetGroupName: String,
        val conflict: ArtistGroupConflict
    ) : DialogState
    data class CreateGroup(val isFirstGroup: Boolean) : DialogState
    data class SelectGroup(val groups: List<LibraryGroup>) : DialogState
}

@Singleton
class LibraryActionsManager @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val preferencesManager: PreferencesManager,
    private val libraryGroupDao: LibraryGroupDao,
    private val songDao: SongDao,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _dialogState = MutableStateFlow<DialogState>(DialogState.Hidden)
    val dialogState = _dialogState.asStateFlow()

    private var pendingItem: Any? = null

    fun addToLibrary(item: Any) {
        scope.launch {
            pendingItem = item
            val groups = libraryGroupDao.getAllGroups().first()
            if (groups.isEmpty()) {
                _dialogState.value = DialogState.CreateGroup(isFirstGroup = true)
                return@launch
            }

            val activeGroupId = preferencesManager.activeLibraryGroupId
            if (activeGroupId == 0L) {
                _dialogState.value = DialogState.SelectGroup(groups)
                return@launch
            }

            proceedWithAction(activeGroupId)
        }
    }

    fun onGroupSelected(groupId: Long) {
        preferencesManager.activeLibraryGroupId = groupId
        scope.launch {
            proceedWithAction(groupId)
        }
    }

    fun onCreateGroup(groupName: String) {
        scope.launch {
            val newGroupId = libraryGroupDao.insertGroup(LibraryGroup(name = groupName.trim()))
            preferencesManager.activeLibraryGroupId = newGroupId
            proceedWithAction(newGroupId)
        }
    }

    fun requestCreateGroup() {
        _dialogState.value = DialogState.CreateGroup(isFirstGroup = false)
    }

    fun onResolveConflict() {
        val currentState = _dialogState.value
        if (currentState !is DialogState.Conflict) return

        scope.launch {
            // FIX: Use the correct targetGroupId from the state
            libraryRepository.moveArtistToLibraryGroup(currentState.song.artist, currentState.targetGroupId)
            addSongToLibraryInternal(currentState.song, currentState.targetGroupId)
        }
        dismissDialog()
    }

    fun dismissDialog() {
        _dialogState.value = DialogState.Hidden
        pendingItem = null
    }

    private suspend fun proceedWithAction(groupId: Long) {
        val item = pendingItem ?: return
        val song = libraryRepository.getOrCreateSongFromItem(item, groupId)
        if (song.isInLibrary) {
            dismissDialog()
            return
        }

        val conflict = libraryRepository.checkArtistGroupConflict(song.artist, groupId)
        if (conflict != null) {
            val targetGroup = libraryGroupDao.getGroup(groupId)
            if (targetGroup != null) {
                // FIX: Pass the group ID along with the name to the state
                _dialogState.value = DialogState.Conflict(song, targetGroup.groupId, targetGroup.name, conflict)
            }
        } else {
            addSongToLibraryInternal(song, groupId)
            dismissDialog()
        }
    }

    private suspend fun addSongToLibraryInternal(song: Song, groupId: Long) {
        val updatedSong = song.copy(
            isInLibrary = true,
            dateAddedTimestamp = System.currentTimeMillis(),
            libraryGroupId = groupId
        )
        songDao.updateSong(updatedSong)
        libraryRepository.linkSongToArtist(updatedSong)
    }
}