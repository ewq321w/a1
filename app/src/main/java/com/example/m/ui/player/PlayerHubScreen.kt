// file: com/example/m/ui/player/PlayerHubScreen.kt
package com.example.m.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaMetadata
import coil.compose.AsyncImage
import com.example.m.data.database.DownloadStatus
import com.example.m.data.database.Song
import com.example.m.ui.common.GradientBackground
import com.example.m.ui.search.SearchResultForList
import com.example.m.ui.library.components.TranslucentDropdownMenu
import com.example.m.ui.main.MainEvent
import com.example.m.ui.main.MainViewModel
import org.burnoutcrew.reorderable.ItemPosition
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import kotlin.math.ceil
import kotlin.math.min

@Composable
fun PlayerHubScreen(
    onDismiss: () -> Unit = {},
    mainViewModel: MainViewModel = hiltViewModel(),
    animationProgress: Float = 0f,  // From sheet
    initialTabIndex: Int = 0  // New parameter to set initial tab
) {
    val (color1, color2) = mainViewModel.playerGradientColors.value
    val nowPlaying by mainViewModel.nowPlaying.collectAsState()
    val isPlaying by mainViewModel.isPlaying.collectAsState()
    val playerState by mainViewModel.playerState.collectAsState()
    val songQueue by mainViewModel.displayQueue.collectAsState()
    val currentLyrics by mainViewModel.currentLyrics.collectAsState()
    val isLoadingLyrics by mainViewModel.isLoadingLyrics.collectAsState()
    val lyricsError by mainViewModel.lyricsError.collectAsState()
    val currentMediaId by mainViewModel.currentMediaId.collectAsState() // Add this line

    // New unfiltered results
    val unfilteredMusicResults by mainViewModel.unfilteredMusicResults.collectAsState()
    val unfilteredRegularResults by mainViewModel.unfilteredRegularResults.collectAsState()
    val isLoadingUnfilteredResults by mainViewModel.isLoadingUnfilteredResults.collectAsState()

    // YouTube Mix results
    val youtubeMixResults by mainViewModel.youtubeMixResults.collectAsState()

    // Track progress from sheet
    var currentProgress by remember { mutableStateOf(animationProgress) }
    // Update progress if passed from parent
    LaunchedEffect(animationProgress) {
        currentProgress = animationProgress
    }

    // Tab state - reset to initial tab index every time PlayerHub opens
    var selectedTabIndex by remember(initialTabIndex) { mutableStateOf(initialTabIndex) }

    // Data readiness for conditional loading
    val isDataReady by remember {
        derivedStateOf {
            songQueue.any { it.second != null }
        }
    }

    // Always render the full structure for smooth sliding
    GradientBackground(
        gradientColor1 = color1,
        gradientColor2 = color2,
        fadeStartFraction = 1.0f,
        fadeEndFraction = 1.75f,
        radialGradientRadiusMultiplier = 10.0f,
        radialGradientAlpha = 0.15f
    ) {
        // Ensure related songs and lyrics are loaded when PlayerHubScreen opens or song changes
        LaunchedEffect(currentMediaId) {
            // Also trigger lyrics loading immediately to ensure no delays
            mainViewModel.ensureLyricsLoaded()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Content structure always renders (with placeholders)
            Column(modifier = Modifier.fillMaxSize()) {
                // Header always shows (lightweight with placeholder)
                PlayerHubHeader(
                    mediaMetadata = nowPlaying,
                    isPlaying = isPlaying,
                    playerState = playerState,
                    onTogglePlayPause = { mainViewModel.onEvent(MainEvent.TogglePlayPause) }
                )

                // QueueContent with progress-based phasing
                TabContent(
                    mainViewModel = mainViewModel,
                    animationProgress = currentProgress,
                    songQueue = songQueue,
                    relatedSongs = emptyList(), // Pass empty list
                    isLoadingRelatedSongs = false, // Pass false
                    unfilteredMusicResults = unfilteredMusicResults,
                    unfilteredRegularResults = unfilteredRegularResults,
                    youtubeMixResults = youtubeMixResults, // Pass YouTube Mix results
                    isLoadingUnfilteredResults = isLoadingUnfilteredResults,
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { selectedTabIndex = it },
                    nowPlayingMediaId = currentMediaId, // Use currentMediaId instead of nowPlaying?.mediaId
                    currentLyrics = currentLyrics,
                    isLoadingLyrics = isLoadingLyrics,
                    lyricsError = lyricsError
                )
            }

            // Overlay spinner only during very early animation AND if no data yet
            if (currentProgress < 0.3f && !isDataReady) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun TabContent(
    mainViewModel: MainViewModel,
    animationProgress: Float,
    songQueue: List<Pair<String, Song?>>,
    relatedSongs: List<SearchResultForList>,
    isLoadingRelatedSongs: Boolean,
    unfilteredMusicResults: List<SearchResultForList>,
    unfilteredRegularResults: List<SearchResultForList>,
    youtubeMixResults: List<SearchResultForList>,
    isLoadingUnfilteredResults: Boolean,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    nowPlayingMediaId: String?, // New parameter for current playing media ID
    currentLyrics: String?,
    isLoadingLyrics: Boolean,
    lyricsError: String?
) {
    val currentIndex by mainViewModel.currentMediaItemIndex.collectAsState()
    val (color1, color2) = mainViewModel.playerGradientColors.value

    // Filter queue items directly - recalculates on every composition when songQueue changes
    val filteredQueueItems = songQueue.filter { it.second != null }
        .map { it.first to it.second!! }

    // Local queue for reorderable to prevent jumping during drag
    var localQueue by remember { mutableStateOf(filteredQueueItems) }

    // Track if we're currently reordering to prevent scroll conflicts
    var isReordering by remember { mutableStateOf(false) }

    // Update localQueue whenever songQueue changes and we're not reordering
    LaunchedEffect(songQueue) {
        if (!isReordering) {
            localQueue = filteredQueueItems
        }
    }

    // Track whether we've already scrolled initially to prevent re-scrolling when becoming reorderable
    var hasScrolledInitially by remember { mutableStateOf(false) }

    // Track the last currentIndex we scrolled to, to only scroll on song changes
    var lastScrolledIndex by remember { mutableStateOf(-1) }

    // Determine if reorder is enabled based on animation progress and tab selection
    val isReorderEnabled = animationProgress >= 0.75f && selectedTabIndex == 0  // Only for Queue tab

    // onMove lambda for reordering
    val onMoveLambda = { from: ItemPosition, to: ItemPosition ->
        if (isReorderEnabled) {
            isReordering = true // Set reordering flag
            mainViewModel.moveQueueItem(from.index, to.index)
            // Update local queue immediately to reflect the move
            val list = localQueue.toMutableList()
            val item = list.removeAt(from.index)
            list.add(to.index, item)
            localQueue = list
        }
    }
    val updatedOnMove = rememberUpdatedState(onMoveLambda)

    // Set initial scroll position to currentIndex for smooth initial positioning
    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (selectedTabIndex == 0) {
            currentIndex.coerceAtMost(maxOf(0, filteredQueueItems.size - 1))
        } else 0
    )

    // Create reorderable state with the pre-initialized list state
    val reorderableState = rememberReorderableLazyListState(
        listState = lazyListState,
        onMove = { from, to -> updatedOnMove.value(from, to) }
    )

    // Scroll to new currentIndex only when:
    // 1. We haven't scrolled initially and animation is complete
    // 2. OR the currentIndex actually changed (new song) AND we're not currently reordering
    LaunchedEffect(currentIndex, animationProgress, selectedTabIndex) {
        if (selectedTabIndex == 0 && filteredQueueItems.indices.contains(currentIndex) && !isReordering) {
            val shouldScrollInitially = !hasScrolledInitially && animationProgress >= 1f
            val shouldScrollForNewSong = hasScrolledInitially && currentIndex != lastScrolledIndex

            if (shouldScrollInitially || shouldScrollForNewSong) {
                reorderableState.listState.animateScrollToItem(currentIndex, 0)
                hasScrolledInitially = true
                lastScrolledIndex = currentIndex
            }
        }
    }

    // Reset reordering flag after a delay to allow for natural song changes
    LaunchedEffect(isReordering) {
        if (isReordering) {
            kotlinx.coroutines.delay(500) // Wait 500ms after reordering before allowing auto-scroll again
            isReordering = false
        }
    }

    // Reset scroll tracking when tab changes or when PlayerHubScreen is reopened
    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex == 0) {
            // Reset when switching to queue tab
            hasScrolledInitially = false
            lastScrolledIndex = -1
            isReordering = false // Also reset reordering state
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
        ) {
            GradientBackground(
                gradientColor1 = color1,
                gradientColor2 = color2,
                fadeStartFraction = 1.0f,
                fadeEndFraction = 1.5f,
                radialGradientRadiusMultiplier = 8.0f,
                radialGradientAlpha = 0.25f
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = Color.White.copy(alpha = 0.5f),
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = Color.White
                            )
                        },
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        // Queue Tab
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { onTabSelected(0) },
                            text = {
                                Text(
                                    "Up Next",
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(bottom = 3.dp),
                                    color = Color.White
                                )
                            }
                        )

                        // Lyrics Tab
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { onTabSelected(1) },
                            text = {
                                Text(
                                    "Lyrics",
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(bottom = 3.dp),
                                    color = Color.White
                                )
                            }
                        )

                        // Related Tab
                        Tab(
                            selected = selectedTabIndex == 2,
                            onClick = { onTabSelected(2) },
                            text = {
                                Text(
                                    "Related",
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(bottom = 3.dp),
                                    color = Color.White
                                )
                            }
                        )
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val showSkeleton = animationProgress >= 0.3f && animationProgress < 1f
                        if (showSkeleton) {
                            // Phase 2: Empty LazyColumn for outline (no items, no placeholders)
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 36.dp, top = 12.dp)
                            ) {
                                // Empty - just the outline/structure
                            }
                        } else {
                            // Phase 3: Full content (when >=1f)
                            when (selectedTabIndex) {
                                0 -> {
                                    // Queue Tab Content
                                    LazyColumn(
                                        state = lazyListState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(
                                                if (isReorderEnabled) {
                                                    Modifier.reorderable(reorderableState)
                                                } else {
                                                    Modifier
                                                }
                                            ),
                                        contentPadding = PaddingValues(bottom = 36.dp, top = 12.dp)
                                    ) {
                                        itemsIndexed(
                                            items = localQueue,
                                            key = { index, item -> "${item.first}_$index" }
                                        ) { index, item ->
                                            if (isReorderEnabled) {
                                                ReorderableItem(reorderableState, key = "${item.first}_$index") { isDragging ->
                                                    QueueItem(
                                                        song = item.second,
                                                        isPlaying = index == currentIndex,
                                                        onPlay = { mainViewModel.skipToQueueItem(index) },
                                                        modifier = Modifier.shadow(if (isDragging) 4.dp else 0.dp),
                                                        dragHandleModifier = Modifier.detectReorder(reorderableState),
                                                        showDragHandle = true
                                                    )
                                                }
                                            } else {
                                                QueueItem(
                                                    song = item.second,
                                                    isPlaying = index == currentIndex,
                                                    onPlay = { mainViewModel.skipToQueueItem(index) },
                                                    showDragHandle = false
                                                )
                                            }
                                        }
                                    }
                                }
                                1 -> {
                                    // Lyrics Tab Content
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        when {
                                            isLoadingLyrics -> {
                                                CircularProgressIndicator(
                                                    color = Color.White,
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                            lyricsError != null -> {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = lyricsError ?: "Error loading lyrics",
                                                        color = Color.White.copy(alpha = 0.7f),
                                                        fontSize = 14.sp,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                    TextButton(
                                                        onClick = { mainViewModel.ensureLyricsLoaded() }
                                                    ) {
                                                        Text(
                                                            "Retry",
                                                            color = Color.White.copy(alpha = 0.8f),
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                }
                                            }
                                            currentLyrics != null -> {
                                                // --- FIX START: Reverted to a single Text composable for smooth scrolling ---
                                                LazyColumn(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentPadding = PaddingValues(
                                                        start = 20.dp,
                                                        end = 20.dp,
                                                        top = 16.dp,
                                                        bottom = 32.dp
                                                    )
                                                ) {
                                                    item {
                                                        Text(
                                                            text = currentLyrics,
                                                            color = Color.White.copy(alpha = 0.9f),
                                                            fontSize = 16.sp,
                                                            lineHeight = 24.sp,
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    }
                                                }
                                                // --- FIX END ---
                                            }
                                            else -> {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = "No lyrics found",
                                                        color = Color.White.copy(alpha = 0.7f),
                                                        fontSize = 14.sp
                                                    )
                                                    TextButton(
                                                        onClick = { mainViewModel.ensureLyricsLoaded() }
                                                    ) {
                                                        Text(
                                                            "Search again",
                                                            color = Color.White.copy(alpha = 0.8f),
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                2 -> {
                                    // Related Songs Tab Content - Three sections with paging
                                    when {
                                        isLoadingUnfilteredResults -> {
                                            // Show loading spinner when any section is loading
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    color = Color.White,
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                        }
                                        unfilteredMusicResults.isNotEmpty() || unfilteredRegularResults.isNotEmpty() -> {
                                            // Show all three sections in a scrollable column
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                contentPadding = PaddingValues(bottom = 36.dp, top = 12.dp)
                                            ) {
                                                // Section 1: Unfiltered Music Search Results (More like this)
                                                if (unfilteredMusicResults.isNotEmpty()) {
                                                    item {
                                                        RelatedSection(
                                                            title = "More like this",
                                                            items = unfilteredMusicResults,
                                                            nowPlayingMediaId = nowPlayingMediaId,
                                                            mainViewModel = mainViewModel,
                                                            useYoutubeMixQueue = true
                                                        )
                                                        Spacer(modifier = Modifier.height(24.dp))
                                                    }
                                                }

                                                // Section 2: Unfiltered Regular YouTube Results (renamed)
                                                if (unfilteredRegularResults.isNotEmpty()) {
                                                    item {
                                                        RelatedSection(
                                                            title = "Related Videos",
                                                            items = unfilteredRegularResults,
                                                            nowPlayingMediaId = nowPlayingMediaId,
                                                            mainViewModel = mainViewModel,
                                                            useYoutubeMixQueue = true
                                                        )
                                                        Spacer(modifier = Modifier.height(24.dp))
                                                    }
                                                }

                                                // Section 3: YouTube Mix Results
                                                if (youtubeMixResults.isNotEmpty()) {
                                                    item {
                                                        RelatedSection(
                                                            title = "YouTube Mix",
                                                            items = youtubeMixResults,
                                                            nowPlayingMediaId = nowPlayingMediaId,
                                                            mainViewModel = mainViewModel,
                                                            useYoutubeMixQueue = true
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        else -> {
                                            // Show empty state only when not loading and no content found
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "No related content found",
                                                    color = Color.White.copy(alpha = 0.7f),
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerHubHeader(
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    playerState: Int,
    onTogglePlayPause: () -> Unit
) {
    val placeholderColor = Color.Gray  // Simple placeholder for immediate render

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 5.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = mediaMetadata?.artworkUri,
                contentDescription = "Album Art",
                modifier = Modifier
                    .padding(start = 10.dp)
                    .size(48.dp)
                    .clip(RoundedCornerShape(3.dp)),
                contentScale = ContentScale.Crop,
                placeholder = remember { ColorPainter(placeholderColor) },
                error = remember { ColorPainter(placeholderColor) }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaMetadata?.title?.toString() ?: "Unknown Title",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
                Text(
                    text = mediaMetadata?.artist?.toString() ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (playerState == androidx.media3.common.Player.STATE_BUFFERING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(25.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onTogglePlayPause) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(25.dp),
                            tint = Color.White
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

@Composable
private fun QueueItem(
    song: Song,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    showDragHandle: Boolean = true
) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isPlaying) Color.White.copy(alpha = 0.075f) else Color.Transparent)
            .clickable(onClick = onPlay)
            .heightIn(min = 68.dp) // Use minimum height instead of fixed height
            .padding(start = 18.dp, top = 8.dp, end = 16.dp, bottom = 8.dp), // Fixed padding syntax
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(50.dp) // Updated from 48dp to 50dp for consistency
                .clip(RoundedCornerShape(3.dp)),
            contentScale = ContentScale.Crop,
            placeholder = remember { ColorPainter(placeholderColor) },
            error = remember { ColorPainter(placeholderColor) }
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = Color.White,
                maxLines = 2, // Changed from 1 to 2 for better readability
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, // Changed back to bodyMedium
                fontWeight = FontWeight.Medium // Changed from SemiBold to Medium
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status indicator (same as search and library)
                if (song.downloadStatus != DownloadStatus.NOT_DOWNLOADED || song.isInLibrary) {
                    Box(
                        modifier = Modifier.width(18.dp), // Consistent size
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val iconSize = 14.dp // Consistent size
                        when {
                            song.downloadStatus == DownloadStatus.DOWNLOADING -> {
                                CircularProgressIndicator(
                                    progress = { song.downloadProgress / 100f },
                                    modifier = Modifier.size(iconSize),
                                    strokeWidth = 1.5.dp,
                                    color = Color.White
                                )
                            }
                            song.downloadStatus == DownloadStatus.QUEUED -> {
                                Icon(
                                    imageVector = Icons.Default.HourglassTop,
                                    contentDescription = "Queued",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                            song.downloadStatus == DownloadStatus.FAILED -> {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Failed",
                                    tint = Color.Red,
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                            song.downloadStatus == DownloadStatus.DOWNLOADED -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                            song.isInLibrary -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "In Library",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = song.artist,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (showDragHandle) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                modifier = dragHandleModifier.size(24.dp),
                tint = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun RelatedSongItem(
    searchResultForList: SearchResultForList,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false, // New parameter for playing state
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    var showMenu by remember { mutableStateOf(false) }

    val streamInfoItem = searchResultForList.result.streamInfo
    val correspondingSong = searchResultForList.localSong

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isPlaying) Color.White.copy(alpha = 0.075f) else Color.Transparent)
            .clickable(onClick = onPlay)
            .heightIn(min = 68.dp) // Use minimum height instead of fixed height
            .padding(start = 18.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically // Keep CenterVertically for proper alignment
    ) {
        AsyncImage(
            model = streamInfoItem.thumbnails.maxByOrNull { it.width * it.height }?.url,
            contentDescription = null,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(3.dp)),
            contentScale = ContentScale.Crop,
            placeholder = remember { ColorPainter(placeholderColor) },
            error = remember { ColorPainter(placeholderColor) }
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center // Center the text content vertically
        ) {
            Text(
                text = streamInfoItem.name,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                val shouldShowStatusIcon = searchResultForList.result.isInLibrary || searchResultForList.result.isDownloaded

                if (shouldShowStatusIcon && correspondingSong != null) {
                    Box(
                        modifier = Modifier.width(18.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val iconSize = 14.dp
                        when {
                            correspondingSong.downloadStatus == DownloadStatus.DOWNLOADING -> {
                                CircularProgressIndicator(
                                    progress = { correspondingSong.downloadProgress / 100f },
                                    modifier = Modifier.size(iconSize),
                                    strokeWidth = 1.5.dp,
                                    color = Color.White
                                )
                            }
                            correspondingSong.downloadStatus == DownloadStatus.QUEUED -> {
                                Icon(
                                    imageVector = Icons.Default.HourglassTop,
                                    contentDescription = "Queued",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                            correspondingSong.downloadStatus == DownloadStatus.FAILED -> {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Failed",
                                    tint = Color.Red,
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                            correspondingSong.downloadStatus == DownloadStatus.DOWNLOADED -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                            correspondingSong.isInLibrary -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "In Library",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = streamInfoItem.uploaderName ?: "Unknown Artist",
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(40.dp) // Consistent button size
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
            TranslucentDropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Play next") },
                    onClick = {
                        mainViewModel.addToQueueNext(streamInfoItem)
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    onClick = {
                        mainViewModel.addToQueue(streamInfoItem)
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (searchResultForList.result.isInLibrary) "In Library" else "Add to Library") },
                    enabled = !searchResultForList.result.isInLibrary,
                    onClick = {
                        mainViewModel.libraryActionsManager.addToLibrary(streamInfoItem)
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to playlist") },
                    onClick = {
                        mainViewModel.playlistActionsManager.selectItem(streamInfoItem)
                        showMenu = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DotsIndicator(
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
            val dotColor = if (index == selectedIndex) Color.White else Color.White.copy(alpha = 0.3f)
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
private fun RelatedSection(
    title: String,
    items: List<SearchResultForList>,
    nowPlayingMediaId: String?,
    mainViewModel: MainViewModel,
    useYoutubeMixQueue: Boolean = false
) {
    val pagerState = rememberPagerState(
        pageCount = { ceil(items.size / 4f).toInt() },
        initialPage = 0
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp)
        )

        // Fixed height container to prevent dots indicator from moving
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(284.dp) // Fixed height for 4 songs (80dp each)
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
                        // Determine if this related song is currently playing
                        val normalizedUrl = item.result.streamInfo.url?.replace("music.youtube.com", "www.youtube.com")
                        val isPlaying = normalizedUrl == nowPlayingMediaId || item.localSong?.localFilePath == nowPlayingMediaId

                        RelatedSongItem(
                            searchResultForList = item,
                            isPlaying = isPlaying,
                            onPlay = {
                                if (useYoutubeMixQueue) {
                                    mainViewModel.playRelatedSongWithYoutubeMix(item.result.streamInfo)
                                } else {
                                    mainViewModel.playSingleSong(item.result.streamInfo)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // Add empty space to maintain consistent height for incomplete pages
                    if (endIndex - startIndex < 4) {
                        repeat(4 - (endIndex - startIndex)) {
                            Spacer(modifier = Modifier.height(68.dp)) // Updated to match RelatedSongItem height
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
