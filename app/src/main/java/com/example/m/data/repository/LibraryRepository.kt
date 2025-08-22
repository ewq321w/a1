package com.example.m.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import com.example.m.data.database.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao,
    private val downloadQueueDao: DownloadQueueDao,
    private val artistDao: ArtistDao,
    private val artistGroupDao: ArtistGroupDao,
    @ApplicationContext private val context: Context
) {
    fun getPlaylistsWithSongs(): Flow<List<PlaylistWithSongs>> {
        return playlistDao.getPlaylistsWithSongsAndOrderedSongsInternal().map { map ->
            map.entries.map { PlaylistWithSongs(it.key, it.value) }
        }
    }

    fun getAllPlaylists(): Flow<List<Playlist>> = playlistDao.getAllPlaylists()
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
            val newArtistId = artistDao.insertArtist(Artist(name = song.artist))
            artist = artistDao.getArtistByName(song.artist)
                ?: throw IllegalStateException("Failed to create and retrieve artist: ${song.artist}")
        }

        val maxPosition = artistDao.getMaxArtistSongPosition(artist.artistId)
        val newPosition = maxPosition + 1

        artistDao.insertArtistSongCrossRef(
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
}