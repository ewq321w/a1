package com.example.m.ui.common

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun GradientBackground(
    gradientColor1: Color,
    gradientColor2: Color,
    fadeStartFraction: Float = 0.3f,
    fadeEndFraction: Float = 1.0f,
    radialGradientRadiusMultiplier: Float = 1.2f,
    radialGradientAlpha: Float = 0.7f,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val localMaxWidth = maxWidth
        val localMaxHeight = maxHeight
        val screenWidthInPx: Float
        val screenHeightInPx: Float
        with(LocalDensity.current) {
            screenWidthInPx = localMaxWidth.toPx()
            screenHeightInPx = localMaxHeight.toPx()
        }

        val topLeftBrush = remember(gradientColor1, screenWidthInPx, screenHeightInPx, radialGradientRadiusMultiplier, radialGradientAlpha) {
            Brush.radialGradient(
                colors = listOf(gradientColor1.copy(alpha = radialGradientAlpha), Color.Transparent),
                center = Offset(x = 0f, y = -screenHeightInPx * 0.2f),
                radius = screenWidthInPx * radialGradientRadiusMultiplier
            )
        }
        val topRightBrush = remember(gradientColor2, screenWidthInPx, screenHeightInPx, radialGradientRadiusMultiplier, radialGradientAlpha) {
            Brush.radialGradient(
                colors = listOf(gradientColor2.copy(alpha = radialGradientAlpha), Color.Transparent),
                center = Offset(x = screenWidthInPx, y = -screenHeightInPx * 0.2f),
                radius = screenWidthInPx * radialGradientRadiusMultiplier
            )
        }
        val fadeToBlackBrush = remember(screenHeightInPx, fadeStartFraction, fadeEndFraction) {
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = screenHeightInPx * fadeStartFraction,
                endY = screenHeightInPx * fadeEndFraction
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.fillMaxSize().background(Color.Black))
            Spacer(modifier = Modifier.fillMaxSize().background(topLeftBrush))
            Spacer(modifier = Modifier.fillMaxSize().background(topRightBrush))
            Spacer(modifier = Modifier.fillMaxSize().background(fadeToBlackBrush))
            content()
        }
    }
}