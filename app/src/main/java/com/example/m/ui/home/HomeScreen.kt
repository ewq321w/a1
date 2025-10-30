package com.example.m.ui.home

import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.m.R
import com.example.m.data.database.Song
import com.example.m.ui.common.GradientBackground
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.main.MainViewModel
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import kotlin.math.ceil
import kotlin.math.min

@SuppressLint("RestrictedApi", "ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as ComponentActivity
    val mainViewModel: MainViewModel = hiltViewModel(activity)
    val (gradientColor1, gradientColor2) = mainViewModel.randomGradientColors.value

    GradientBackground(
        gradientColor1 = gradientColor1,
        gradientColor2 = gradientColor2
    ) {
        if (uiState.listeningStats?.totalSongs == 0 && !uiState.isLoading) {
            // Empty state
            EmptyHomeState(
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.onEvent(HomeEvent.Refresh) }
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 36.dp, bottom = 16.dp)
                ) {
                    // Welcome header with stats
                    item {
                        WelcomeHeader(
                            stats = uiState.listeningStats,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Quick actions
                    item {
                        QuickActionsSection(
                            onShuffleAll = { viewModel.onEvent(HomeEvent.ShuffleAll) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Top Songs This Week - HorizontalPager with 3x3 grid
                    if (uiState.topSongsThisWeek.isNotEmpty()) {
                        item {
                            TopSongsThisWeekPager(
                                songs = uiState.topSongsThisWeek,
                                onSongClick = { index ->
                                    viewModel.onEvent(HomeEvent.PlayTopSong(index))
                                },
                                nowPlayingMediaId = uiState.nowPlayingMediaId,
                                isPlaying = uiState.isPlaying
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Discovery Mix
                    if (uiState.discoveryMix.isNotEmpty()) {
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
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Recently Added
                    if (uiState.recentlyAdded.isNotEmpty()) {
                        item {
                            SongListSection(
                                title = "Recently Added",
                                songs = uiState.recentlyAdded,
                                onSongClick = { index ->
                                    viewModel.onEvent(HomeEvent.PlayRecentlyAdded(index))
                                },
                                nowPlayingMediaId = uiState.nowPlayingMediaId,
                                isPlaying = uiState.isPlaying
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Quick Access Playlists
                    if (uiState.quickAccessPlaylists.isNotEmpty()) {
                        item {
                            QuickAccessPlaylistsSection(
                                playlists = uiState.quickAccessPlaylists,
                                onPlaylistClick = { playlistId ->
                                    viewModel.onEvent(HomeEvent.NavigateToPlaylist(playlistId))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyHomeState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Welcome to M",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Start by searching for music or adding songs to your library",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TopSongsThisWeekPager(
    songs: List<Song>,
    onSongClick: (Int) -> Unit,
    nowPlayingMediaId: String?,
    isPlaying: Boolean
) {
    val pagerState = rememberPagerState(
        pageCount = { ceil(songs.size / 9f).toInt() },
        initialPage = 0
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "On Repeat",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            pageSpacing = 24.dp
        ) { pageIndex ->
            val startIndex = pageIndex * 9
            val endIndex = min(startIndex + 9, songs.size)
            val pageSongs = songs.subList(startIndex, endIndex)

            // 3x3 Grid for each page
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (row in 0 until 3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (col in 0 until 3) {
                            val index = row * 3 + col
                            if (index < pageSongs.size) {
                                val song = pageSongs[index]
                                val globalIndex = startIndex + index
                                val isCurrentlyPlaying = song.youtubeUrl.replace("music.youtube.com", "www.youtube.com") == nowPlayingMediaId

                                TopSongGridItem(
                                    song = song,
                                    onClick = { onSongClick(globalIndex) },
                                    isCurrentlyPlaying = isCurrentlyPlaying,
                                    isPlaying = isPlaying,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                // Empty space for incomplete rows
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Page indicator
        if (pagerState.pageCount > 1) {
            DotsIndicator(
                totalDots = pagerState.pageCount,
                selectedIndex = pagerState.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun TopSongGridItem(
    song: Song,
    onClick: () -> Unit,
    isCurrentlyPlaying: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = song.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                error = painterResource(id = R.drawable.placeholder_gray),
                placeholder = painterResource(id = R.drawable.placeholder_gray)
            )

            // Dark gradient at bottom with song name
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.3f)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.DarkGray.copy(alpha = 0.7f),
                                Color.DarkGray.copy(alpha = 0.9f),
                            )
                        )
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = song.title,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                )
            }

            if (isCurrentlyPlaying) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Color.Black.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DotsIndicator(
    totalDots: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalDots) { index ->
            val dotColor = if (index == selectedIndex) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            }
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(dotColor)
            )
            if (index < totalDots - 1) {
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}

@Composable
fun WelcomeHeader(
    stats: ListeningStats?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
        val greeting = when (currentHour) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }

        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )


        if (stats != null && stats.totalSongs > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatChip(
                    icon = Icons.Default.MusicNote,
                    label = "${stats.totalSongs} songs"
                )
                StatChip(
                    icon = Icons.Default.LibraryMusic,
                    label = "${stats.totalPlaylists} playlists"
                )
                if (stats.totalArtists > 0) {
                    StatChip(
                        icon = Icons.Default.Person,
                        label = "${stats.totalArtists} artists"
                    )
                }
            }
        }
    }
}

@Composable
fun StatChip(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun QuickActionsSection(
    onShuffleAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onShuffleAll,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Shuffle All")
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

@Composable
fun SongListSection(
    title: String,
    songs: List<Song>,
    onSongClick: (Int) -> Unit,
    nowPlayingMediaId: String?,
    isPlaying: Boolean
) {
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
            itemsIndexed(songs) { index, song ->
                val rememberedOnClick = remember { { onSongClick(index) } }
                val isCurrentlyPlayingItem = song.youtubeUrl.replace("music.youtube.com", "www.youtube.com") == nowPlayingMediaId
                SongCard(
                    song = song,
                    onClick = rememberedOnClick,
                    isCurrentlyPlaying = isCurrentlyPlayingItem,
                    isPlaying = isPlaying
                )
            }
        }
    }
}

@Composable
fun SongCard(
    song: Song,
    onClick: () -> Unit,
    isCurrentlyPlaying: Boolean,
    isPlaying: Boolean
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = song.title,
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(8.dp)),
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
                            shape = RoundedCornerShape(8.dp)
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
            text = song.title,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = song.artist,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp
        )
    }
}

@Composable
fun QuickAccessPlaylistsSection(
    playlists: List<QuickAccessPlaylist>,
    onPlaylistClick: (Long) -> Unit
) {
    Column {
        Text(
            text = "Your Playlists",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(playlists) { _, playlist ->
                PlaylistQuickCard(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist.playlistId) }
                )
            }
        }
    }
}

@Composable
fun PlaylistQuickCard(
    playlist: QuickAccessPlaylist,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${playlist.songCount} songs",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

