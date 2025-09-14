// file: com/example/m/ui/library/LibraryModels.kt
package com.example.m.ui.library

import com.example.m.data.database.ArtistGroup
import com.example.m.data.database.ArtistWithSongs
import com.example.m.data.database.PlaylistWithSongs
import com.example.m.data.database.Song

sealed interface DeletableItem {
    data class DeletableSong(val song: Song) : DeletableItem
    data class DeletablePlaylist(val playlist: PlaylistWithSongs) : DeletableItem
    data class DeletableArtistGroup(val group: ArtistGroup) : DeletableItem
}

enum class SongSortOrder {
    ARTIST,
    TITLE,
    DATE_ADDED,
    PLAY_COUNT
}

enum class DownloadFilter { ALL, DOWNLOADED }
enum class PlaylistFilter { ALL, IN_PLAYLIST }
enum class GroupingFilter { ALL, UNGROUPED }

sealed interface LibraryArtistItem {
    data class ArtistItem(val artistWithSongs: ArtistWithSongs) : LibraryArtistItem
    data class GroupItem(val group: ArtistGroup, val thumbnailUrls: List<String>, val artistCount: Int) : LibraryArtistItem
}