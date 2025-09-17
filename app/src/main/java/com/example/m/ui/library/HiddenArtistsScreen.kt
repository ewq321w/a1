package com.example.m.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m.data.database.Artist
import com.example.m.ui.common.GradientBackground
import com.example.m.ui.library.components.EmptyStateMessage
import com.example.m.ui.main.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenArtistsScreen(
    onBack: () -> Unit,
    viewModel: HiddenArtistsViewModel = hiltViewModel()
) {
    val hiddenArtists by viewModel.hiddenArtists.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val activity = LocalContext.current as ComponentActivity
    val mainViewModel: MainViewModel = hiltViewModel(activity)
    val (gradientColor1, gradientColor2) = mainViewModel.randomGradientColors.value

    GradientBackground(gradientColor1 = gradientColor1, gradientColor2 = gradientColor2) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = { Text("Hidden Artists") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.1f),
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            if (hiddenArtists.isEmpty()) {
                EmptyStateMessage(message = "You have no hidden artists.")
            } else {
                LazyColumn(modifier = Modifier.padding(paddingValues)) {
                    items(hiddenArtists, key = { it.artistId }) { artist ->
                        val rememberedOnUnhide = remember { { viewModel.unhideArtist(artist) } }
                        HiddenArtistItem(
                            artist = artist,
                            onUnhideClick = rememberedOnUnhide
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenArtistItem(
    artist: Artist,
    onUnhideClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(artist.name) },
        trailingContent = {
            TextButton(onClick = onUnhideClick) {
                Text("Unhide")
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}