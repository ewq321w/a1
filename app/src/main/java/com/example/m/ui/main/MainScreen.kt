package com.example.m.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.m.ui.navigation.AppNavHost
import com.example.m.ui.navigation.bottomNavItems
import com.example.m.ui.player.MiniPlayer
import com.example.m.ui.player.PlayerScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val nowPlaying by mainViewModel.nowPlaying.collectAsState()
    val isPlaying by mainViewModel.isPlaying.collectAsState()
    val playbackState by mainViewModel.playbackState.collectAsState()
    val showPlayerScreen by mainViewModel.isPlayerScreenVisible

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                nowPlaying?.let { metadata ->
                    MiniPlayer(
                        artworkUri = metadata.artworkUri?.toString() ?: "",
                        songTitle = metadata.title?.toString() ?: "Unknown Title",
                        artistName = metadata.artist?.toString() ?: "Unknown Artist",
                        isPlaying = isPlaying,
                        currentPosition = playbackState.currentPosition,
                        totalDuration = playbackState.totalDuration,
                        onPlayPauseClicked = mainViewModel::togglePlayPause,
                        onContainerClicked = mainViewModel::showPlayerScreen
                    )
                }

                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    val navBackStack by navController.currentBackStack.collectAsState()

                    // Find the route of the last main screen in the back stack
                    val currentTabRoute = navBackStack
                        .lastOrNull { entry -> bottomNavItems.any { it.route == entry.destination.route } }
                        ?.destination?.route

                    bottomNavItems.forEach { screen ->
                        val selected = screen.route == currentTabRoute

                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = selected,
                            onClick = {
                                if (selected) {
                                    navController.popBackStack(screen.route, inclusive = false)
                                } else {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
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
            AppNavHost(navController = navController)
        }
    }

    if (showPlayerScreen) {
        PlayerScreen(onDismiss = mainViewModel::hidePlayerScreen)
    }
}