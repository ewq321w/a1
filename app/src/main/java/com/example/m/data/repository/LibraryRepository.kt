// file: com/example/m/data/repository/LibraryRepository.kt
package com.example.m.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.room.Transaction
import com.example.m.data.database.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ArtistGroupConflict(
    val conflictingGroupId: Long,
    val conflictingGroupName: String
)

sealed class AutoDownloadConflict {
    data class Artist(val name: String) : AutoDownloadConflict()
    data class Playlist(val name: String) : AutoDownloadConflict()
}

@Singleton
class LibraryRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao,
    private val downloadQueueDao: DownloadQueueDao,
    private val artistDao: ArtistDao,
    private val artistGroupDao: ArtistGroupDao,
    private val libraryGroupDao: LibraryGroupDao,
    @ApplicationContext private val context: Context
) {
    fun getPlaylistsWithSongs(): Flow<List<PlaylistWithSongs>> {
        return playlistDao.getPlaylistsWithSongsAndOrderedSongsInternal().map { map ->
            map.entries.map { PlaylistWithSongs(it.key, it.value) }
        }
    }

    fun getPlaylistsWithSongs(groupId: Long): Flow<List<PlaylistWithSongs>> {
        return playlistDao.getPlaylistsWithSongsAndOrderedSongsInternal().map { map ->
            map.entries
                .map { PlaylistWithSongs(it.key, it.value) }
                .filter { it.playlist.libraryGroupId == groupId }
        }
    }

    suspend fun checkArtistGroupConflict(artistName: String, targetGroupId: Long): ArtistGroupConflict? {
        if (targetGroupId == 0L) return null // "All Music" never conflicts

        val existingSong = songDao.getArtistLibrarySong(artistName) ?: return null
        val existingGroupId = existingSong.libraryGroupId

        return if (existingGroupId != null && existingGroupId != targetGroupId) {
            val conflictingGroup = libraryGroupDao.getGroup(existingGroupId)
            conflictingGroup?.let {
                ArtistGroupConflict(it.groupId, it.name)
            }
        } else {
            null
        }
    }

    suspend fun moveArtistToLibraryGroup(artistName: String, groupId: Long) {
        songDao.moveArtistToLibraryGroup(artistName, groupId)
    }

    @Transaction
    suspend fun deleteLibraryGroupAndContents(groupId: Long) {
        // Step 1: Identify and delete playlists that contain only songs from the deleted group.
        val allPlaylists = playlistDao.getPlaylistsWithSongsAndOrderedSongsInternal().first().map { PlaylistWithSongs(it.key, it.value) }
        val songIdsInGroup = songDao.getSongIdsInLibraryGroup(groupId).toSet()

        if (songIdsInGroup.isNotEmpty()) {
            val playlistsToDelete = allPlaylists.filter { playlist ->
                val playlistSongIds = playlist.songs.map { it.songId }.toSet()
                // A playlist is deleted if it's not empty and all of its songs belong to the group being deleted.
                playlistSongIds.isNotEmpty() && songIdsInGroup.containsAll(playlistSongIds)
            }

            for (playlist in playlistsToDelete) {
                playlistDao.deletePlaylistById(playlist.playlist.playlistId)
            }
        }

        // Step 2: Delete the library group. The CASCADE rule will automatically delete all its songs.
        libraryGroupDao.deleteGroupById(groupId)

        // Step 3: Clean up any artists that are now orphaned (have no songs).
        artistDao.deleteOrphanedArtists()
    }

    fun getAllPlaylists(): Flow<List<Playlist>> = playlistDao.getAllPlaylists()

    fun getPlaylistsByGroupId(groupId: Long): Flow<List<Playlist>> = playlistDao.getPlaylistsByGroupId(groupId)

    fun getDownloadedSongs(): Flow<List<Song>> = songDao.getDownloadedSongs()
    fun getAllArtists(): Flow<List<String>> = songDao.getAllArtists()
    fun getPlaylistWithSongsById(playlistId: Long): Flow<PlaylistWithSongs?> = playlistDao.getPlaylistWithSongsById(playlistId)
    fun getAllSongsByArtist(artistName: String): Flow<List<Song>> = songDao.getAllSongsByArtist(artistName)
    suspend fun getSongsByIds(songIds: List<Long>): List<Song> = songDao.getSongsByIds(songIds)
    suspend fun deletePlaylist(playlistId: Long) = playlistDao.deletePlaylistById(playlistId)
    suspend fun deleteSong(song: Song) = songDao.deleteSong(song)
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = playlistDao.deleteSongFromPlaylist(playlistId, songId)
    fun getSongsInPlaylists(): Flow<List<Song>> = songDao.getSongsInPlaylists()

    suspend fun deleteSongFromDeviceAndDb(song: Song) {
        val artist = artistDao.getArtistByName(song.artist)

        song.localFilePath?.let { path ->
            try {
                val uri = path.toUri()
                if (uri.scheme == "content") {
                    context.contentResolver.delete(uri, null, null)
                } else {
                    File(path).delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        songDao.deleteSong(song)

        artist?.let {
            val remainingSongs = artistDao.getArtistSongCount(it.artistId)
            if (remainingSongs == 0) {
                artistDao.deleteArtist(it)
            }
        }
    }

    suspend fun deleteDownloadedFileForSong(song: Song) {
        song.localFilePath?.let { path ->
            try {
                val uri = path.toUri()
                val deletedCount = if (uri.scheme == "content") {
                    context.contentResolver.delete(uri, null, null)
                } else {
                    if (File(path).delete()) 1 else 0
                }
                if (deletedCount > 0) {
                    songDao.updateSong(song.copy(localFilePath = null))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // If deletion fails (e.g. SecurityException), still unlink from DB
                // to fix the app's state for the user.
                songDao.updateSong(song.copy(localFilePath = null))
            }
        }
    }

    suspend fun checkForAutoDownloadConflict(song: Song): AutoDownloadConflict? {
        val artist = artistDao.getArtistByName(song.artist)
        if (artist?.downloadAutomatically == true) {
            return AutoDownloadConflict.Artist(artist.name)
        }

        val conflictingPlaylists = playlistDao.getAutoDownloadPlaylistsForSong(song.songId)
        conflictingPlaylists.firstOrNull()?.let {
            return AutoDownloadConflict.Playlist(it.name)
        }

        return null
    }

    suspend fun verifyLibraryEntries(): Int {
        val songsToReQueue = mutableListOf<DownloadQueueItem>()
        val downloadedSongs = songDao.getAllDownloadedSongsOnce()

        for (song in downloadedSongs) {
            val path = song.localFilePath
            if (path.isNullOrBlank() || !isUriValid(path.toUri())) {
                songsToReQueue.add(DownloadQueueItem(song.songId))
            }
        }

        if (songsToReQueue.isNotEmpty()) {
            downloadQueueDao.addItems(songsToReQueue)
        }
        return songsToReQueue.size
    }

    suspend fun cleanOrphanedFiles(): Int {
        val databasePaths = songDao.getAllLocalFilePaths().toSet()
        val orphanedUris = mutableListOf<Uri>()

        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA)
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%${Environment.DIRECTORY_MUSIC}/MyMusicApp/%")

        val queryUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                cursor.getString(dataColumn)
                val contentUri = Uri.withAppendedPath(queryUri, id.toString())

                if (!databasePaths.contains(contentUri.toString())) {
                    orphanedUris.add(contentUri)
                }
            }
        }

        for (uri in orphanedUris) {
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return orphanedUris.size
    }

    suspend fun fixMissingArtistLinks(): Int {
        val unlinkedSongs = songDao.getUnlinkedSongs()
        if (unlinkedSongs.isNotEmpty()) {
            for (song in unlinkedSongs) {
                linkSongToArtist(song)
            }
        }
        return unlinkedSongs.size
    }

    suspend fun linkSongToArtist(song: Song) {
        if (song.artist.isBlank()) return

        var artist = artistDao.getArtistByName(song.artist)
        if (artist == null) {
            artistDao.insertArtist(Artist(name = song.artist))
            artist = artistDao.getArtistByName(song.artist)
                ?: throw IllegalStateException("Failed to create and retrieve artist: ${song.artist}")
        }

        val maxPosition = artistDao.getMaxArtistSongPosition(artist.artistId)
        val newPosition = maxPosition + 1

        artistDao.upsertArtistSongCrossRef(
            ArtistSongCrossRef(
                artistId = artist.artistId,
                songId = song.songId,
                customOrderPosition = newPosition
            )
        )
    }

    private fun isUriValid(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.close() }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun cleanOrphanedArtists(): Int = artistDao.deleteOrphanedArtists()

    suspend fun deleteArtistGroup(groupId: Long) {
        artistGroupDao.deleteGroupAndUngroupArtists(groupId)
    }

    suspend fun cleanOrphanedSongs(): Int = songDao.deleteOrphanSongs()

    suspend fun createArtistSongGroup(artistId: Long, name: String) {
        artistDao.insertArtistSongGroup(ArtistSongGroup(artistId = artistId, name = name))
    }

    suspend fun renameArtistSongGroup(group: ArtistSongGroup, newName: String) {
        artistDao.updateArtistSongGroup(group.copy(name = newName))
    }

    suspend fun deleteArtistSongGroup(groupId: Long) {
        artistDao.deleteArtistSongGroup(groupId)
    }

    suspend fun addSongToArtistGroup(groupId: Long, songId: Long) {
        val maxPos = artistDao.getMaxSongPositionInGroup(groupId)
        artistDao.insertSongIntoArtistSongGroup(ArtistSongGroupSongCrossRef(groupId, songId, maxPos + 1))
    }

    suspend fun removeSongFromArtistGroup(groupId: Long, songId: Long) {
        artistDao.deleteSongFromArtistSongGroup(groupId, songId)
    }
}