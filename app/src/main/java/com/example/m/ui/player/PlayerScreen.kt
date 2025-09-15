// file: com/example/m/ui/player/PlayerScreen.kt
package com.example.m.ui.player

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.m.ui.main.MainEvent
import com.example.m.ui.main.MainViewModel
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    onDismiss: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant

    var sliderPosition by remember { mutableStateOf(0f) }
    var userIsDragging by remember { mutableStateOf(false) }

    LaunchedEffect(playbackState.currentPosition, userIsDragging) {
        if (!userIsDragging) {
            sliderPosition = playbackState.currentPosition.toFloat()
        }
    }

    BackHandler {
        onDismiss()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceAround
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onBackground)
                }
            }

            AsyncImage(
                model = nowPlaying?.artworkUri,
                contentDescription = "Album Art",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                placeholder = remember { ColorPainter(placeholderColor) },
                error = remember { ColorPainter(placeholderColor) }
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = nowPlaying?.title?.toString() ?: "Unknown Title",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    text = nowPlaying?.artist?.toString() ?: "Unknown Artist",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontSize = 18.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .basicMarquee()
                )

                Slider(
                    value = sliderPosition,
                    onValueChange = { newValue ->
                        sliderPosition = newValue
                        userIsDragging = true
                    },
                    valueRange = 0f..(playbackState.totalDuration.toFloat().takeIf { it > 0 } ?: 1f),
                    onValueChangeFinished = {
                        viewModel.onEvent(MainEvent.SeekTo(sliderPosition.toLong()))
                        userIsDragging = false
                    },
                    modifier = Modifier.padding(vertical = 16.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.onBackground,
                        activeTrackColor = MaterialTheme.colorScheme.onBackground,
                        inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatDuration(playbackState.currentPosition), color =  MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    Text(text = formatDuration(playbackState.totalDuration), color =  MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.onEvent(MainEvent.SkipToPrevious) }, modifier = Modifier.size(55.dp)) {
                        Icon(
                            Icons.Default.SkipPrevious, contentDescription = "Previous",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.onEvent(MainEvent.TogglePlayPause) }, modifier = Modifier.size(100.dp)) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                            contentDescription = "Play/Pause",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.onEvent(MainEvent.SkipToNext) }, modifier = Modifier.size(55.dp)) {
                        Icon(
                            Icons.Default.SkipNext, contentDescription = "Next",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(40.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@SuppressLint("DefaultLocale")
private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
    return String.format("%02d:%02d", minutes, seconds)
}