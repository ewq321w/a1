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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
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
                                isPlaying = uiState.isPlaying,
                                isRefreshing = uiState.isRefreshing
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
                                isPlaying = uiState.isPlaying,
                                mainViewModel = mainViewModel,
                                isRefreshing = uiState.isRefreshing
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
                                isPlaying = uiState.isPlaying,
                                mainViewModel = mainViewModel,
                                isRefreshing = uiState.isRefreshing
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
    isPlaying: Boolean,
    isRefreshing: Boolean
) {
    val pagerState = rememberPagerState(
        pageCount = { ceil(songs.size / 9f).toInt() },
        initialPage = 0
    )

    // Reset to first page immediately when refresh starts
    LaunchedEffect(isRefreshing) {
        if (isRefreshing && pagerState.currentPage != 0) {
            pagerState.scrollToPage(0)
        }
    }

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
    isPlaying: Boolean,
    mainViewModel: MainViewModel,
    isRefreshing: Boolean
) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(
        pageCount = { ceil(items.size / 4f).toInt() },
        initialPage = 0
    )

    // Reset to first page immediately when refresh starts
    LaunchedEffect(isRefreshing) {
        if (isRefreshing && pagerState.currentPage != 0) {
            pagerState.scrollToPage(0)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Fixed height container to prevent dots indicator from moving
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp) // Fixed height for 4 songs with spacing
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(end = 24.dp),
                pageSpacing = 0.dp
            ) { pageIndex ->
                val startIndex = pageIndex * 4
                val endIndex = min(startIndex + 4, items.size)

                // Page content with consistent spacing
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    for (index in startIndex until endIndex) {
                        val item = items[index]
                        val normalizedUrl = item.url?.replace("music.youtube.com", "www.youtube.com")
                        val isCurrentlyPlaying = normalizedUrl == nowPlayingMediaId

                        DiscoveryMixItem(
                            item = item,
                            isPlaying = isCurrentlyPlaying && isPlaying,
                            onPlay = { onItemClick(index) },
                            mainViewModel = mainViewModel,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // Add empty space to maintain consistent height for incomplete pages
                    if (endIndex - startIndex < 4) {
                        repeat(4 - (endIndex - startIndex)) {
                            Spacer(modifier = Modifier.height(72.5.dp))
                        }
                    }
                }
            }
        }

        // Add page indicator if there are multiple pages
        if (pagerState.pageCount > 1) {
            DotsIndicator(
                totalDots = pagerState.pageCount,
                selectedIndex = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun DiscoveryMixItem(
    item: StreamInfoItem,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isPlaying) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color.Transparent)
            .clickable(onClick = onPlay)
            .heightIn(min = 71.dp)
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val highQualityThumbnailUrl = item.getHighQualityThumbnailUrl()

        AsyncImage(
            model = highQualityThumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(55.dp)
                .aspectRatio(1f, matchHeightConstraintsFirst = false)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop,
            error = painterResource(id = R.drawable.placeholder_gray),
            placeholder = painterResource(id = R.drawable.placeholder_gray)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.name ?: "Unknown",
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Get library/download status from local songs
                val allLocalSongs by mainViewModel.allLocalSongs.collectAsState()
                val normalizedUrl = item.url?.replace("music.youtube.com", "www.youtube.com")
                val correspondingSong = allLocalSongs.find { it.youtubeUrl == normalizedUrl }

                // Show download/library status icon
                if (correspondingSong != null && (correspondingSong.downloadStatus != com.example.m.data.database.DownloadStatus.NOT_DOWNLOADED || correspondingSong.isInLibrary)) {
                    Box(
                        modifier = Modifier.width(18.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val iconSize = 14.dp
                        when {
                            correspondingSong.downloadStatus == com.example.m.data.database.DownloadStatus.DOWNLOADING -> {
                                CircularProgressIndicator(
                                    progress = { correspondingSong.downloadProgress / 100f },
                                    modifier = Modifier.size(iconSize),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            correspondingSong.downloadStatus == com.example.m.data.database.DownloadStatus.QUEUED -> {
                                Icon(
                                    imageVector = Icons.Default.HourglassTop,
                                    contentDescription = "Queued",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                            correspondingSong.downloadStatus == com.example.m.data.database.DownloadStatus.FAILED -> {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Failed",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                            correspondingSong.downloadStatus == com.example.m.data.database.DownloadStatus.DOWNLOADED -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                            correspondingSong.isInLibrary -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "In Library",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = item.uploaderName ?: "Unknown Artist",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Play next") },
                    onClick = {
                        mainViewModel.addToQueueNext(item)
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    onClick = {
                        mainViewModel.addToQueue(item)
                        showMenu = false
                    }
                )
                // Get library/download status from local songs
                val allLocalSongs by mainViewModel.allLocalSongs.collectAsState()
                val normalizedUrl = item.url?.replace("music.youtube.com", "www.youtube.com")
                val correspondingSong = allLocalSongs.find { it.youtubeUrl == normalizedUrl }
                val isInLibrary = correspondingSong?.isInLibrary == true

                DropdownMenuItem(
                    text = { Text(if (isInLibrary) "In Library" else "Add to Library") },
                    enabled = !isInLibrary,
                    onClick = {
                        mainViewModel.libraryActionsManager.addToLibrary(item)
                        showMenu = false
                    }
                )
            }
        }
    }
}

@Composable
fun SongListSection(
    title: String,
    songs: List<Song>,
    onSongClick: (Int) -> Unit,
    nowPlayingMediaId: String?,
    isPlaying: Boolean,
    mainViewModel: MainViewModel,
    isRefreshing: Boolean
) {
    if (songs.isEmpty()) return

    val pagerState = rememberPagerState(
        pageCount = { ceil(songs.size / 4f).toInt() },
        initialPage = 0
    )

    // Reset to first page immediately when refresh starts
    LaunchedEffect(isRefreshing) {
        if (isRefreshing && pagerState.currentPage != 0) {
            pagerState.scrollToPage(0)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Fixed height container to prevent dots indicator from moving
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp) // Fixed height for 4 songs with spacing
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(end = 24.dp),
                pageSpacing = 0.dp
            ) { pageIndex ->
                val startIndex = pageIndex * 4
                val endIndex = min(startIndex + 4, songs.size)

                // Page content with consistent spacing
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    for (index in startIndex until endIndex) {
                        val song = songs[index]
                        val isCurrentlyPlaying = song.youtubeUrl.replace("music.youtube.com", "www.youtube.com") == nowPlayingMediaId

                        RecentlyAddedItem(
                            song = song,
                            isPlaying = isCurrentlyPlaying && isPlaying,
                            onPlay = { onSongClick(index) },
                            mainViewModel = mainViewModel,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // Add empty space to maintain consistent height for incomplete pages
                    if (endIndex - startIndex < 4) {
                        repeat(4 - (endIndex - startIndex)) {
                            Spacer(modifier = Modifier.height(72.5.dp))
                        }
                    }
                }
            }
        }

        // Add page indicator if there are multiple pages
        if (pagerState.pageCount > 1) {
            DotsIndicator(
                totalDots = pagerState.pageCount,
                selectedIndex = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun RecentlyAddedItem(
    song: Song,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isPlaying) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color.Transparent)
            .clickable(onClick = onPlay)
            .heightIn(min = 71.dp)
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(55.dp)
                .aspectRatio(1f, matchHeightConstraintsFirst = false)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop,
            error = painterResource(id = R.drawable.placeholder_gray),
            placeholder = painterResource(id = R.drawable.placeholder_gray)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Show download/library status icon
                if (song.downloadStatus != com.example.m.data.database.DownloadStatus.NOT_DOWNLOADED || song.isInLibrary) {
                    Box(
                        modifier = Modifier.width(18.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val iconSize = 14.dp
                        when {
                            song.downloadStatus == com.example.m.data.database.DownloadStatus.DOWNLOADING -> {
                                CircularProgressIndicator(
                                    progress = { song.downloadProgress / 100f },
                                    modifier = Modifier.size(iconSize),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            song.downloadStatus == com.example.m.data.database.DownloadStatus.QUEUED -> {
                                Icon(
                                    imageVector = Icons.Default.HourglassTop,
                                    contentDescription = "Queued",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                            song.downloadStatus == com.example.m.data.database.DownloadStatus.FAILED -> {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Failed",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                            song.downloadStatus == com.example.m.data.database.DownloadStatus.DOWNLOADED -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                            song.isInLibrary -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "In Library",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = song.artist,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Play next") },
                    onClick = {
                        mainViewModel.addSongToQueueNext(song)
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    onClick = {
                        mainViewModel.addSongToQueue(song)
                        showMenu = false
                    }
                )
                if (!song.isInLibrary) {
                    DropdownMenuItem(
                        text = { Text("Add to Library") },
                        onClick = {
                            mainViewModel.libraryActionsManager.addToLibrary(song)
                            showMenu = false
                        }
                    )
                }
            }
        }
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

