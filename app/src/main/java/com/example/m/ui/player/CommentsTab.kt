// file: com/example/m/ui/player/CommentsTab.kt
package com.example.m.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.m.ui.common.ClickableHtmlText
import com.example.m.ui.main.MainEvent
import com.example.m.ui.main.MainViewModel
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.text.NumberFormat
import java.util.*

private fun parseTimestampFromUrl(url: String, currentVideoId: String?): Long? {
    if (currentVideoId == null) return null
    try {
        val uri = android.net.Uri.parse(url)
        val isYoutubeUrl = uri.host?.contains("youtube.com") == true
        val videoId = uri.getQueryParameter("v")

        // Check if the link is for the currently playing video.
        if (isYoutubeUrl && videoId == currentVideoId) {
            val timestamp = uri.getQueryParameter("t")
            // If there's a timestamp parameter, parse it.
            if (!timestamp.isNullOrBlank()) {
                return timestamp.removeSuffix("s").toLongOrNull()
            }
            // If there's NO timestamp parameter, it's a link to the start of the current video.
            return 0L
        }
    } catch (e: Exception) {
        return null // URL parsing failed.
    }
    // It's a link to a different video or not a valid YouTube URL for the current context.
    return null
}

private fun getYoutubeVideoIdFromUrl(url: String): String? {
    return try {
        val uri = android.net.Uri.parse(url)
        if (uri.host?.contains("youtube.com") == true && uri.path?.contains("watch") == true) {
            uri.getQueryParameter("v")
        } else if (uri.host?.contains("youtu.be") == true) {
            uri.lastPathSegment
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
fun CommentsTabContent(
    mainViewModel: MainViewModel = hiltViewModel(),
    viewModel: CommentsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val uriHandler = LocalUriHandler.current
    val currentMediaId by mainViewModel.currentMediaId.collectAsState()
    val currentVideoId = currentMediaId?.substringAfter("v=")?.substringBefore('&')

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            uiState.isLoading -> CircularProgressIndicator()
            uiState.error != null -> Text(uiState.error!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
            uiState.comments.isEmpty() -> Text("No comments found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.comments, key = { it.commentId }) { comment ->
                        CommentItem(
                            comment = comment,
                            onLinkClick = { url ->
                                val timestampSeconds = parseTimestampFromUrl(url, currentVideoId)
                                val clickedVideoId = getYoutubeVideoIdFromUrl(url)

                                when {
                                    timestampSeconds != null -> {
                                        mainViewModel.onEvent(MainEvent.SeekTo(timestampSeconds * 1000L))
                                    }
                                    clickedVideoId != null -> {
                                        val newItem = StreamInfoItem(
                                            ServiceList.YouTube.serviceId,
                                            url,
                                            "Loading...",
                                            StreamType.AUDIO_STREAM
                                        )
                                        mainViewModel.playSingleSong(newItem)
                                    }
                                    else -> {
                                        try {
                                            uriHandler.openUri(url)
                                        } catch (_: Exception) {
                                            // Ignore invalid URLs
                                        }
                                    }
                                }
                            }
                        )
                    }
                    if (uiState.isLoadingMore) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }

                val isScrolledToEnd by remember {
                    derivedStateOf {
                        val layoutInfo = listState.layoutInfo
                        val visibleItemsInfo = layoutInfo.visibleItemsInfo
                        if (layoutInfo.totalItemsCount == 0) {
                            false
                        } else {
                            val lastVisibleItem = visibleItemsInfo.last()
                            val viewportEndOffset = layoutInfo.viewportEndOffset
                            lastVisibleItem.index + 1 == layoutInfo.totalItemsCount && lastVisibleItem.offset + lastVisibleItem.size <= viewportEndOffset
                        }
                    }
                }

                LaunchedEffect(isScrolledToEnd) {
                    if (isScrolledToEnd) {
                        viewModel.loadMoreComments()
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentItem(
    comment: CommentsInfoItem,
    onLinkClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        AsyncImage(
            model = comment.uploaderAvatars.maxByOrNull { it.width }?.url,
            contentDescription = "${comment.uploaderName}'s avatar",
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.uploaderName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = comment.textualUploadDate ?: "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))

            var isExpanded by remember { mutableStateOf(false) }
            var canExpand by remember { mutableStateOf(false) }

            ClickableHtmlText(
                html = comment.commentText.content,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                onClick = onLinkClick,
                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { textLayoutResult ->
                    if (textLayoutResult.hasVisualOverflow) {
                        canExpand = true
                    }
                }
            )

            if (canExpand) {
                Text(
                    text = if (isExpanded) "Show less" else "Show more",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { isExpanded = !isExpanded }
                )
            }

            if (comment.likeCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${NumberFormat.getNumberInstance(Locale.US).format(comment.likeCount)} likes",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}