package com.example.m.managers

import android.content.Context
import android.content.Intent
import com.example.m.data.database.*
import com.example.m.data.repository.LibraryRepository
import com.example.m.download.DownloadService
import com.example.m.ui.common.getHighQualityThumbnailUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistManager @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao,
    private val downloadQueueDao: DownloadQueueDao,
    private val libraryRepository: LibraryRepository,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun addItemToPlaylist(playlistId: Long, item: Any) {
        scope.launch {
            val song = getSongForItem(item, null)
            addSongToPlaylistInternal(song, playlistId)
        }
    }

    fun createPlaylistAndAddItem(playlistName: String, item: Any, groupId: Long) {
        scope.launch {
            val newPlaylistId = playlistDao.insertPlaylist(Playlist(name = playlistName.trim(), libraryGroupId = groupId))
            val song = getSongForItem(item, groupId)
            addSongToPlaylistInternal(song, newPlaylistId)
        }
    }

    fun createEmptyPlaylist(playlistName: String, groupId: Long) {
        scope.launch {
            playlistDao.insertPlaylist(Playlist(name = playlistName.trim(), libraryGroupId = groupId))
        }
    }

    private suspend fun addSongToPlaylistInternal(song: Song, playlistId: Long) {
        val maxPosition = playlistDao.getMaxPlaylistSongPosition(playlistId)
        val newPosition = maxPosition + 1

        val crossRef = PlaylistSongCrossRef(
            playlistId = playlistId,
            songId = song.songId,
            dateAddedTimestamp = System.currentTimeMillis(),
            customOrderPosition = newPosition
        )
        playlistDao.insertSongIntoPlaylist(crossRef)

        val playlist = playlistDao.getPlaylistById(playlistId)
        if (playlist?.downloadAutomatically == true) {
            startDownload(song)
        }
    }

    suspend fun getSongForItem(item: Any, libraryGroupId: Long?): Song {
        return when (item) {
            is Song -> item
            is StreamInfoItem -> {
                val normalizedUrl = item.url?.replace("music.youtube.com", "www.youtube.com")
                    ?: item.url ?: ""
                songDao.getSongByUrl(normalizedUrl) ?: run {
                    val videoId = item.url?.substringAfter("v=")?.substringBefore('&')
                    val newSong = Song(
                        videoId = videoId,
                        youtubeUrl = normalizedUrl,
                        title = item.name ?: "Unknown Title",
                        artist = item.uploaderName ?: "Unknown Artist",
                        duration = item.duration,
                        thumbnailUrl = getHighQualityThumbnailUrl(videoId),
                        localFilePath = null,
                        libraryGroupId = libraryGroupId
                    )
                    val finalSong = songDao.upsertSong(newSong)
                    libraryRepository.linkSongToArtist(finalSong)
                    finalSong
                }
            }
            else -> throw IllegalArgumentException("Unsupported item type for playlist addition")
        }
    }

    suspend fun cacheAndGetSong(item: StreamInfoItem): Song {
        val normalizedUrl = item.url?.replace("music.youtube.com", "www.youtube.com")
            ?: item.url ?: ""
        return songDao.getSongByUrl(normalizedUrl) ?: run {
            val videoId = item.url?.substringAfter("v=")?.substringBefore('&')
            val newSong = Song(
                videoId = videoId,
                youtubeUrl = normalizedUrl,
                title = item.name ?: "Unknown Title",
                artist = item.uploaderName ?: "Unknown Artist",
                duration = item.duration,
                thumbnailUrl = getHighQualityThumbnailUrl(videoId),
                localFilePath = null,
                isInLibrary = false
            )
            val finalSong = songDao.upsertSong(newSong)
            libraryRepository.linkSongToArtist(finalSong)
            finalSong
        }
    }

    fun startDownload(song: Song) {
        scope.launch {
            downloadQueueDao.addItem(DownloadQueueItem(songId = song.songId))
            val intent = Intent(context, DownloadService::class.java).apply {
                action = DownloadService.ACTION_PROCESS_QUEUE
            }
            context.startService(intent)
        }
    }
}