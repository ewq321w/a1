package com.example.m.download

import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.net.toUri
import com.example.m.data.database.*
import com.example.m.data.repository.YoutubeRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var youtubeRepository: YoutubeRepository
    @Inject
    lateinit var songDao: SongDao
    @Inject
    lateinit var downloadQueueDao: DownloadQueueDao
    @Inject
    @Named("NewPipe")
    lateinit var okHttpClient: OkHttpClient

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val isProcessing = AtomicBoolean(false)

    companion object {
        const val ACTION_PROCESS_QUEUE = "com.example.m.PROCESS_QUEUE"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 5000L // 5 seconds
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PROCESS_QUEUE) {
            serviceScope.launch {
                // The status is already set to QUEUED by the manager, so we just process the queue.
                processNextInQueue()
            }
        }
        return START_NOT_STICKY
    }

    private fun processNextInQueue() {
        if (isProcessing.compareAndSet(false, true)) {
            serviceScope.launch {
                val nextQueueItem = downloadQueueDao.getNextItem()
                if (nextQueueItem != null) {
                    val songToDownload = songDao.getSongById(nextQueueItem.songId)

                    if (songToDownload != null) {
                        val path = songToDownload.localFilePath
                        var fileExists = false
                        if (path != null) {
                            val uri = path.toUri()
                            fileExists = if (uri.scheme == "content") {
                                try {
                                    applicationContext.contentResolver.openInputStream(uri)?.use { it.close() }
                                    true
                                } catch (e: Exception) {
                                    false
                                }
                            } else {
                                File(path).exists()
                            }
                        }

                        if (fileExists) {
                            songDao.updateDownloadStatus(songToDownload.songId, DownloadStatus.DOWNLOADED)
                            downloadQueueDao.deleteItem(songToDownload.songId)
                            isProcessing.set(false)
                            processNextInQueue()
                        } else {
                            downloadSong(songToDownload)
                        }
                    } else {
                        downloadQueueDao.deleteItem(nextQueueItem.songId)
                        isProcessing.set(false)
                        processNextInQueue()
                    }
                } else {
                    isProcessing.set(false)
                    stopSelf()
                }
            }
        }
    }

    private suspend fun downloadSong(song: Song) {
        songDao.updateDownloadInfo(song.songId, DownloadStatus.DOWNLOADING, 0)
        var isDownloadSuccessful = false
        val tempFile = File(cacheDir, "${UUID.randomUUID()}.part")

        for (attempt in 1..MAX_RETRIES) {
            try {
                val totalBytesDownloaded = AtomicLong(0)

                val streamInfo = youtubeRepository.getStreamInfo(song.youtubeUrl)
                val audioStream = streamInfo?.audioStreams?.maxByOrNull { it.bitrate }

                if (streamInfo == null || audioStream == null || audioStream.url == null) {
                    throw IOException("Could not get a valid audio stream URL.")
                }

                val contentLength = getContentLength(audioStream.url.toString())
                if (contentLength <= 0) throw IOException("Could not get content length.")

                val segments = 32
                val segmentSize = contentLength / segments

                RandomAccessFile(tempFile, "rw").use { randomAccessFile ->
                    randomAccessFile.setLength(contentLength)
                    coroutineScope {
                        (0 until segments).map { i ->
                            async(Dispatchers.IO) {
                                val startByte = i * segmentSize
                                val endByte = if (i == segments - 1) contentLength - 1 else startByte + segmentSize - 1
                                downloadSegment(song.songId, audioStream.url.toString(), randomAccessFile, startByte, endByte, totalBytesDownloaded, contentLength)
                            }
                        }.awaitAll()
                    }
                }

                val uniqueName = "${song.artist}_${song.title}_${song.videoId}"
                val fileName = "${uniqueName.replace("[^a-zA-Z0-9.-]".toRegex(), "_")}.m4a"

                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/MyMusicApp")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }

                val resolver = applicationContext.contentResolver
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        tempFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)

                    val downloadedSong = song.copy(localFilePath = uri.toString())
                    songDao.upsertDownloadedSong(downloadedSong) // This will update status to DOWNLOADED
                    isDownloadSuccessful = true
                    break
                } else {
                    throw IOException("MediaStore failed to create new file entry.")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAY_MS)
                }
            }
        }

        if (tempFile.exists()) {
            tempFile.delete()
        }

        if (!isDownloadSuccessful) {
            songDao.updateDownloadStatus(song.songId, DownloadStatus.FAILED)
        }

        downloadQueueDao.deleteItem(song.songId)
        isProcessing.set(false)
        processNextInQueue()
    }

    private suspend fun getContentLength(url: String): Long {
        return withContext(Dispatchers.IO) {
            try {
                val request = OkHttpRequest.Builder().url(url).head().build()
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    response.header("Content-Length")?.toLong() ?: 0L
                } else {
                    0L
                }
            } catch (e: IOException) {
                0L
            }
        }
    }

    private suspend fun downloadSegment(
        songId: Long,
        url: String,
        raf: RandomAccessFile,
        startByte: Long,
        endByte: Long,
        totalBytesDownloaded: AtomicLong,
        contentLength: Long
    ) {
        val request = OkHttpRequest.Builder().url(url).header("Range", "bytes=$startByte-$endByte").build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) throw IOException("Failed to download segment: ${response.message}")

        response.body?.byteStream()?.use { input ->
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            var currentPosition = startByte
            while (input.read(buffer).also { bytesRead = it } != -1) {
                synchronized(raf) {
                    raf.seek(currentPosition)
                    raf.write(buffer, 0, bytesRead)
                }
                currentPosition += bytesRead
                val downloaded = totalBytesDownloaded.addAndGet(bytesRead.toLong())
                val progress = ((downloaded * 100) / contentLength).toInt()
                songDao.updateDownloadInfo(songId, DownloadStatus.DOWNLOADING, progress)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}