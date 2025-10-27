// file: com.example.m/ui/main/MainScreen.kt
package com.example.m.ui.main

import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.rememberNavController
import com.example.m.ui.common.CustomBottomSheet
import com.example.m.ui.common.GradientBackground
import com.example.m.ui.navigation.AppNavHost
import com.example.m.ui.navigation.bottomNavItems
import com.example.m.ui.player.MiniPlayer
import com.example.m.ui.player.PlayerScreen

@SuppressLint("RestrictedApi", "ContextCastToActivity")
@Composable
fun MainScreen() {
    val activity = LocalContext.current as ComponentActivity
    val mainViewModel: MainViewModel = hiltViewModel(activity)

    val navController = rememberNavController()
    val nowPlaying by mainViewModel.nowPlaying.collectAsState()
    val isPlaying by mainViewModel.isPlaying.collectAsState()
    val playerState by mainViewModel.playerState.collectAsState()
    val playbackState by mainViewModel.playbackState.collectAsState()
    val uiState by mainViewModel.uiState
    val (randomColor1, randomColor2) = mainViewModel.randomGradientColors.value

    val snackbarHostState = remember { SnackbarHostState() }
    val maintenanceResult by mainViewModel.maintenanceResult

    LaunchedEffect(maintenanceResult) {
        maintenanceResult?.let {
            snackbarHostState.showSnackbar(
                message = it,
                actionLabel = "Close",
                duration = SnackbarDuration.Short
            )
            mainViewModel.clearMaintenanceResult()
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.snackbarManager.messages.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Close",
                duration = SnackbarDuration.Short
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        GradientBackground(gradientColor1 = randomColor1, gradientColor2 = randomColor2) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = Color.Transparent,
                bottomBar = {
                    Column(Modifier.navigationBarsPadding()) {
                        nowPlaying?.let { metadata ->
                            if (!metadata.title.isNullOrBlank()) {
                                MiniPlayer(
                                    artworkUri = metadata.artworkUri?.toString() ?: "",
                                    songTitle = metadata.title.toString(),
                                    artistName = metadata.artist?.toString() ?: "Unknown Artist",
                                    isPlaying = isPlaying,
                                    playerState = playerState,
                                    currentPosition = playbackState.currentPosition,
                                    totalDuration = playbackState.totalDuration,
                                    onPlayPauseClicked = { mainViewModel.onEvent(MainEvent.TogglePlayPause) },
                                    onContainerClicked = { mainViewModel.onEvent(MainEvent.ShowPlayerScreen) }
                                )
                            }
                        }
                        NavigationBar(modifier = Modifier.height(72.dp), containerColor = MaterialTheme.colorScheme.surface) {
                            val navBackStack by navController.currentBackStack.collectAsState()
                            val currentTabRoute = navBackStack.lastOrNull { entry -> bottomNavItems.any { it.route == entry.destination.route } }?.destination?.route

                            bottomNavItems.forEach { screen ->
                                val selected = screen.route == currentTabRoute
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = screen.label) },
                                    label = { Text(screen.label) },
                                    selected = selected,
                                    onClick = {
                                        if (!selected) {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    )
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    AppNavHost(navController = navController, modifier = Modifier.fillMaxSize())
                }
            }
        }

        CustomBottomSheet(
            isVisible = uiState.isPlayerScreenVisible,
            onDismiss = { mainViewModel.onEvent(MainEvent.HidePlayerScreen) },
            dismissThreshold = 0.4f,
            fastSwipeVelocityThreshold = 600f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 350f)
        ) {
            PlayerScreen(onDismiss = {
                mainViewModel.onEvent(MainEvent.HidePlayerScreen)
            })
        }

        // SnackbarHost positioned at the top level to appear above everything
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 88.dp), // Padding to avoid bottom bar
            contentAlignment = Alignment.BottomCenter
        ) {
            SnackbarHost(snackbarHostState)
        }
    }
}