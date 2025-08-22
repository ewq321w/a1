package com.example.m.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Primary,       // Main accent color (e.g., active elements, buttons)
    onPrimary = DarkOnPrimary,  // Text/icons on primary color
    secondary = DarkSurface,    // Secondary containers, less prominent elements
    onSecondary = DarkOnSurface, // Text/icons on secondary color
    tertiary = DarkSurfaceVariant, // Tertiary elements, distinct background for some components
    onTertiary = DarkOnSurfaceVariant, // Text/icons on tertiary color

    background = DarkBackground, // Main screen background
    onBackground = DarkOnBackground, // Text/icons on main background
    surface = DarkSurface,      // Default surface for cards, dialogs, app bars
    onSurface = DarkOnSurface,  // Text/icons on surface
    surfaceVariant = DarkSurfaceVariant, // Used for MiniPlayer, NavigationBar
    onSurfaceVariant = DarkOnSurfaceVariant, // Text/icons on surface variant

    error = ErrorRed,           // Error state color
    onError = OnErrorWhite,     // Text/icons on error color
    outline = DarkOutline,       // For outlined elements, disabled states

    primaryContainer = DarkRedContainer,    // Background for primary-colored elements (e.g., selected FilterChip)
    onPrimaryContainer = DarkOnRedContainer, // Text/icon on primaryContainer
    secondaryContainer = DarkSurfaceVariant, // Background for secondary elements (e.g., selected SegmentedButton)
    onSecondaryContainer = DarkOnSurfaceVariant, // Text/icon on secondaryContainer
    tertiaryContainer = DarkSurfaceVariant,  // Can be same as secondaryContainer or a different shade
    onTertiaryContainer = DarkOnSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,       // Main accent color
    onPrimary = LightOnPrimary, // Text/icons on primary color
    secondary = LightSurface,   // Secondary containers
    onSecondary = LightOnSurface, // Text/icons on secondary color
    tertiary = LightSurfaceVariant, // Tertiary elements
    onTertiary = LightOnSurfaceVariant, // Text/icons on tertiary color

    background = LightBackground, // Main screen background
    onBackground = LightOnBackground, // Text/icons on main background
    surface = LightSurface,     // Default surface for cards, dialogs, app bars
    onSurface = LightOnSurface, // Text/icons on surface
    surfaceVariant = LightSurfaceVariant, // Used for MiniPlayer, NavigationBar
    onSurfaceVariant = LightOnSurfaceVariant, // Text/icons on surface variant

    error = ErrorRed,           // Error state color
    onError = OnErrorWhite,     // Text/icons on error color
    outline = LightOutline,      // For outlined elements, disabled states

    primaryContainer = LightRedContainer,
    onPrimaryContainer = LightOnRedContainer,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightOnSurfaceVariant,
    tertiaryContainer = LightSurfaceVariant,
    onTertiaryContainer = LightOnSurfaceVariant
)

@Composable
fun MTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && true -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as ComponentActivity).window
            window.statusBarColor = Color.Transparent.toArgb() // Make status bar transparent
            window.navigationBarColor = Color.Transparent.toArgb() // Make nav bar transparent
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}