package com.example.m.ui.library.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.m.ui.library.SongSortOrder
import com.example.m.ui.library.details.ArtistSortOrder
import com.example.m.ui.library.details.PlaylistSortOrder

@Composable
fun SongSortMenu(
    currentSortOrder: SongSortOrder,
    onSortOrderSelected: (SongSortOrder) -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showSortMenu = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort Options")
        }
        TranslucentDropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
            DropdownMenuItem(
                text = { Text("Artist") },
                onClick = { onSortOrderSelected(SongSortOrder.ARTIST); showSortMenu = false },
                trailingIcon = { if (currentSortOrder == SongSortOrder.ARTIST) Icon(Icons.Default.Check, contentDescription = "Selected") }
            )
            DropdownMenuItem(
                text = { Text("Title") },
                onClick = { onSortOrderSelected(SongSortOrder.TITLE); showSortMenu = false },
                trailingIcon = { if (currentSortOrder == SongSortOrder.TITLE) Icon(Icons.Default.Check, contentDescription = "Selected") }
            )
            DropdownMenuItem(
                text = { Text("Date Added") },
                onClick = { onSortOrderSelected(SongSortOrder.DATE_ADDED); showSortMenu = false },
                trailingIcon = { if (currentSortOrder == SongSortOrder.DATE_ADDED) Icon(Icons.Default.Check, contentDescription = "Selected") }
            )
            DropdownMenuItem(
                text = { Text("Play Count") },
                onClick = { onSortOrderSelected(SongSortOrder.PLAY_COUNT); showSortMenu = false },
                trailingIcon = { if (currentSortOrder == SongSortOrder.PLAY_COUNT) Icon(Icons.Default.Check, contentDescription = "Selected") }
            )
        }
    }
}

@Composable
fun PlaylistSortMenu(
    currentSortOrder: PlaylistSortOrder,
    onSortOrderSelected: (PlaylistSortOrder) -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showSortMenu = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort Options")
        }
        TranslucentDropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
            DropdownMenuItem(
                text = { Text("Custom Order") },
                onClick = { onSortOrderSelected(PlaylistSortOrder.CUSTOM); showSortMenu = false },
                trailingIcon = { if (currentSortOrder == PlaylistSortOrder.CUSTOM) Icon(Icons.Default.Check, contentDescription = "Selected") }
            )
            DropdownMenuItem(
                text = { Text("Title") },
                onClick = { onSortOrderSelected(PlaylistSortOrder.TITLE); showSortMenu = false },
                trailingIcon = { if (currentSortOrder == PlaylistSortOrder.TITLE) Icon(Icons.Default.Check, contentDescription = "Selected") }
            )
            DropdownMenuItem(
                text = { Text("Artist") },
                onClick = { onSortOrderSelected(PlaylistSortOrder.ARTIST); showSortMenu = false },
                trailingIcon = { if (currentSortOrder == PlaylistSortOrder.ARTIST) Icon(Icons.Default.Check, contentDescription = "Selected") }
            )
            DropdownMenuItem(
                text = { Text("Date Added") },
                onClick = { onSortOrderSelected(PlaylistSortOrder.DATE_ADDED); showSortMenu = false },
                trailingIcon = { if (currentSortOrder == PlaylistSortOrder.DATE_ADDED) Icon(Icons.Default.Check, contentDescription = "Selected") }
            )
            DropdownMenuItem(
                text = { Text("Play Count") },
                onClick = { onSortOrderSelected(PlaylistSortOrder.PLAY_COUNT); showSortMenu = false },
                trailingIcon = { if (currentSortOrder == PlaylistSortOrder.PLAY_COUNT) Icon(Icons.Default.Check, contentDescription = "Selected") }
            )
        }
    }
}

@Composable
fun ArtistSortMenu(
    currentSortOrder: ArtistSortOrder,
    onSortOrderSelected: (ArtistSortOrder) -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showSortMenu = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort Options")
        }
        TranslucentDropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
            DropdownMenuItem(
                text = { Text("Custom") },
                onClick = { onSortOrderSelected(ArtistSortOrder.CUSTOM); showSortMenu = false },
                trailingIcon = { if (currentSortOrder == ArtistSortOrder.CUSTOM) Icon(Icons.Default.Check, contentDescription = "Selected") }
            )
            DropdownMenuItem(
                text = { Text("Title (A-Z)") },
                onClick = { onSortOrderSelected(ArtistSortOrder.TITLE); showSortMenu = false },
                trailingIcon = { if (currentSortOrder == ArtistSortOrder.TITLE) Icon(Icons.Default.Check, contentDescription = "Selected") }
            )
            DropdownMenuItem(
                text = { Text("Date Added") },
                onClick = { onSortOrderSelected(ArtistSortOrder.DATE_ADDED); showSortMenu = false },
                trailingIcon = { if (currentSortOrder == ArtistSortOrder.DATE_ADDED) Icon(Icons.Default.Check, contentDescription = "Selected") }
            )
            DropdownMenuItem(
                text = { Text("Play Count") },
                onClick = { onSortOrderSelected(ArtistSortOrder.PLAY_COUNT); showSortMenu = false },
                trailingIcon = { if (currentSortOrder == ArtistSortOrder.PLAY_COUNT) Icon(Icons.Default.Check, contentDescription = "Selected") }
            )
        }
    }
}