package com.example.m.ui.library.edit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.m.data.database.Song
import org.burnoutcrew.reorderable.ReorderableLazyListState
import org.burnoutcrew.reorderable.detectReorder

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditSongItem(
    song: Song,
    onRemoveClick: () -> Unit,
    state: ReorderableLazyListState,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier,
        headlineContent = { Text(song.title, maxLines = 1, modifier = Modifier.basicMarquee()) },
        supportingContent = { Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = song.title,
                modifier = Modifier.size(56.dp),
                contentScale = ContentScale.Crop
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRemoveClick) {
                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove song")
                }
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Drag Handle Icon",
                    modifier = Modifier.detectReorder(state)
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = MaterialTheme.colorScheme.onSurface,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}