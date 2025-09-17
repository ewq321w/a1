// file: com/example/m/data/database/DAOs.kt
package com.example.m.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface LibraryGroupDao {
    @Query("SELECT * FROM library_groups ORDER BY customOrderPosition ASC, name ASC")
    fun getAllGroups(): Flow<List<LibraryGroup>>

    @Query("SELECT * FROM library_groups WHERE groupId = :groupId LIMIT 1")
    suspend fun getGroup(groupId: Long): LibraryGroup?

    @Query("SELECT * FROM library_groups WHERE name = :name LIMIT 1")
    suspend fun getGroup(name: String): LibraryGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: LibraryGroup): Long

    @Update
    suspend fun updateGroup(group: LibraryGroup)

    @Delete
    suspend fun deleteGroup(group: LibraryGroup)

    @Query("DELETE FROM library_groups WHERE groupId = :groupId")
    suspend fun deleteGroupById(groupId: Long)
}

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSong(song: Song): Long

    @Update
    suspend fun updateSong(song: Song)

    @Query("UPDATE songs SET playCount = playCount + 1 WHERE songId = :songId")
    suspend fun incrementPlayCount(songId: Long)

    @Query("UPDATE songs SET downloadStatus = :status, downloadProgress = :progress WHERE songId = :songId")
    suspend fun updateDownloadInfo(songId: Long, status: DownloadStatus, progress: Int)

    @Query("UPDATE songs SET downloadStatus = :status WHERE songId = :songId")
    suspend fun updateDownloadStatus(songId: Long, status: DownloadStatus)

    @Query("SELECT * FROM songs WHERE songId = :id")
    suspend fun getSongById(id: Long): Song?

    @Query("SELECT * FROM songs WHERE youtubeUrl = :youtubeUrl")
    suspend fun getSongByUrl(youtubeUrl: String): Song?

    @Query("SELECT * FROM songs WHERE localFilePath = :filePath LIMIT 1")
    suspend fun getSongByFilePath(filePath: String): Song?

    @Query("SELECT * FROM songs ORDER BY artist, title ASC")
    fun getAllSongs(): Flow<List<Song>>

    // --- Queries for "All Music" ---
    @Query("""
        SELECT s.* FROM songs AS s
        INNER JOIN artist_song_cross_ref AS ascr ON s.songId = ascr.songId
        WHERE s.isInLibrary = 1
        ORDER BY s.artist ASC, ascr.customOrderPosition ASC
    """)
    fun getLibrarySongsSortedByArtist(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE isInLibrary = 1 ORDER BY title ASC")
    fun getLibrarySongsSortedByTitle(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE isInLibrary = 1 ORDER BY dateAddedTimestamp ASC")
    fun getLibrarySongsSortedByDateAdded(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE isInLibrary = 1 ORDER BY playCount DESC, title ASC")
    fun getLibrarySongsSortedByPlayCount(): Flow<List<Song>>

    // --- Queries to filter songs by libraryGroupId ---
    @Query("""
        SELECT s.* FROM songs AS s
        INNER JOIN artist_song_cross_ref AS ascr ON s.songId = ascr.songId
        WHERE s.isInLibrary = 1 AND s.libraryGroupId = :groupId
        ORDER BY s.artist ASC, ascr.customOrderPosition ASC
    """)
    fun getLibrarySongsSortedByArtist(groupId: Long): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE isInLibrary = 1 AND libraryGroupId = :groupId ORDER BY title ASC")
    fun getLibrarySongsSortedByTitle(groupId: Long): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE isInLibrary = 1 AND libraryGroupId = :groupId ORDER BY dateAddedTimestamp ASC")
    fun getLibrarySongsSortedByDateAdded(groupId: Long): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE isInLibrary = 1 AND libraryGroupId = :groupId ORDER BY playCount DESC, title ASC")
    fun getLibrarySongsSortedByPlayCount(groupId: Long): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE artist = :artistName AND isInLibrary = 1 LIMIT 1")
    suspend fun getArtistLibrarySong(artistName: String): Song?

    @Query("UPDATE songs SET libraryGroupId = :groupId WHERE artist = :artistName AND isInLibrary = 1")
    suspend fun moveArtistToLibraryGroup(artistName: String, groupId: Long)

    @Query("SELECT songId FROM songs WHERE libraryGroupId = :groupId")
    suspend fun getSongIdsInLibraryGroup(groupId: Long): List<Long>


    @Query("""
        SELECT s.* FROM songs s
        LEFT JOIN artist_song_cross_ref ascr ON s.songId = ascr.songId
        WHERE ascr.songId IS NULL
    """)
    suspend fun getUnlinkedSongs(): List<Song>

    @Query("SELECT * FROM songs WHERE localFilePath IS NOT NULL ORDER BY artist, title ASC")
    fun getDownloadedSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE songId IN (:songIds)")
    suspend fun getSongsByIds(songIds: List<Long>): List<Song>

    @Query("SELECT * FROM songs WHERE songId IN (SELECT DISTINCT songId FROM playlist_songs) ORDER BY artist, title ASC")
    fun getSongsInPlaylists(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE youtubeUrl IN (:youtubeUrls)")
    suspend fun getSongsByUrls(youtubeUrls: List<String>): List<Song>

    @Query("SELECT * FROM songs WHERE downloadStatus = 'DOWNLOADED'")
    suspend fun getAllDownloadedSongsOnce(): List<Song>

    @Query("SELECT localFilePath FROM songs WHERE localFilePath IS NOT NULL")
    suspend fun getAllLocalFilePaths(): List<String>

    @Query("SELECT DISTINCT artist FROM songs ORDER BY artist ASC")
    fun getAllArtists(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE artist = :artistName ORDER BY title ASC")
    fun getAllSongsByArtist(artistName: String): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE artist = :artistName ORDER BY dateAddedTimestamp ASC")
    fun getSongsByArtistSortedByDateAdded(artistName: String): Flow<List<Song>>

    @Delete
    suspend fun deleteSong(song: Song)

    @Transaction
    suspend fun upsertDownloadedSong(song: Song) {
        val existingSong = getSongByUrl(song.youtubeUrl)
        if (existingSong != null) {
            updateSong(
                existingSong.copy(
                    localFilePath = song.localFilePath,
                    downloadStatus = DownloadStatus.DOWNLOADED,
                    downloadProgress = 100
                )
            )
        } else {
            insertSong(
                song.copy(
                    downloadStatus = DownloadStatus.DOWNLOADED,
                    downloadProgress = 100
                )
            )
        }
    }

    @Transaction
    suspend fun upsertSong(song: Song): Song {
        val newId = insertSong(song)
        return if (newId == -1L) {
            getSongByUrl(song.youtubeUrl)
                ?: throw IllegalStateException("Failed to retrieve existing song with URL: ${song.youtubeUrl}")
        } else {
            song.copy(songId = newId)
        }
    }

    @Query("""
        DELETE FROM songs
        WHERE isInLibrary = 0
        AND songId NOT IN (SELECT DISTINCT songId FROM playlist_songs)
        AND songId NOT IN (SELECT DISTINCT songId FROM listening_history)
    """)
    suspend fun deleteOrphanSongs(): Int
}

data class PlaylistWithSongs(
    @Embedded val playlist: Playlist,
    @Relation(
        parentColumn = "playlistId",
        entityColumn = "songId",
        associateBy = Junction(PlaylistSongCrossRef::class)
    )
    val songs: List<Song>
)

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): Playlist?

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongIntoPlaylist(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deleteSongFromPlaylist(playlistId: Long, songId: Long)

    @Transaction
    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    fun getPlaylistWithSongsById(playlistId: Long): Flow<PlaylistWithSongs?> {
        return getPlaylistWithSongsSortedByCustom(playlistId)
            .map { playlistSongsMap ->
                playlistSongsMap.entries.firstOrNull()?.let { entry ->
                    PlaylistWithSongs(
                        playlist = entry.key,
                        songs = entry.value.filter { it.songId != 0L }
                    )
                }
            }
    }

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT P.*, S.*, PSC.customOrderPosition FROM playlists AS P LEFT JOIN playlist_songs AS PSC ON P.playlistId = PSC.playlistId LEFT JOIN songs AS S ON PSC.songId = S.songId ORDER BY P.playlistId, PSC.customOrderPosition ASC")
    fun getPlaylistsWithSongsAndOrderedSongsInternal(): Flow<Map<Playlist, List<Song>>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT P.*, S.*, PSC.customOrderPosition FROM playlists AS P LEFT JOIN playlist_songs AS PSC ON P.playlistId = PSC.playlistId LEFT JOIN songs AS S ON PSC.songId = S.songId WHERE P.playlistId = :playlistId ORDER BY PSC.customOrderPosition ASC")
    fun getPlaylistWithSongsSortedByCustom(playlistId: Long): Flow<Map<Playlist, List<Song>>>

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE libraryGroupId = :groupId ORDER BY name ASC")
    fun getPlaylistsByGroupId(groupId: Long): Flow<List<Playlist>>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY customOrderPosition ASC")
    suspend fun getPlaylistSongCrossRefs(playlistId: Long): List<PlaylistSongCrossRef>

    @Query("UPDATE playlist_songs SET customOrderPosition = :position WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun updateSongPositionInPlaylist(playlistId: Long, songId: Long, position: Int)

    @Transaction
    suspend fun updateSongOrder(playlistId: Long, songs: List<Song>) {
        songs.forEachIndexed { index, song ->
            updateSongPositionInPlaylist(playlistId, song.songId, index)
        }
    }

    @Query("SELECT IFNULL(MAX(customOrderPosition), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getMaxPlaylistSongPosition(playlistId: Long): Int

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE songId = :songId")
    suspend fun getPlaylistCountForSong(songId: Long): Int

    @Query("""
        SELECT P.* FROM playlists AS P
        INNER JOIN playlist_songs AS PS ON P.playlistId = PS.playlistId
        WHERE PS.songId = :songId AND P.downloadAutomatically = 1
        """)
    suspend fun getAutoDownloadPlaylistsForSong(songId: Long): List<Playlist>
}

data class RecentPlay(
    val songId: Long,
    val recentPlayCount: Int
)

data class HistoryEntry(
    @Embedded val song: Song,
    val logId: Long
)

@Dao
interface ListeningHistoryDao {
    @Insert
    suspend fun insertPlayLog(log: ListeningHistory)

    @Query("DELETE FROM listening_history WHERE timestamp < :timestamp")
    suspend fun pruneOldHistory(timestamp: Long)

    @Query("SELECT songId, COUNT(songId) as recentPlayCount FROM listening_history GROUP BY songId ORDER BY recentPlayCount DESC LIMIT :limit")
    fun getTopRecentSongs(limit: Int): Flow<List<RecentPlay>>

    @Query("SELECT s.*, h.logId FROM songs s INNER JOIN listening_history h ON s.songId = h.songId ORDER BY h.timestamp DESC")
    fun getListeningHistory(): Flow<List<HistoryEntry>>

    @Query("DELETE FROM listening_history WHERE logId = :logId")
    suspend fun deleteHistoryEntry(logId: Long)

    @Query("SELECT COUNT(*) FROM listening_history WHERE songId = :songId")
    suspend fun getHistoryCountForSong(songId: Long): Int

    @Query("DELETE FROM listening_history")
    suspend fun clearAllHistory()

    @Query("""
        DELETE FROM listening_history
        WHERE logId NOT IN (
            SELECT logId FROM listening_history
            ORDER BY timestamp DESC
            LIMIT :keepCount
        )
    """)
    suspend fun clearHistoryExceptLast(keepCount: Int)
}

@Dao
interface DownloadQueueDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItem(item: DownloadQueueItem)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItems(items: List<DownloadQueueItem>)

    @Query("SELECT * FROM download_queue LIMIT 1")
    suspend fun getNextItem(): DownloadQueueItem?

    @Query("DELETE FROM download_queue WHERE songId = :songId")
    suspend fun deleteItem(songId: Long)

    @Query("SELECT songId FROM download_queue WHERE songId IN (:songIds)")
    suspend fun getQueuedSongIds(songIds: List<Long>): List<Long>
}

@Dao
interface PlaybackStateDao {
    @Upsert
    suspend fun saveState(state: PlaybackStateEntity)

    @Query("SELECT * FROM playback_state WHERE id = 1")
    suspend fun getState(): PlaybackStateEntity?

    @Query("DELETE FROM playback_state WHERE id = 1")
    suspend fun clearState()
}

data class ArtistIdAndSong(
    val artistId: Long,
    @Embedded val song: Song
)

data class ArtistIdAndThumbnail(
    val artistId: Long,
    val thumbnailUrl: String
)

@Dao
interface ArtistDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArtist(artist: Artist): Long

    @Update
    suspend fun updateArtist(artist: Artist)

    @Query("SELECT * FROM artists WHERE artistId = :artistId")
    fun getArtistById(artistId: Long): Flow<Artist?>

    @Query("SELECT * FROM artists WHERE name = :name LIMIT 1")
    suspend fun getArtistByName(name: String): Artist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtistSongCrossRef(crossRef: ArtistSongCrossRef)

    @Transaction
    @Query("SELECT * FROM artists WHERE artistId = :artistId")
    fun getArtistWithSongs(artistId: Long): Flow<ArtistWithSongs?>

    fun getArtistWithLibrarySongs(artistId: Long): Flow<ArtistWithSongs?> {
        return getArtistWithSongsMapSortedByCustom(artistId).map { map ->
            map.entries.firstOrNull()?.let { (artist, songs) ->
                ArtistWithSongs(artist, songs.filter { it.songId != 0L })
            }
        }
    }

    @Query("""
        SELECT A.*, S.* FROM artists AS A
        LEFT JOIN artist_song_cross_ref AS A_S ON A.artistId = A_S.artistId
        LEFT JOIN songs AS S ON A_S.songId = S.songId
        WHERE A.artistId = :artistId AND S.isInLibrary = 1
        ORDER BY A_S.customOrderPosition ASC
    """)
    fun getArtistWithSongsMapSortedByCustom(artistId: Long): Flow<Map<Artist, List<Song>>>

    @Query("UPDATE artist_song_cross_ref SET customOrderPosition = :position WHERE artistId = :artistId AND songId = :songId")
    suspend fun updateArtistSongPosition(artistId: Long, songId: Long, position: Int)

    @Transaction
    suspend fun updateSongOrder(artistId: Long, songs: List<Song>) {
        songs.forEachIndexed { index, song ->
            updateArtistSongPosition(artistId, song.songId, index)
        }
    }

    @Transaction
    @Query("""
        SELECT * FROM artists
        WHERE isHidden = 0 AND parentGroupId IS NULL
        AND artistId IN (
            SELECT DISTINCT ascr.artistId
            FROM artist_song_cross_ref AS ascr
            INNER JOIN songs AS s ON ascr.songId = s.songId
            WHERE s.isInLibrary = 1
        )
        ORDER BY customOrderPosition ASC
    """)
    fun getAllArtistsSortedByCustom(): Flow<List<ArtistWithSongs>>

    @Transaction
    @Query("""
        SELECT * FROM artists
        WHERE isHidden = 0 AND parentGroupId IS NULL
        AND artistId IN (
            SELECT DISTINCT ascr.artistId
            FROM artist_song_cross_ref AS ascr
            INNER JOIN songs AS s ON ascr.songId = s.songId
            WHERE s.isInLibrary = 1 AND s.libraryGroupId = :groupId
        )
        ORDER BY customOrderPosition ASC
    """)
    fun getAllArtistsSortedByCustom(groupId: Long): Flow<List<ArtistWithSongs>>

    @Query("UPDATE artists SET customOrderPosition = :position WHERE artistId = :artistId")
    suspend fun updateArtistPosition(artistId: Long, position: Long)

    @Query("UPDATE artists SET isHidden = 1 WHERE artistId = :artistId")
    suspend fun hideArtist(artistId: Long)

    @Query("UPDATE artists SET isHidden = 0 WHERE artistId = :artistId")
    suspend fun unhideArtist(artistId: Long)

    @Query("SELECT * FROM artists WHERE isHidden = 1 ORDER BY name ASC")
    fun getHiddenArtists(): Flow<List<Artist>>

    @Query("UPDATE artists SET parentGroupId = :groupId WHERE artistId = :artistId")
    suspend fun moveArtistToGroup(artistId: Long, groupId: Long)

    @Query("UPDATE artists SET parentGroupId = NULL WHERE artistId = :artistId")
    suspend fun removeArtistFromGroup(artistId: Long)

    @Query("SELECT T.thumbnailUrl FROM artist_song_cross_ref AS A_S JOIN songs AS T ON A_S.songId = T.songId WHERE A_S.artistId = :artistId ORDER BY A_S.customOrderPosition ASC LIMIT 4")
    suspend fun getThumbnailsForArtist(artistId: Long): List<String>

    @Query("""
        SELECT ascr.artistId, s.thumbnailUrl FROM songs s
        INNER JOIN artist_song_cross_ref ascr ON s.songId = ascr.songId
        WHERE ascr.artistId IN (:artistIds) AND s.isInLibrary = 1
        GROUP BY ascr.artistId
    """)
    suspend fun getRepresentativeThumbnailsForArtists(artistIds: List<Long>): List<ArtistIdAndThumbnail>

    @Query("""
        SELECT ascr.artistId, s.* FROM songs s 
        INNER JOIN artist_song_cross_ref ascr ON s.songId = ascr.songId 
        WHERE ascr.artistId IN (:artistIds)
    """)
    suspend fun getSongsGroupedByArtistId(artistIds: List<Long>): List<ArtistIdAndSong>

    @Query("""
        SELECT S.* FROM songs AS S
        INNER JOIN artist_song_cross_ref AS A_S ON S.songId = A_S.songId
        WHERE A_S.artistId = :artistId AND S.isInLibrary = 1
        ORDER BY A_S.customOrderPosition ASC
    """)
    suspend fun getSongsForArtistSortedByCustom(artistId: Long): List<Song>

    @Delete
    suspend fun deleteArtist(artist: Artist)

    @Query("SELECT COUNT(songId) FROM artist_song_cross_ref WHERE artistId = :artistId")
    suspend fun getArtistSongCount(artistId: Long): Int

    @Query("DELETE FROM artist_song_cross_ref WHERE songId NOT IN (SELECT songId FROM songs)")
    suspend fun deleteOrphanedArtistSongCrossRefs(): Int

    @Query("DELETE FROM artists WHERE artistId NOT IN (SELECT DISTINCT artistId FROM artist_song_cross_ref)")
    suspend fun deleteOrphanedArtistsInternal(): Int

    @Transaction
    suspend fun deleteOrphanedArtists(): Int {
        deleteOrphanedArtistSongCrossRefs()
        return deleteOrphanedArtistsInternal()
    }

    @Query("SELECT IFNULL(MAX(customOrderPosition), -1) FROM artist_song_cross_ref WHERE artistId = :artistId")
    suspend fun getMaxArtistSongPosition(artistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtistSongGroup(group: ArtistSongGroup): Long

    @Update
    suspend fun updateArtistSongGroup(group: ArtistSongGroup)

    @Query("DELETE FROM artist_song_groups WHERE groupId = :groupId")
    suspend fun deleteArtistSongGroup(groupId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongIntoArtistSongGroup(crossRef: ArtistSongGroupSongCrossRef)

    @Query("DELETE FROM artist_song_group_songs WHERE groupId = :groupId AND songId = :songId")
    suspend fun deleteSongFromArtistSongGroup(groupId: Long, songId: Long)

    @Query("SELECT IFNULL(MAX(customOrderPosition), -1) FROM artist_song_group_songs WHERE groupId = :groupId")
    suspend fun getMaxSongPositionInGroup(groupId: Long): Int

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("""
        SELECT G.*, S.* FROM artist_song_groups AS G
        LEFT JOIN artist_song_group_songs AS GS_CR ON G.groupId = GS_CR.groupId
        LEFT JOIN songs AS S ON GS_CR.songId = S.songId
        WHERE G.artistId = :artistId
        ORDER BY G.customOrderPosition ASC, GS_CR.customOrderPosition ASC
    """)
    fun getAllArtistSongGroupsWithSongsOrdered(artistId: Long): Flow<Map<ArtistSongGroup, List<Song>>>


    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("""
        SELECT G.*, S.* FROM artist_song_groups AS G
        LEFT JOIN artist_song_group_songs AS GS_CR ON G.groupId = GS_CR.groupId
        LEFT JOIN songs AS S ON GS_CR.songId = S.songId
        WHERE G.groupId = :groupId
        ORDER BY GS_CR.customOrderPosition ASC
    """)
    fun getArtistSongGroupWithSongsOrdered(groupId: Long): Flow<Map<ArtistSongGroup, List<Song>>>


    @Query("UPDATE artist_song_group_songs SET customOrderPosition = :position WHERE groupId = :groupId AND songId = :songId")
    suspend fun updateSongPositionInGroup(groupId: Long, songId: Long, position: Int)

    @Transaction
    suspend fun updateGroupSongOrder(groupId: Long, songs: List<Song>) {
        songs.forEachIndexed { index, song ->
            updateSongPositionInGroup(groupId, song.songId, index)
        }
    }

    @Query("SELECT DISTINCT songId FROM artist_song_group_songs")
    fun getAllSongIdsInGroups(): Flow<List<Long>>

    @Query("SELECT s.* FROM songs s INNER JOIN artist_song_group_songs ags ON s.songId = ags.songId WHERE ags.groupId = :groupId")
    suspend fun getSongsInArtistSongGroup(groupId: Long): List<Song>
}

@Dao
interface ArtistGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: ArtistGroup): Long

    @Update
    suspend fun updateGroup(group: ArtistGroup)

    @Transaction
    @Query("SELECT * FROM artist_groups ORDER BY customOrderPosition ASC")
    fun getGroupsWithArtists(): Flow<List<ArtistGroupWithArtists>>

    @Transaction
    @Query("SELECT * FROM artist_groups WHERE groupId = :groupId")
    fun getGroupWithArtists(groupId: Long): Flow<ArtistGroupWithArtists?>

    @Transaction
    @Query("SELECT * FROM artist_groups WHERE groupId = :groupId")
    suspend fun getGroupWithArtistsOnce(groupId: Long): ArtistGroupWithArtists?

    @Query("SELECT * FROM artist_groups ORDER BY name ASC")
    fun getAllGroups(): Flow<List<ArtistGroup>>

    @Query("UPDATE artists SET parentGroupId = NULL WHERE parentGroupId = :groupId")
    suspend fun ungroupArtists(groupId: Long)

    @Query("DELETE FROM artist_groups WHERE groupId = :groupId")
    suspend fun deleteGroupById(groupId: Long)

    @Transaction
    suspend fun deleteGroupAndUngroupArtists(groupId: Long) {
        ungroupArtists(groupId)
        deleteGroupById(groupId)
    }
}