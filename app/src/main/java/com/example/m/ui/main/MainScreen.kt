// file: com/example.m/ui/main/MainScreen.kt
package com.example.m.ui.main

import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.rememberNavController
import com.example.m.ui.common.GradientBackground
import com.example.m.ui.navigation.AppNavHost
import com.example.m.ui.navigation.bottomNavItems
import com.example.m.ui.player.MiniPlayer
import com.example.m.ui.player.PlayerScreen

@SuppressLint("RestrictedApi", "ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class)
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

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    // Syncs the ViewModel's state TO the sheet, causing it to show or hide.
    LaunchedEffect(uiState.isPlayerScreenVisible) {
        if (uiState.isPlayerScreenVisible) {
            sheetState.show()
        } else {
            sheetState.hide()
        }
    }

    // Syncs the sheet's state BACK to the ViewModel, for when the user swipes to dismiss.
    LaunchedEffect(sheetState.isVisible) {
        if (!sheetState.isVisible && uiState.isPlayerScreenVisible) {
            mainViewModel.onEvent(MainEvent.HidePlayerScreen)
        }
    }

    LaunchedEffect(maintenanceResult) {
        maintenanceResult?.let {
            snackbarHostState.showSnackbar(it)
            mainViewModel.clearMaintenanceResult()
        }
    }

    Box(Modifier.fillMaxSize()) {
        GradientBackground(gradientColor1 = randomColor1, gradientColor2 = randomColor2) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
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

        // This condition now allows the sheet to remain in composition during its closing animation
        if (uiState.isPlayerScreenVisible || sheetState.isVisible) {
            ModalBottomSheet(
                onDismissRequest = { mainViewModel.onEvent(MainEvent.HidePlayerScreen) },
                sheetState = sheetState,
                windowInsets = WindowInsets(0, 0, 0, 0),
                dragHandle = null,
                containerColor = Color.Transparent,
                scrimColor = Color.Transparent
            ) {
                PlayerScreen(onDismiss = {
                    mainViewModel.onEvent(MainEvent.HidePlayerScreen)
                })
            }
        }
    }
}