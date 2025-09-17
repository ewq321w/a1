// file: com/example/m/ui/player/PlayerScreen.kt
package com.example.m.ui.player

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.m.ui.common.GradientBackground
import com.example.m.ui.main.MainEvent
import com.example.m.ui.main.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onDismiss: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    val (gradientColor1, gradientColor2) = viewModel.playerGradientColors.value

    var showPlayerHub by remember { mutableStateOf(false) }
    val hubSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Sync showing the sheet
    LaunchedEffect(showPlayerHub) {
        if (showPlayerHub) {
            hubSheetState.show()
        } else {
            hubSheetState.hide()
        }
    }

    // Sync hiding the sheet (from swiping)
    LaunchedEffect(hubSheetState.isVisible) {
        if (!hubSheetState.isVisible && showPlayerHub) {
            showPlayerHub = false
        }
    }

    GradientBackground(
        gradientColor1 = gradientColor1,
        gradientColor2 = gradientColor2,
        fadeStartFraction = 0.4f,
        fadeEndFraction = 1.7f,
        radialGradientRadiusMultiplier = 5.0f,
        radialGradientAlpha = 0.4f
    ) {
        BackHandler {
            if (showPlayerHub) {
                showPlayerHub = false
            } else {
                onDismiss()
            }
        }

        Scaffold(
            containerColor = Color.Transparent
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(100.dp))

                    AsyncImage(
                        model = nowPlaying?.artworkUri,
                        contentDescription = "Album Art",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                        placeholder = remember { ColorPainter(placeholderColor) },
                        error = remember { ColorPainter(placeholderColor) }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    PlayerControls(
                        viewModel = viewModel,
                        playbackState = playbackState,
                        onShowQueue = { showPlayerHub = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    val iconColor = MaterialTheme.colorScheme.onBackground
                    Canvas(
                        modifier = Modifier.size(30.dp)
                    ) {
                        val strokeWidth = 1.0.dp.toPx()
                        val width = size.width
                        val height = size.height

                        val path = Path().apply {
                            moveTo(width * 0.3f, height * 0.4f)
                            lineTo(width * 0.5f, height * 0.6f)
                            lineTo(width * 0.7f, height * 0.4f)
                        }

                        drawPath(
                            path = path,
                            color = iconColor,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }

                if (showPlayerHub || hubSheetState.isVisible) {
                    ModalBottomSheet(
                        onDismissRequest = { showPlayerHub = false },
                        sheetState = hubSheetState,
                        shape = RectangleShape,
                        containerColor = Color.Transparent,
                        dragHandle = null,
                        windowInsets = WindowInsets(0)
                    ) {
                        PlayerHubScreen(
                            viewModel = viewModel,
                            onDismiss = { showPlayerHub = false }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerControls(
    viewModel: MainViewModel,
    playbackState: com.example.m.playback.PlaybackState,
    onShowQueue: () -> Unit
) {
    val isPlaying by viewModel.isPlaying.collectAsState()

    var localSliderProgress by remember { mutableStateOf(0f) }
    var isUserInteracting by remember { mutableStateOf(false) }

    LaunchedEffect(playbackState.currentPosition, playbackState.totalDuration) {
        if (!isUserInteracting) {
            localSliderProgress = if (playbackState.totalDuration > 0) {
                playbackState.currentPosition.toFloat() / playbackState.totalDuration
            } else 0f
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = viewModel.nowPlaying.value?.title?.toString() ?: "Unknown Title",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            Text(
                text = viewModel.nowPlaying.value?.artist?.toString() ?: "Unknown Artist",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                fontSize = 16.sp,
                maxLines = 1,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .basicMarquee()
            )
        }

        CustomPlayerSlider(
            value = localSliderProgress,
            isDragging = isUserInteracting,
            onValueChange = { newProgress ->
                isUserInteracting = true
                localSliderProgress = newProgress
            },
            onValueChangeFinished = {
                val newPosition = (localSliderProgress * playbackState.totalDuration).toLong()
                viewModel.onEvent(MainEvent.SeekTo(newPosition))
                isUserInteracting = false
            },
            modifier = Modifier.padding(top = 16.dp)
        )


        val displayedPosition = (localSliderProgress * playbackState.totalDuration).toLong()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(displayedPosition),
                color =  MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
            Text(
                text = formatDuration(playbackState.totalDuration),
                color =  MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.onEvent(MainEvent.SkipToPrevious) }, modifier = Modifier.size(55.dp)) {
                Icon(
                    Icons.Default.SkipPrevious, contentDescription = "Previous",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(35.dp)
                )
            }
            IconButton(onClick = { viewModel.onEvent(MainEvent.TogglePlayPause) }, modifier = Modifier.size(100.dp)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                    contentDescription = "Play/Pause",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(88.dp)
                )
            }
            IconButton(onClick = { viewModel.onEvent(MainEvent.SkipToNext) }, modifier = Modifier.size(55.dp)) {
                Icon(
                    Icons.Default.SkipNext, contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(35.dp))
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        TextButton(onClick = onShowQueue) {
            Text("UP NEXT", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun CustomPlayerSlider(
    value: Float,
    isDragging: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    val animatedThumbSize by animateDpAsState(
        targetValue = if (isDragging) 18.dp else 12.dp,
        label = "Thumb Size Animation"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp) // Set height to the largest thumb size to prevent clipping
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val newValue = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onValueChange(newValue)
                    },
                    onDragEnd = { onValueChangeFinished() },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val newValue = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onValueChange(newValue)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    scope.launch {
                        val newValue = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onValueChange(newValue)
                        onValueChangeFinished()
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val trackHeight = 2.dp

        // Inactive Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), CircleShape)
        )

        // Active Track
        Box(
            modifier = Modifier
                .fillMaxWidth(value)
                .height(trackHeight)
                .background(MaterialTheme.colorScheme.onBackground, CircleShape)
        )

        // Thumb
        Box(
            modifier = Modifier
                .offset(x = (maxWidth - animatedThumbSize) * value)
                .size(animatedThumbSize)
                .background(Color.White, CircleShape)
        )
    }
}

@SuppressLint("DefaultLocale")
private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
    return String.format("%d:%02d", minutes, seconds)
}