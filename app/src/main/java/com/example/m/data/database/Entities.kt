package com.example.m.data.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

enum class SourceType {
    STANDARD,
    LOCAL_ONLY
}

enum class DownloadStatus {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

@Entity(tableName = "library_groups")
data class LibraryGroup(
    @PrimaryKey(autoGenerate = true)
    val groupId: Long = 0,
    val name: String,
    val customOrderPosition: Int = 0
)

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["youtubeUrl"], unique = true),
        Index(value = ["libraryGroupId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = LibraryGroup::class,
            parentColumns = ["groupId"],
            childColumns = ["libraryGroupId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Song(
    @PrimaryKey(autoGenerate = true)
    val songId: Long = 0,
    val videoId: String?,
    val youtubeUrl: String,
    val title: String,
    val artist: String,
    val duration: Long,
    val thumbnailUrl: String,
    var localFilePath: String? = null,
    val sourceType: SourceType = SourceType.STANDARD,
    val dateAddedTimestamp: Long = System.currentTimeMillis(),
    val isInLibrary: Boolean = false,
    val playCount: Int = 0,
    val libraryGroupId: Long? = null,
    val downloadStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    val downloadProgress: Int = 0
)

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["libraryGroupId"])],
    foreignKeys = [
        ForeignKey(
            entity = LibraryGroup::class,
            parentColumns = ["groupId"],
            childColumns = ["libraryGroupId"],
            onDelete = ForeignKey.CASCADE // If a group is deleted, its playlists are deleted too.
        )
    ]
)
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val playlistId: Long = 0,
    val name: String,
    val downloadAutomatically: Boolean = false,
    val defaultSortOrder: String = "customOrderPosition",
    val libraryGroupId: Long // Each playlist now must belong to a group
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Song::class,
            parentColumns = ["songId"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["songId"])]
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val dateAddedTimestamp: Long,
    val customOrderPosition: Int
)

@Entity(
    tableName = "listening_history",
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["songId"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["songId"])]
)
data class ListeningHistory(
    @PrimaryKey(autoGenerate = true)
    val logId: Long = 0,
    val songId: Long,
    val timestamp: Long
)

fun Song.toStreamInfoItem(): StreamInfoItem {
    val item = StreamInfoItem(
        0,
        this.youtubeUrl,
        this.title,
        StreamType.AUDIO_STREAM
    ).apply {
        duration = this@toStreamInfoItem.duration
        uploaderName = this@toStreamInfoItem.artist
    }
    item.thumbnails = listOf(Image(this.thumbnailUrl, 0, 0, Image.ResolutionLevel.HIGH))
    return item
}

@Entity(
    tableName = "download_queue",
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["songId"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DownloadQueueItem(
    @PrimaryKey
    val songId: Long
)

@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey
    val id: Int = 1, // Use a constant ID to ensure only one row
    val queue: List<String>,
    val currentItemIndex: Int,
    val currentPosition: Long,
    val isPlaying: Boolean,
    val accumulatedListeningTime: Long = 0L,
    val playCountIncrements: Int = 0
)

@Entity(tableName = "artists", indices = [Index(value = ["name"], unique = true)])
data class Artist(
    @PrimaryKey(autoGenerate = true)
    val artistId: Long = 0,
    val name: String,
    val customOrderPosition: Long = 0,
    val downloadAutomatically: Boolean = false,
    val isHidden: Boolean = false,
    val parentGroupId: Long? = null
)

@Entity(
    tableName = "artist_song_cross_ref",
    primaryKeys = ["artistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = Artist::class,
            parentColumns = ["artistId"],
            childColumns = ["artistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Song::class,
            parentColumns = ["songId"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["songId"])]
)
data class ArtistSongCrossRef(
    val artistId: Long,
    val songId: Long,
    val customOrderPosition: Int
)

data class ArtistWithSongs(
    @Embedded val artist: Artist,
    @Relation(
        parentColumn = "artistId",
        entityColumn = "songId",
        associateBy = Junction(ArtistSongCrossRef::class)
    )
    val songs: List<Song>
)

@Entity(tableName = "artist_groups")
data class ArtistGroup(
    @PrimaryKey(autoGenerate = true)
    val groupId: Long = 0,
    val name: String,
    val customOrderPosition: Long = 0
)

data class ArtistGroupWithArtists(
    @Embedded val group: ArtistGroup,
    @Relation(
        parentColumn = "groupId",
        entityColumn = "parentGroupId"
    )
    val artists: List<Artist>
)

@Entity(
    tableName = "artist_song_groups",
    foreignKeys = [
        ForeignKey(
            entity = Artist::class,
            parentColumns = ["artistId"],
            childColumns = ["artistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["artistId"])]
)
data class ArtistSongGroup(
    @PrimaryKey(autoGenerate = true)
    val groupId: Long = 0,
    val artistId: Long,
    val name: String,
    val customOrderPosition: Int = 0
)

@Entity(
    tableName = "artist_song_group_songs",
    primaryKeys = ["groupId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = ArtistSongGroup::class,
            parentColumns = ["groupId"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Song::class,
            parentColumns = ["songId"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["songId"])]
)
data class ArtistSongGroupSongCrossRef(
    val groupId: Long,
    val songId: Long,
    val customOrderPosition: Int
)

data class ArtistSongGroupWithSongs(
    @Embedded val group: ArtistSongGroup,
    @Relation(
        parentColumn = "groupId",
        entityColumn = "songId",
        associateBy = Junction(
            value = ArtistSongGroupSongCrossRef::class,
            parentColumn = "groupId",
            entityColumn = "songId"
        )
    )
    val songs: List<Song>
)

@Entity(
    tableName = "lyrics_cache",
    indices = [Index(value = ["artist", "title"], unique = true)]
)
data class LyricsCache(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val artist: String,
    val title: String,
    val lyrics: String?,
    val source: String,
    val isSuccessful: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "search_history",
    indices = [Index(value = ["query"], unique = true)]
)
data class SearchHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val query: String,
    val timestamp: Long = System.currentTimeMillis(),
    val searchCount: Int = 1
)

