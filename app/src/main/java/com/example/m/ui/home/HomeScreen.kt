package com.example.m.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.m.R
import com.example.m.ui.common.GradientBackground
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.main.MainViewModel
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val activity = LocalContext.current as ComponentActivity
    val mainViewModel: MainViewModel = hiltViewModel(activity)
    val (gradientColor1, gradientColor2) = mainViewModel.randomGradientColors.value

    GradientBackground(
        gradientColor1 = gradientColor1,
        gradientColor2 = gradientColor2
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = { Text("Home") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    scrollBehavior = scrollBehavior
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    RecommendationSection(
                        title = "Your Recent Mix",
                        items = uiState.recentMix,
                        onItemClick = { index ->
                            viewModel.onEvent(HomeEvent.PlayRecentMix(index))
                        },
                        nowPlayingMediaId = uiState.nowPlayingMediaId,
                        isPlaying = uiState.isPlaying
                    )
                }
                item {
                    RecommendationSection(
                        title = "Discovery Mix",
                        items = uiState.discoveryMix,
                        onItemClick = { index ->
                            viewModel.onEvent(HomeEvent.PlayDiscoveryMix(index))
                        },
                        nowPlayingMediaId = uiState.nowPlayingMediaId,
                        isPlaying = uiState.isPlaying
                    )
                }
            }
        }
    }
}

@Composable
fun RecommendationSection(
    title: String,
    items: List<StreamInfoItem>,
    onItemClick: (Int) -> Unit,
    nowPlayingMediaId: String?,
    isPlaying: Boolean
) {
    if (items.isNotEmpty()) {
        Column {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(items) { index, item ->
                    val rememberedOnClick = remember { { onItemClick(index) } }
                    val normalizedUrl = item.url?.replace("music.youtube.com", "www.youtube.com")
                    val isCurrentlyPlayingItem = normalizedUrl == nowPlayingMediaId
                    RecommendationItem(
                        item = item,
                        onClick = rememberedOnClick,
                        isCurrentlyPlaying = isCurrentlyPlayingItem,
                        isPlaying = isPlaying
                    )
                }
            }
        }
    }
}

@Composable
fun RecommendationItem(
    item: StreamInfoItem,
    onClick: () -> Unit,
    isCurrentlyPlaying: Boolean,
    isPlaying: Boolean
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        val highQualityThumbnailUrl = item.getHighQualityThumbnailUrl()

        Box {
            AsyncImage(
                model = highQualityThumbnailUrl,
                contentDescription = item.name,
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(3.dp)),
                contentScale = ContentScale.Crop,
                error = painterResource(id = R.drawable.placeholder_gray),
                placeholder = painterResource(id = R.drawable.placeholder_gray)
            )

            if (isCurrentlyPlaying) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(3.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        Text(
            text = item.name ?: "Unknown",
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 13.sp
        )
        Text(
            text = item.uploaderName ?: "Unknown",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp
        )
    }
}