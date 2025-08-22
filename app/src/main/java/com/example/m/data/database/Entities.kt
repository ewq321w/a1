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

@Entity(
    tableName = "songs",
    indices = [Index(value = ["youtubeUrl"], unique = true)]
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
    val isInLibrary: Boolean = false
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val playlistId: Long = 0,
    val name: String,
    val downloadAutomatically: Boolean = false,
    val defaultSortOrder: String = "customOrderPosition"
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
    val isPlaying: Boolean
)

@Entity(tableName = "artists", indices = [Index(value = ["name"], unique = true)])
data class Artist(
    @PrimaryKey(autoGenerate = true)
    val artistId: Long = 0,
    val name: String,
    val customOrderPosition: Long = 0,
    val downloadAutomatically: Boolean = false,
    // --- NEW FIELDS FOR HIDING AND GROUPING ---
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

// +++ ADD THESE NEW CLASSES FOR ARTIST GROUPS +++
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