package com.example.m.ui.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.m.data.database.ArtistGroup
import com.example.m.data.database.ArtistSongGroup
import com.example.m.data.database.Playlist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Playlist") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Playlist name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onCreate(text) },
                enabled = text.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateArtistGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Artist Group") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Group name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onCreate(text) },
                enabled = text.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateArtistSongGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Group") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Group name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onCreate(text) },
                enabled = text.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameArtistGroupDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(initialName) { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Artist Group") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Group name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameArtistSongGroupDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(initialName) { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Group") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Group name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    songTitle: String,
    songArtist: String,
    songThumbnailUrl: String,
    playlists: List<Playlist>,
    onPlaylistSelected: (playlistId: Long) -> Unit,
    onCreateNewPlaylist: () -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 32.dp)) {
        ListItem(
            headlineContent = { Text(songTitle, fontWeight = FontWeight.Bold) },
            supportingContent = { Text(songArtist) },
            leadingContent = {
                AsyncImage(
                    model = songThumbnailUrl,
                    contentDescription = "Song thumbnail",
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    contentScale = ContentScale.Crop
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        HorizontalDivider()

        ListItem(
            headlineContent = { Text("New playlist") },
            leadingContent = {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "New playlist"
                )
            },
            modifier = Modifier.clickable(onClick = onCreateNewPlaylist),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        LazyColumn {
            items(playlists) { playlist ->
                ListItem(
                    headlineContent = { Text(playlist.name) },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = "Playlist"
                        )
                    },
                    modifier = Modifier.clickable { onPlaylistSelected(playlist.playlistId) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
fun AddToArtistSongGroupSheet(
    songTitle: String,
    songArtist: String,
    songThumbnailUrl: String,
    groups: List<ArtistSongGroup>,
    onGroupSelected: (groupId: Long) -> Unit,
    onCreateNewGroup: () -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 32.dp)) {
        ListItem(
            headlineContent = { Text(songTitle, fontWeight = FontWeight.Bold) },
            supportingContent = { Text(songArtist) },
            leadingContent = {
                AsyncImage(
                    model = songThumbnailUrl,
                    contentDescription = "Song thumbnail",
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    contentScale = ContentScale.Crop
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("New group") },
            leadingContent = {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "New group"
                )
            },
            modifier = Modifier.clickable(onClick = onCreateNewGroup),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        if (groups.isEmpty()) {
            ListItem(
                headlineContent = { Text("No groups available for this artist.") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        } else {
            LazyColumn {
                items(groups) { group ->
                    ListItem(
                        headlineContent = { Text(group.name) },
                        leadingContent = {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = "Group"
                            )
                        },
                        modifier = Modifier.clickable { onGroupSelected(group.groupId) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
fun ConfirmDeleteDialog(
    itemType: String,
    itemName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $itemType") },
        text = { Text("Are you sure you want to permanently delete \"$itemName\"?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ConfirmRemoveDialog(
    itemType: String,
    itemName: String,
    containerType: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove $itemType") },
        text = { Text("Are you sure you want to remove \"$itemName\" from this $containerType?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ConfirmAddAllToLibraryDialog(
    itemName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Library") },
        text = { Text("Are you sure you want to add all songs from \"$itemName\" to your library?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MoveToGroupSheet(
    artistName: String,
    groups: List<ArtistGroup>,
    onGroupSelected: (groupId: Long) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 32.dp)) {
        ListItem(
            headlineContent = { Text("Move \"$artistName\" to...", fontWeight = FontWeight.Bold) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        HorizontalDivider()
        LazyColumn {
            items(groups) { group ->
                ListItem(
                    headlineContent = { Text(group.name) },
                    leadingContent = {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = "Group"
                        )
                    },
                    modifier = Modifier.clickable { onGroupSelected(group.groupId) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}