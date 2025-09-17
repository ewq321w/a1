// ui/theme/Color.kt
package com.example.m.ui.theme

import androidx.compose.ui.graphics.Color

// --- Common Accent Color (YouTube Red) ---
val Primary = Color(0xFF4F90E7) // YouTube's iconic red

// --- Dark Theme Colors (YouTube Music inspired) ---
val DarkBackground = Color(0xFF050505) // Very dark background
val DarkSurface = Color(0xFF0F0F0F)    // Slightly lighter dark gray for surfaces/cards
val DarkSurfaceVariant = Color(0xFF141414) // Even lighter dark gray for distinct components (e.g., mini-player, nav bar)
val DarkOnPrimary = Color(0xFFFFFFFF)  // Text/icons on primary (red)
val DarkOnBackground = Color(0xFFFFFFFF) // Text/icons on background
val DarkOnSurface = Color(0xFFFFFFFF)  // Text/icons on surface
val DarkOnSurfaceVariant = Color(0xFFAAAAAA) // Lighter gray text/icons for secondary info
val DarkOutline = Color(0xFF555555)    // For borders, inactive states

val DarkRedContainer = Color(0xFF276FC0) // A dark, subtle red for selected containers
val DarkOnRedContainer = Color(0xFFFFFFFF) // White text on dark red container

// Standard Material error colors (can be kept as is)
val ErrorRed = Color(0xFFB00020)
val OnErrorWhite = Color(0xFFFFFFFF)