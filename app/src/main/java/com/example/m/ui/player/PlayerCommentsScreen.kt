// file: com/example/m/ui/player/PlayerCommentsScreen.kt
package com.example.m.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaMetadata
import coil.compose.AsyncImage
import com.example.m.ui.common.GradientBackground
import com.example.m.ui.main.MainEvent
import com.example.m.ui.main.MainViewModel

@Composable
private fun PlayerInfoBar(
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 5.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = mediaMetadata?.artworkUri,
                contentDescription = "Album Art",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(3.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaMetadata?.title?.toString() ?: "Unknown Title",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium, // Changed from SemiBold to Medium
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    text = mediaMetadata?.artist?.toString() ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(onClick = onTogglePlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(25.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

@Composable
private fun CommentsTopBar(
    onClose: () -> Unit,
    onBackToComments: () -> Unit = {},
    isShowingReplies: Boolean = false,
    repliesAuthor: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (isShowingReplies) {
                IconButton(onClick = onBackToComments) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Comments",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    "Replies",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium, // Changed from SemiBold to Medium
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp)
                )
            } else {
                Text(
                    "Comments",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium, // Changed from SemiBold to Medium
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }

        if (!isShowingReplies) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close Comments",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}


@Composable
fun PlayerCommentsScreen(
    onBack: () -> Unit,
    isContentReady: Boolean,
    mainViewModel: MainViewModel = hiltViewModel(),
    commentsViewModel: CommentsViewModel = hiltViewModel()
) {
    val currentMediaId by mainViewModel.currentMediaId.collectAsState()
    val nowPlaying by mainViewModel.nowPlaying.collectAsState()
    val isPlaying by mainViewModel.isPlaying.collectAsState()
    val (gradientColor1, gradientColor2) = mainViewModel.playerGradientColors.value

    // Get the comments UI state to check if we're showing replies
    val commentsUiState by commentsViewModel.uiState.collectAsState()
    val isShowingReplies = commentsUiState.showingRepliesFor != null

    // Handle Android back button properly
    BackHandler(enabled = isShowingReplies) {
        // When showing replies, back button should go back to comments
        commentsViewModel.hideReplies()
    }

    LaunchedEffect(currentMediaId) {
        commentsViewModel.loadInitialComments(currentMediaId)
    }

    GradientBackground(
        gradientColor1 = gradientColor1,
        gradientColor2 = gradientColor2,
        fadeStartFraction = 0.4f,
        fadeEndFraction = 1.7f,
        radialGradientRadiusMultiplier = 5.0f,
        radialGradientAlpha = 0.4f
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PlayerInfoBar(
                mediaMetadata = nowPlaying,
                isPlaying = isPlaying,
                onTogglePlayPause = { mainViewModel.onEvent(MainEvent.TogglePlayPause) }
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                CommentsTopBar(
                    onClose = onBack,
                    onBackToComments = { commentsViewModel.hideReplies() },
                    isShowingReplies = isShowingReplies,
                    repliesAuthor = commentsUiState.showingRepliesFor?.uploaderName ?: ""
                )
                HorizontalDivider()
                if (isContentReady) {
                    CommentsTabContent(mainViewModel = mainViewModel, viewModel = commentsViewModel)
                }
            }
        }
    }
}