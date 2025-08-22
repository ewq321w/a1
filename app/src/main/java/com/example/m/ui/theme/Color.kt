// ui/theme/Color.kt
package com.example.m.ui.theme

import androidx.compose.ui.graphics.Color

// --- Common Accent Color (YouTube Red) ---
val Primary = Color(0xFF4F90E7) // YouTube's iconic red

// --- Dark Theme Colors (YouTube Music inspired) ---
val DarkBackground = Color(0xFF0C0C0C) // Very dark background
val DarkSurface = Color(0xFF151515)    // Slightly lighter dark gray for surfaces/cards
val DarkSurfaceVariant = Color(0xFF1C1C1C) // Even lighter dark gray for distinct components (e.g., mini-player, nav bar)
val DarkOnPrimary = Color(0xFFFFFFFF)  // Text/icons on primary (red)
val DarkOnBackground = Color(0xFFFFFFFF) // Text/icons on background
val DarkOnSurface = Color(0xFFFFFFFF)  // Text/icons on surface
val DarkOnSurfaceVariant = Color(0xFFCCCCCC) // Lighter gray text/icons for secondary info
val DarkOutline = Color(0xFF555555)    // For borders, inactive states

val DarkRedContainer = Color(0xFF276FC0) // A dark, subtle red for selected containers
val DarkOnRedContainer = Color(0xFFFFFFFF) // White text on dark red container

// --- Light Theme Colors (YouTube Music inspired) ---
val LightBackground = Color(0xFFFFFFFF) // Pure white background
val LightSurface = Color(0xFFF0F0F0)    // Very light gray for surfaces/cards
val LightSurfaceVariant = Color(0xFFE0E0E0) // Slightly darker light gray for distinct components
val LightOnPrimary = Color(0xFFFFFFFF)  // Text/icons on primary (red) - assuming red will be bold enough
val LightOnBackground = Color(0xFF141414) // Dark text/icons on light background
val LightOnSurface = Color(0xFF141414)  // Dark text/icons on light surface
val LightOnSurfaceVariant = Color(0xFF444444) // Medium gray text/icons for secondary info
val LightOutline = Color(0xFFAAAAAA)   // For borders, inactive states

val LightRedContainer = Color(0xFFFFEEEE) // A very light red for selected containers
val LightOnRedContainer = Color(0xFF2A73CC) // Dark red text on light red container

// Standard Material error colors (can be kept as is)
val ErrorRed = Color(0xFFB00020)
val OnErrorWhite = Color(0xFFFFFFFF)