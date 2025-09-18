// file: com/example/m/ui/player/QueueScreen.kt
package com.example.m.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.m.data.database.Song
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

@Composable
fun QueueTabContent(
    queue: List<Pair<String, Song?>>,
    isLoading: Boolean,
    currentMediaItemIndex: Int,
    onPlayItem: (Int) -> Unit,
    onMoveItem: (from: Int, to: Int) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            val reorderableState = rememberReorderableLazyListState(
                onMove = { from, to -> onMoveItem(from.index, to.index) }
            )
            val items = remember(queue) { queue.filter { it.second != null } }

            LaunchedEffect(Unit) {
                if (items.indices.contains(currentMediaItemIndex)) {
                    reorderableState.listState.scrollToItem(index = currentMediaItemIndex)
                }
            }

            LazyColumn(
                state = reorderableState.listState,
                modifier = Modifier
                    .fillMaxSize()
                    .reorderable(reorderableState),
                contentPadding = PaddingValues(bottom = 36.dp, top = 12.dp)
            ) {
                itemsIndexed(items, key = { _, item -> item.first }) { index, item ->
                    val song = item.second!!
                    ReorderableItem(reorderableState, key = item.first) { isDragging ->
                        val isPlaying = index == currentMediaItemIndex
                        QueueItem(
                            song = song,
                            isPlaying = isPlaying,
                            onPlay = { onPlayItem(index) },
                            modifier = Modifier.shadow(if (isDragging) 4.dp else 0.dp),
                            dragHandleModifier = Modifier.detectReorder(reorderableState)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueItem(
    song: Song,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier
) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant

    ListItem(
        headlineContent = {
            Text(
                text = song.title,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        supportingContent = {
            Text(
                text = song.artist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = "Album art for ${song.title}",
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(3.dp)),
                contentScale = ContentScale.Crop,
                placeholder = remember { ColorPainter(placeholderColor) },
                error = remember { ColorPainter(placeholderColor) }
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    modifier = dragHandleModifier,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isPlaying) Color.White.copy(alpha = 0.1f) else Color.Transparent
        ),
        modifier = modifier.clickable(onClick = onPlay).defaultMinSize(minHeight = 72.dp)
    )
}