// file: com/example/m/ui/player/PlayerScreen.kt
package com.example.m.ui.player

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.m.ui.common.CustomBottomSheet
import com.example.m.ui.common.GradientBackground
import com.example.m.ui.main.MainEvent
import com.example.m.ui.main.MainViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private object WhiteRippleTheme : RippleTheme {
    @Composable
    override fun defaultColor() = RippleTheme.defaultRippleColor(
        contentColor = Color.White,
        lightTheme = !isSystemInDarkTheme()
    )

    @Composable
    override fun rippleAlpha() = RippleTheme.defaultRippleAlpha(
        contentColor = Color.White,
        lightTheme = !isSystemInDarkTheme()
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    onDismiss: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val (gradientColor1, gradientColor2) = viewModel.playerGradientColors.value

    var showPlayerHub by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var initialHubTabIndex by remember { mutableStateOf(0) }

    var hubAnimationProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        viewModel.ensureCommentCountLoaded()
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
            when {
                showComments -> showComments = false
                showPlayerHub -> showPlayerHub = false
                else -> onDismiss()
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

                    PlayerArtwork(viewModel = viewModel)

                    Spacer(modifier = Modifier.weight(1f))

                    PlayerControls(
                        viewModel = viewModel,
                        onShowQueue = {
                            initialHubTabIndex = 0
                            showPlayerHub = true
                        },
                        onShowRelated = {
                            initialHubTabIndex = 1
                            showPlayerHub = true
                        },
                        onShowComments = { showComments = true }
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
            }
        }

        CustomBottomSheet(
            isVisible = showPlayerHub,
            onDismiss = { showPlayerHub = false },
            dismissThreshold = 0.4f,
            fastSwipeVelocityThreshold = 1200f,
            animationSpec = SpringSpec(
                dampingRatio = 0.8f,
                stiffness = 350f
            ),
            onAnimationProgress = { progress -> hubAnimationProgress = progress }
        ) {
            PlayerHubScreen(
                onDismiss = { showPlayerHub = false },
                animationProgress = hubAnimationProgress,
                initialTabIndex = initialHubTabIndex
            )
        }

        CustomBottomSheet(
            isVisible = showComments,
            onDismiss = { showComments = false },
            dismissThreshold = 0.4f,
            fastSwipeVelocityThreshold = 1200f,
            animationSpec = SpringSpec(
                dampingRatio = 0.8f,
                stiffness = 350f
            )
        ) {
            PlayerCommentsScreen(
                onBack = { showComments = false },
                isContentReady = true
            )
        }
    }
}

@Composable
private fun PlayerArtwork(viewModel: MainViewModel) {
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        var showBackwardFeedback by remember { mutableStateOf(false) }
        var showForwardFeedback by remember { mutableStateOf(false) }

        LaunchedEffect(showBackwardFeedback) {
            if (showBackwardFeedback) {
                kotlinx.coroutines.delay(250)
                showBackwardFeedback = false
            }
        }

        LaunchedEffect(showForwardFeedback) {
            if (showForwardFeedback) {
                kotlinx.coroutines.delay(250)
                showForwardFeedback = false
            }
        }

        AsyncImage(
            model = nowPlaying?.artworkUri,
            contentDescription = "Album Art",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop,
            placeholder = remember { ColorPainter(placeholderColor) },
            error = remember { ColorPainter(placeholderColor) }
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterStart)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            showBackwardFeedback = true
                            val currentPosition = viewModel.playbackState.value.currentPosition
                            val newPosition = (currentPosition - 10000).coerceAtLeast(0)
                            viewModel.onEvent(MainEvent.SeekTo(newPosition))
                        }
                    )
                }
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterEnd)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            showForwardFeedback = true
                            val currentPosition = viewModel.playbackState.value.currentPosition
                            val totalDuration = viewModel.playbackState.value.totalDuration
                            val newPosition = (currentPosition + 10000).coerceAtMost(totalDuration)
                            viewModel.onEvent(MainEvent.SeekTo(newPosition))
                        }
                    )
                }
        )

        AnimatedVisibility(
            visible = showBackwardFeedback,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            SeekFeedbackOverlay(isForward = false)
        }

        AnimatedVisibility(
            visible = showForwardFeedback,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            SeekFeedbackOverlay(isForward = true)
        }
    }
}

@Composable
private fun SeekFeedbackOverlay(isForward: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerPointX = width / 2f
            val archBaseX = width * 0.4f

            val path = Path().apply {
                // --- FIX START: Corrected the cubic curve calculation for both arches ---
                if (!isForward) { // Backward (left side)
                    moveTo(0f, 0f)
                    lineTo(archBaseX, 0f)
                    // Control points are now to the RIGHT of the curve's start/end points,
                    // making the curve bulge outward (convex).
                    cubicTo(
                        centerPointX + (width * 0.05f), height * 0.25f, // x at 55%
                        centerPointX + (width * 0.05f), height * 0.75f, // x at 55%
                        archBaseX, height
                    )
                    lineTo(0f, height)
                    close()
                } else { // Forward (right side)
                    val forwardArchBaseX = width - archBaseX // At 60% of width
                    moveTo(width, 0f)
                    lineTo(forwardArchBaseX, 0f)
                    // Control points are now to the LEFT of the curve's start/end points,
                    // making the curve bulge outward (convex).
                    cubicTo(
                        centerPointX - (width * 0.05f), height * 0.25f, // x at 45%
                        centerPointX - (width * 0.05f), height * 0.75f, // x at 45%
                        forwardArchBaseX, height
                    )
                    lineTo(width, height)
                    close()
                }
                // --- FIX END ---
            }
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.125f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(if (!isForward) Alignment.CenterStart else Alignment.CenterEnd),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Icon(
                    imageVector = if (!isForward) Icons.Default.FastRewind else Icons.Default.FastForward,
                    contentDescription = if (!isForward) "Seek Backward" else "Seek Forward",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "10s",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@SuppressLint("AutoboxingStateCreation")
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerControls(
    viewModel: MainViewModel,
    onShowQueue: () -> Unit,
    onShowRelated: () -> Unit,
    onShowComments: () -> Unit
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val commentCount by viewModel.commentCount.collectAsState()
    var localSliderProgress by remember { mutableFloatStateOf(0f) }
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
                fontWeight = FontWeight.Medium,
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

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                CompositionLocalProvider(LocalRippleTheme provides WhiteRippleTheme) {
                    TextButton(
                        onClick = onShowComments,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.13f))
                            .height(36.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Comment,
                                contentDescription = "Show Comments",
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = when (commentCount) {
                                    null -> "•••"
                                    0 -> "0"
                                    else -> {
                                        val count = commentCount ?: 0
                                        when {
                                            count >= 1000 -> "${count / 1000}K"
                                            else -> count.toString()
                                        }
                                    }
                                },
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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
            modifier = Modifier
        )
        val displayedPosition = (localSliderProgress * playbackState.totalDuration).toLong()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(displayedPosition),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
            Text(
                text = formatDuration(playbackState.totalDuration),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
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
        CompositionLocalProvider(LocalRippleTheme provides WhiteRippleTheme) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = onShowQueue) {
                    Text("UP NEXT", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }
                TextButton(onClick = onShowRelated) {
                    Text("RELATED", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }
            }
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
            .height(18.dp)
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), CircleShape)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(value)
                .height(trackHeight)
                .background(MaterialTheme.colorScheme.onBackground, CircleShape)
        )
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