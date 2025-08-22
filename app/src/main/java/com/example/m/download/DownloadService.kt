package com.example.m.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.example.m.R
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

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var youtubeRepository: YoutubeRepository
    @Inject
    lateinit var songDao: SongDao
    @Inject
    lateinit var downloadQueueDao: DownloadQueueDao
    @Inject
    lateinit var okHttpClient: OkHttpClient

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var notificationManager: NotificationManager
    private val isProcessing = AtomicBoolean(false)

    companion object {
        const val ACTION_PROCESS_QUEUE = "com.example.m.PROCESS_QUEUE"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 5000L // 5 seconds
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PROCESS_QUEUE) {
            processNextInQueue()
        }
        return START_STICKY
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
                        val isAlreadyDownloaded = fileExists

                        if (isAlreadyDownloaded) {
                            downloadQueueDao.deleteItem(songToDownload.songId)
                            isProcessing.set(false)
                            processNextInQueue()
                        } else {
                            downloadSong(songToDownload, songToDownload.songId.toInt())
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

    private suspend fun downloadSong(song: Song, notificationId: Int) {
        val notification = createNotification(song.title, 0)
        startForeground(notificationId, notification)

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

                val segments = 16
                val segmentSize = contentLength / segments

                RandomAccessFile(tempFile, "rw").use { randomAccessFile ->
                    randomAccessFile.setLength(contentLength)
                    coroutineScope {
                        (0 until segments).map { i ->
                            async(Dispatchers.IO) {
                                val startByte = i * segmentSize
                                val endByte = if (i == segments - 1) contentLength - 1 else startByte + segmentSize - 1
                                downloadSegment(audioStream.url.toString(), randomAccessFile, startByte, endByte, totalBytesDownloaded, contentLength, song.title, notificationId)
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
                    songDao.upsertDownloadedSong(downloadedSong)
                    isDownloadSuccessful = true
                    break // Exit loop on success
                } else {
                    throw IOException("MediaStore failed to create new file entry.")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                if (attempt < MAX_RETRIES) {
                    updateNotification(song.title, 0, notificationId, isError = false, statusText = "Download failed. Retrying...")
                    delay(RETRY_DELAY_MS)
                } else {
                    updateNotification(song.title, 0, notificationId, isError = true, statusText = "Download failed")
                }
            }
        } // End of retry loop

        // **FIX**: Removed the incorrect 'finally' keyword. This code now runs
        // correctly after the for-loop has finished.
        if (tempFile.exists()) {
            tempFile.delete()
        }
        downloadQueueDao.deleteItem(song.songId)

        if (isDownloadSuccessful) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
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
        url: String,
        raf: RandomAccessFile,
        startByte: Long,
        endByte: Long,
        totalBytesDownloaded: AtomicLong,
        contentLength: Long,
        videoName: String,
        notificationId: Int
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
                updateNotification(videoName, progress, notificationId)
            }
        }
    }

    private fun createNotification(title: String, progress: Int, isError: Boolean = false, statusText: String = "Download in progress"): android.app.Notification {
        val channelId = "download_channel"
        val channel = NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(!isError)

        if (isError) {
            builder.setContentText(statusText)
        } else {
            builder.setProgress(100, progress, progress == 0 && statusText.contains("Retrying"))
            builder.setContentText(statusText)

        }

        return builder.build()
    }

    private fun updateNotification(title: String, progress: Int, notificationId: Int, isError: Boolean = false, statusText: String = "Download in progress") {
        val notification = createNotification(title, progress, isError, statusText)
        notificationManager.notify(notificationId, notification)
    }


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}