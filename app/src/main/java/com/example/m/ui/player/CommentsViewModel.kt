// file: com/example/m/ui/player/CommentsViewModel.kt
package com.example.m.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.repository.YoutubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import javax.inject.Inject

data class CommentsUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val comments: List<CommentsInfoItem> = emptyList(),
    val error: String? = null,
    internal val commentsInfo: CommentsInfo? = null,
    internal val nextPage: Page? = null
)

@HiltViewModel
class CommentsViewModel @Inject constructor(
    private val youtubeRepository: YoutubeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommentsUiState())
    val uiState: StateFlow<CommentsUiState> = _uiState.asStateFlow()

    fun loadInitialComments(url: String?) {
        if (url == null || url == _uiState.value.commentsInfo?.url) return

        viewModelScope.launch {
            _uiState.value = CommentsUiState(isLoading = true) // Reset state
            val result = youtubeRepository.getComments(url)
            _uiState.update {
                if (result == null) {
                    it.copy(isLoading = false, error = "Could not load comments.")
                } else if (result.isCommentsDisabled) {
                    it.copy(isLoading = false, error = "Comments are disabled for this video.")
                } else {
                    it.copy(
                        isLoading = false,
                        commentsInfo = result,
                        comments = result.relatedItems,
                        nextPage = result.nextPage,
                        error = null
                    )
                }
            }
        }
    }

    fun loadMoreComments() {
        val currentState = _uiState.value
        if (currentState.isLoading || currentState.isLoadingMore || currentState.nextPage == null || currentState.commentsInfo == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val result = youtubeRepository.getMoreComments(currentState.commentsInfo, currentState.nextPage)
            _uiState.update {
                it.copy(
                    isLoadingMore = false,
                    comments = it.comments + (result?.items ?: emptyList()),
                    nextPage = result?.nextPage
                )
            }
        }
    }
}