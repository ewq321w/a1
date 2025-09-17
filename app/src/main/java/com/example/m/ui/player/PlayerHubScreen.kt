// file: com/example/m/ui/player/PlayerHubScreen.kt
package com.example.m.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaMetadata
import coil.compose.AsyncImage
import com.example.m.ui.common.GradientBackground
import com.example.m.ui.main.MainEvent
import com.example.m.ui.main.MainViewModel

@Composable
private fun PlayerHubHeader(
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 5.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = mediaMetadata?.artworkUri,
                contentDescription = "Album Art",
                modifier = Modifier
                    .padding(start = 10.dp)
                    .size(48.dp)
                    .clip(RoundedCornerShape(3.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaMetadata?.title?.toString() ?: "Unknown Title",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
                Text(
                    text = mediaMetadata?.artist?.toString() ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(onClick = onTogglePlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(25.dp),
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerHubScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onDismiss: () -> Unit
) {
    val (color1, color2) = viewModel.playerGradientColors.value
    val queue by viewModel.queue.collectAsState()
    val currentIndex by viewModel.currentMediaItemIndex.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Queue", "asdsa", "sbad")

    GradientBackground(
        gradientColor1 = color1,
        gradientColor2 = color2,
        fadeStartFraction = 1.0f,
        fadeEndFraction = 1.75f,
        radialGradientRadiusMultiplier = 10.0f,
        radialGradientAlpha = 0.15f
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            PlayerHubHeader(
                mediaMetadata = nowPlaying,
                isPlaying = isPlaying,
                onTogglePlayPause = { viewModel.onEvent(MainEvent.TogglePlayPause) },
                onDismiss = onDismiss
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                GradientBackground(
                    gradientColor1 = color1,
                    gradientColor2 = color2,
                    fadeStartFraction = 1.0f,
                    fadeEndFraction = 1.75f,
                    radialGradientRadiusMultiplier = 10.0f,
                    radialGradientAlpha = 0.35f
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {

                        Spacer(modifier = Modifier.height(12.dp))

                        TabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = Color.Transparent,
                            contentColor = Color.White.copy(alpha = 0.5f),
                            indicator = { tabPositions ->
                                if (selectedTabIndex < tabPositions.size) {
                                    TabRowDefaults.SecondaryIndicator(
                                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                        color = Color.White
                                    )
                                }
                            },
                            modifier = Modifier.padding(horizontal = 24.dp)
                        ) {
                            tabs.forEachIndexed { index, title ->
                                val isSelected = selectedTabIndex == index
                                Tab(
                                    selected = isSelected,
                                    onClick = { selectedTabIndex = index },
                                    text = {
                                        Text(
                                            title,
                                            fontSize = 15.sp,
                                            modifier = Modifier.padding(bottom = 3.dp),
                                            color = if (isSelected) Color.White else Color.White.copy(
                                                alpha = 0.8f
                                            )
                                        )
                                    }
                                )
                            }
                        }

                        when (selectedTabIndex) {
                            0 -> QueueTabContent(
                                queue = queue,
                                currentMediaItemIndex = currentIndex,
                                onPlayItem = { viewModel.skipToQueueItem(it) },
                                onMoveItem = { from, to -> viewModel.moveQueueItem(from, to) }
                            )
                        }
                    }
                }
            }
        }
    }
}