package com.example.m.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.m.R

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    artworkUri: String,
    songTitle: String,
    artistName: String,
    isPlaying: Boolean,
    currentPosition: Long,
    totalDuration: Long,
    onPlayPauseClicked: () -> Unit,
    onContainerClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (totalDuration > 0) {
        currentPosition.toFloat() / totalDuration.toFloat()
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onContainerClicked)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            AsyncImage(
                model = artworkUri,
                contentDescription = "Album Art",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(3.dp)),
                contentScale = ContentScale.Crop,
                error = painterResource(id = R.drawable.placeholder_gray),
                placeholder = painterResource(id = R.drawable.placeholder_gray)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Title and Artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = songTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    text = artistName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Play/Pause Button
            IconButton(onClick = onPlayPauseClicked) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(25.dp)
                )
            }
        }

        // Progress Bar
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}