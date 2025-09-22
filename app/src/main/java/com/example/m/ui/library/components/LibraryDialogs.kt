// file: com/example/m/ui/library/components/LibraryDialogs.kt
package com.example.m.ui.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.m.data.database.ArtistGroup
import com.example.m.data.database.ArtistSongGroup
import com.example.m.data.database.LibraryGroup
import com.example.m.data.database.Playlist
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextFieldDialog(
    title: String,
    label: String,
    initialValue: String = "",
    confirmButtonText: String = "Confirm",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    content: @Composable (ColumnScope.() -> Unit)? = null
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                content?.invoke(this)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) {
                Text(confirmButtonText)
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
fun ManageLibraryGroupsDialog(
    groups: List<LibraryGroup>,
    onDismiss: () -> Unit,
    onAddGroup: (String) -> Unit,
    onRenameGroup: (LibraryGroup, String) -> Unit,
    onDeleteGroup: (LibraryGroup) -> Unit
) {
    var newGroupName by remember { mutableStateOf("") }
    var groupToRename by remember { mutableStateOf<LibraryGroup?>(null) }
    var groupToDelete by remember { mutableStateOf<LibraryGroup?>(null) }
    val coroutineScope = rememberCoroutineScope()

    groupToRename?.let { group ->
        TextFieldDialog(
            title = "Rename Group",
            label = "Group name",
            initialValue = group.name,
            confirmButtonText = "Rename",
            onDismiss = { groupToRename = null },
            onConfirm = { newName ->
                onRenameGroup(group, newName)
                groupToRename = null
            }
        )
    }

    groupToDelete?.let { group ->
        ConfirmDeleteDialog(
            itemType = "library group",
            itemName = group.name,
            onDismiss = { groupToDelete = null },
            onConfirm = {
                onDeleteGroup(group)
                groupToDelete = null
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Groups") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(groups, key = { it.groupId }) { group ->
                        ListItem(
                            headlineContent = { Text(group.name) },
                            trailingContent = {
                                Row {
                                    IconButton(
                                        onClick = { groupToRename = group }
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Rename")
                                    }
                                    IconButton(
                                        onClick = { groupToDelete = group }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("New group name") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newGroupName.isNotBlank()) {
                                coroutineScope.launch {
                                    onAddGroup(newGroupName)
                                    newGroupName = ""
                                }
                            }
                        },
                        enabled = newGroupName.isNotBlank()
                    ) {
                        Text("Add")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun ArtistGroupConflictDialog(
    artistName: String,
    conflictingGroupName: String,
    targetGroupName: String,
    onDismiss: () -> Unit,
    onMoveArtistToTargetGroup: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Artist in Another Group") },
        text = {
            Text(
                buildAnnotatedString {
                    append("The artist ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(artistName)
                    }
                    append(" already belongs to the ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Medium)) { // Changed from Bold to Medium
                        append(conflictingGroupName)
                    }
                    append(" group.\n\nTo add this song, you must move the artist and all their songs to the \"")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(targetGroupName)
                    }
                    append("\" group.")
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onMoveArtistToTargetGroup) {
                Text("Move Artist")
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
            headlineContent = { Text(songTitle, fontWeight = FontWeight.Medium) }, // Changed from Bold to Medium
            supportingContent = { Text(songArtist) },
            leadingContent = {
                AsyncImage(
                    model = songThumbnailUrl,
                    contentDescription = "Song thumbnail",
                    modifier = Modifier
                        .size(50.dp)
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
            headlineContent = { Text(songTitle, fontWeight = FontWeight.Medium) }, // Changed from Bold to Medium
            supportingContent = { Text(songArtist) },
            leadingContent = {
                AsyncImage(
                    model = songThumbnailUrl,
                    contentDescription = "Song thumbnail",
                    modifier = Modifier
                        .size(50.dp)
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
fun DeleteGroupWithOptionsDialog(
    groupName: String,
    onDismiss: () -> Unit,
    onConfirmDeleteOnly: () -> Unit,
    onConfirmDeleteAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"$groupName\"") },
        text = {
            Text("This group will be permanently deleted. What should happen to the songs inside it?")
        },
        confirmButton = {
            TextButton(onClick = onConfirmDeleteAll) {
                Text("Delete Group & Songs", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                TextButton(onClick = onConfirmDeleteOnly) {
                    Text("Delete Group Only")
                }
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
fun DisableAutoDownloadConfirmationDialog(
    itemType: String, // "artist" or "playlist"
    onDismiss: () -> Unit,
    onConfirmDisableOnly: () -> Unit,
    onConfirmAndRemove: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disable auto-download?") },
        text = { Text("Do you also want to remove all existing songs downloaded for this $itemType?") },
        confirmButton = {
            TextButton(onClick = onConfirmAndRemove) {
                Text("Remove Downloads")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                TextButton(onClick = onConfirmDisableOnly) {
                    Text("Just Disable")
                }
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
            headlineContent = { Text("Move \"$artistName\" to...", fontWeight = FontWeight.Medium) }, // Changed from Bold to Medium
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        HorizontalDivider()
        if (groups.isEmpty()) {
            ListItem(
                headlineContent = { Text("No other groups available.") },
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
fun SelectLibraryGroupDialog(
    groups: List<LibraryGroup>,
    onDismiss: () -> Unit,
    onGroupSelected: (Long) -> Unit,
    onCreateNewGroup: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select a Group") },
        text = {
            LazyColumn {
                item {
                    ListItem(
                        headlineContent = { Text("Create new group") },
                        leadingContent = { Icon(Icons.Default.Add, contentDescription = "Create new group") },
                        modifier = Modifier.clickable { onCreateNewGroup() },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider()
                }
                items(groups, key = { it.groupId }) { group ->
                    ListItem(
                        headlineContent = { Text(group.name) },
                        modifier = Modifier.clickable { onGroupSelected(group.groupId) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}