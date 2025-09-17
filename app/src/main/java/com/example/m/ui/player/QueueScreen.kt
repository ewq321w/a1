// file: com/example/m/ui/player/QueueScreen.kt
package com.example.m.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

@Composable
fun QueueTabContent(
    queue: List<MediaItem>,
    currentMediaItemIndex: Int,
    onPlayItem: (Int) -> Unit,
    onMoveItem: (from: Int, to: Int) -> Unit
) {
    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to -> onMoveItem(from.index, to.index) }
    )

    LazyColumn(
        state = reorderableState.listState,
        modifier = Modifier
            .fillMaxSize()
            .reorderable(reorderableState)
    ) {
        itemsIndexed(queue, key = { index, item -> "${item.mediaId}-$index" }) { index, item ->
            ReorderableItem(reorderableState, key = "${item.mediaId}-$index") {
                val isPlaying = index == currentMediaItemIndex
                QueueItem(
                    mediaItem = item,
                    isPlaying = isPlaying,
                    onPlay = { onPlayItem(index) },
                    modifier = Modifier.detectReorderAfterLongPress(reorderableState)
                )
            }
        }
    }
}

@Composable
private fun QueueItem(
    mediaItem: MediaItem,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    ListItem(
        headlineContent = {
            Text(
                text = mediaItem.mediaMetadata.title?.toString() ?: "Unknown Title",
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown Artist",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            if (isPlaying) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = "Currently Playing",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    modifier = modifier.padding(end = 10.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onPlay)
    )
}