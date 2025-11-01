// file: com/example/m/managers/PlaylistManager.kt
package com.example.m.managers

import android.content.Context
import com.example.m.data.database.*
import com.example.m.data.repository.LibraryRepository
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
    private val libraryRepository: LibraryRepository,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun addItemToPlaylist(playlistId: Long, item: Any) {
        scope.launch {
            val song = libraryRepository.getOrCreateSongFromItem(item, null)
            addSongToPlaylistInternal(song, playlistId)
        }
    }

    fun createPlaylistAndAddItem(playlistName: String, item: Any, groupId: Long) {
        scope.launch {
            val newPlaylistId = playlistDao.insertPlaylist(Playlist(name = playlistName.trim(), libraryGroupId = groupId))
            val song = libraryRepository.getOrCreateSongFromItem(item, groupId)
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
            libraryRepository.startDownload(song)
        }
    }

    suspend fun cacheAndGetSong(item: StreamInfoItem): Song {
        val rawUrl = item.url

        // Validate that we have a URL
        if (rawUrl.isNullOrBlank()) {
            timber.log.Timber.e("StreamInfoItem has no URL: ${item.name}")
            throw IllegalArgumentException("StreamInfoItem must have a valid URL")
        }

        val normalizedUrl = rawUrl.replace("music.youtube.com", "www.youtube.com")

        return songDao.getSongByUrl(normalizedUrl) ?: run {
            // Robust video ID extraction from various YouTube URL formats
            val videoId = extractVideoIdFromUrl(rawUrl)

            if (videoId == null) {
                timber.log.Timber.e("Failed to extract video ID from URL: $rawUrl")
            }

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

            timber.log.Timber.d("Creating new song from StreamInfoItem: ${newSong.title}, URL: $normalizedUrl, VideoID: $videoId")

            val finalSong = songDao.upsertSong(newSong)
            libraryRepository.linkSongToArtist(finalSong)
            finalSong
        }
    }

    private fun extractVideoIdFromUrl(url: String): String? {
        return try {
            // Extract video ID from various YouTube URL formats
            when {
                url.contains("youtube.com/watch?v=") || url.contains("music.youtube.com/watch?v=") -> {
                    val regex = Regex("v=([a-zA-Z0-9_-]{11})")
                    regex.find(url)?.groupValues?.get(1)
                }
                url.contains("youtu.be/") -> {
                    val regex = Regex("youtu.be/([a-zA-Z0-9_-]{11})")
                    regex.find(url)?.groupValues?.get(1)
                }
                // If the URL itself is just the video ID (11 characters)
                url.matches(Regex("[a-zA-Z0-9_-]{11}")) -> url
                else -> null
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to extract video ID from URL: $url")
            null
        }
    }
}