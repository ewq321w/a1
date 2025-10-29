// file: com/example/m/ui/search/details/SearchedArtistDetailScreen.kt
package com.example.m.ui.search.details

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.m.data.database.Song
import com.example.m.managers.DialogState
import com.example.m.managers.PlaylistActionState
import com.example.m.ui.common.getAvatar
import com.example.m.ui.common.getBanner
import com.example.m.ui.common.getHighQualityThumbnailUrl
import com.example.m.ui.library.components.*
import com.example.m.ui.search.AlbumItem
import com.example.m.ui.search.SearchResultItem
import com.example.m.ui.search.formatSubscriberCount
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchedArtistDetailScreen(
    onBack: () -> Unit,
    onAlbumClick: (searchType: String, albumUrl: String) -> Unit,
    onGoToSongs: (searchType: String, channelUrl: String, artistName: String) -> Unit,
    onGoToReleases: (searchType: String, channelUrl: String, artistName: String) -> Unit,
    viewModel: SearchedArtistDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dialogState by viewModel.dialogState.collectAsState()


    when (val state = dialogState) {
        is DialogState.CreateGroup -> {
            TextFieldDialog(
                title = "New Library Group",
                label = "Group name",
                confirmButtonText = "Create",
                onDismiss = { viewModel.onEvent(SearchedArtistDetailEvent.DismissDialog) },
                onConfirm = { name -> viewModel.onEvent(SearchedArtistDetailEvent.RequestCreateGroup(name)) },
                content = {
                    if (state.isFirstGroup) {
                        Text(
                            "To start your library, please create a group to add songs to.",
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            )
        }
        is DialogState.SelectGroup -> {
            SelectLibraryGroupDialog(
                groups = state.groups,
                onDismiss = { viewModel.onEvent(SearchedArtistDetailEvent.DismissDialog) },
                onGroupSelected = { groupId -> viewModel.onEvent(SearchedArtistDetailEvent.SelectGroup(groupId)) },
                onCreateNewGroup = { viewModel.onEvent(SearchedArtistDetailEvent.DismissDialog) }
            )
        }
        is DialogState.Conflict -> {
            ArtistGroupConflictDialog(
                artistName = state.song.artist,
                conflictingGroupName = state.conflict.conflictingGroupName,
                targetGroupName = state.targetGroupName,
                onDismiss = { viewModel.onEvent(SearchedArtistDetailEvent.DismissDialog) },
                onMoveArtistToTargetGroup = { viewModel.onEvent(SearchedArtistDetailEvent.ResolveConflict) }
            )
        }
        is DialogState.Hidden -> {}
    }


    if (uiState.showConfirmAddAllDialog) {
        val artistName = uiState.channelInfo?.name ?: "this artist"
        ConfirmAddAllToLibraryDialog(
            itemName = artistName,
            onDismiss = { viewModel.onEvent(SearchedArtistDetailEvent.DismissConfirmAddAllToLibraryDialog) },
            onConfirm = { viewModel.onEvent(SearchedArtistDetailEvent.ConfirmAddAllToLibrary) }
        )
    }

    Scaffold { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.errorMessage != null -> {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text(uiState.errorMessage!!)
                }
            }
            else -> {
                LazyColumn(modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()) {
                    item {
                        ArtistHeader(
                            onBack = onBack,
                            bannerUrl = uiState.channelInfo?.getBanner(),
                            avatarUrl = uiState.channelInfo?.getAvatar(),
                            artistName = uiState.channelInfo?.name ?: "Unknown Artist",
                            subscriberCount = uiState.channelInfo?.subscriberCount ?: -1
                        )
                    }

                    if (uiState.songs.isNotEmpty()) {
                        item {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 4.dp),
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val headerText = if (uiState.searchType == "music") "Popular Songs" else "Videos"
                                    Text(text = headerText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                                    TextButton(
                                        onClick = {
                                            val info = uiState.channelInfo
                                            if (info?.url != null && info.name != null) {
                                                onGoToSongs(uiState.searchType, info.url, info.name)
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Text("More")
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                        items(uiState.songs.take(5)) { item ->
                            val index = uiState.songs.indexOf(item)
                            val normalizedUrl = item.result.streamInfo.url?.replace("music.youtube.com", "www.youtube.com")
                            val isPlaying = normalizedUrl == uiState.nowPlayingMediaId || item.localSong?.localFilePath == uiState.nowPlayingMediaId
                            SearchResultItem(
                                result = item.result,
                                localSong = item.localSong,
                                isPlaying = isPlaying,
                                isSong = uiState.searchType == "music",
                                imageLoader = viewModel.imageLoader,
                                onPlay = { viewModel.onEvent(SearchedArtistDetailEvent.SongSelected(index)) },
                                onAddToLibrary = { viewModel.onEvent(SearchedArtistDetailEvent.AddToLibrary(item.result.streamInfo)) },
                                onPlayNext = { viewModel.onEvent(SearchedArtistDetailEvent.PlayNext(item.result.streamInfo)) },
                                onAddToQueue = { viewModel.onEvent(SearchedArtistDetailEvent.AddToQueue(item.result.streamInfo)) }
                            )
                        }
                    }

                    if (uiState.releases.isNotEmpty()) {
                        item {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 4.dp),
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val headerText = if (uiState.searchType == "music") "Releases" else "Playlists"
                                    Text(text = headerText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                                    TextButton(
                                        onClick = {
                                            val info = uiState.channelInfo
                                            if (info?.url != null && info.name != null) {
                                                onGoToReleases(uiState.searchType, info.url, info.name)
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Text("More")
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                        item {
                            LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(uiState.releases.take(10)) { release ->
                                    AlbumItem(
                                        album = release,
                                        imageLoader = viewModel.imageLoader,
                                        onClick = { release.url?.let { onAlbumClick(uiState.searchType, it) } },
                                        showArtist = false
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistHeader(onBack: () -> Unit, bannerUrl: String?, avatarUrl: String?, artistName: String, subscriberCount: Long) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = Modifier.fillMaxWidth()) {
        Box {
            AsyncImage(
                model = bannerUrl,
                contentDescription = "Artist Banner",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1060f / 175f),
                contentScale = ContentScale.Crop,
                placeholder = remember { ColorPainter(placeholderColor) },
                error = remember { ColorPainter(placeholderColor) }
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Top) {
                IconButton(onClick = onBack, modifier = Modifier
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
        }
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Artist Avatar",
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape),
                placeholder = remember { ColorPainter(placeholderColor) },
                error = remember { ColorPainter(placeholderColor) }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(text = artistName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.basicMarquee())
                if (subscriberCount >= 0) {
                    Text(text = formatSubscriberCount(subscriberCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}