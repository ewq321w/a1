package com.example.m.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.m.R
import com.example.m.ui.common.getHighQualityThumbnailUrl
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val recentMix by viewModel.recentMix.collectAsState()
    val discoveryMix by viewModel.discoveryMix.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                windowInsets = TopAppBarDefaults.windowInsets
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
                    items = recentMix,
                    onItemClick = { index, _ ->
                        viewModel.playRecentMix(index)
                    }
                )
            }
            item {
                RecommendationSection(
                    title = "Discovery Mix",
                    items = discoveryMix,
                    onItemClick = { index, _ ->
                        viewModel.playDiscoveryMix(index)
                    }
                )
            }
        }
    }
}

@Composable
fun RecommendationSection(
    title: String,
    items: List<StreamInfoItem>,
    onItemClick: (Int, List<StreamInfoItem>) -> Unit
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
                    val rememberedOnClick = remember { { onItemClick(index, items) } }
                    RecommendationItem(
                        item = item,
                        onClick = rememberedOnClick
                    )
                }
            }
        }
    }
}

@Composable
fun RecommendationItem(
    item: StreamInfoItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        val highQualityThumbnailUrl = item.getHighQualityThumbnailUrl()

        AsyncImage(
            model = highQualityThumbnailUrl,
            contentDescription = item.name,
            modifier = Modifier
                .size(120.dp),
            contentScale = ContentScale.Crop,
            error = painterResource(id = R.drawable.placeholder_gray),
            placeholder = painterResource(id = R.drawable.placeholder_gray)
        )
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