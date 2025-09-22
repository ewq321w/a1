// file: com/example/m/ui/common/CustomBottomSheet.kt
package com.example.m.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun CustomBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissThreshold: Float = 0.5f,
    fastSwipeVelocityThreshold: Float = 800f, // dp per second
    animationSpec: AnimationSpec<Float> = spring(dampingRatio = 0.85f, stiffness = 450f),
    backgroundColor: Color = Color.Transparent,
    onAnimationProgress: (Float) -> Unit = {},  // New: Callback for progress
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val approxHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    var containerHeightPx by remember { mutableStateOf(approxHeightPx) }
    val anim = remember { Animatable(0f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    // This state keeps the sheet in the composition long enough for the exit animation.
    var sheetVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isVisible) {
        if (isVisible) sheetVisible = true
    }

    // This is the main animation driver.
    // It reacts to isVisible changes and animates to the correct target.
    LaunchedEffect(isVisible, containerHeightPx) {
        // Don't animate until we have a measured height.
        if (containerHeightPx == approxHeightPx) return@LaunchedEffect

        val targetValue = if (isVisible) 0f else containerHeightPx
        val startValue = anim.value

        anim.animateTo(targetValue, animationSpec)

        // Emit progress during animation (normalized 0=closed, 1=open)
        val startProgress = 1f - (startValue / containerHeightPx)
        val endProgress = 1f - (targetValue / containerHeightPx)
        while (anim.isRunning) {
            val currentValue = anim.value
            val currentProgress = 1f - (currentValue / containerHeightPx)
            onAnimationProgress(currentProgress)
            kotlinx.coroutines.delay(16)  // ~60fps poll
        }
        onAnimationProgress(if (isVisible) 1f else 0f)  // Final value

        // If the sheet just finished its exit animation, remove it from composition.
        if (!isVisible) {
            sheetVisible = false
        }
    }

    if (!sheetVisible) {
        // When the sheet is fully gone, reset the animation value.
        LaunchedEffect(Unit) {
            anim.snapTo(containerHeightPx)
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                val oldHeight = containerHeightPx
                containerHeightPx = size.height.toFloat()
                // We now check if the sheet is actually closed before snapping.
                val isClosed = anim.value == oldHeight
                if (!isVisible && isClosed) {
                    scope.launch { anim.snapTo(size.height.toFloat()) }
                }
            }
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = (anim.value + dragOffset).coerceIn(0f, containerHeightPx)
                }
                .background(backgroundColor)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            val newPos = anim.value + dragOffset + delta
                            dragOffset = newPos - anim.value
                        }
                    },
                    onDragStarted = {
                        scope.launch { anim.stop() }
                    },
                    onDragStopped = { velocityPxPerSec ->
                        scope.launch {
                            val currentPosition = anim.value + dragOffset
                            dragOffset = 0f

                            val shouldDismiss = currentPosition > containerHeightPx * dismissThreshold || velocityPxPerSec > fastSwipeVelocityThreshold
                            if (shouldDismiss) {
                                // Snap the animator to the current dragged position before dismissing.
                                anim.snapTo(currentPosition)
                                onAnimationProgress(0f)
                                onDismiss() // Dismiss immediately, the LaunchedEffect will handle animation.
                            } else {
                                // Animate back to the open state.
                                anim.snapTo(currentPosition)
                                anim.animateTo(0f, animationSpec)
                                onAnimationProgress(1f)
                            }
                        }
                    }
                )
        ) {
            // Back button is now responsive immediately.
            BackHandler(enabled = isVisible) {
                onDismiss()
            }
            content()
        }
    }
}