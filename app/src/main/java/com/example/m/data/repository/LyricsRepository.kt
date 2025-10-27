package com.example.m.data.repository

import com.example.m.data.LyricsResult
import com.example.m.data.database.LyricsCache
import com.example.m.data.database.LyricsCacheDao
import com.example.m.util.LyricsNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class LyricsRepository @Inject constructor(
    @Named("NewPipe") private val httpClient: OkHttpClient,
    private val lyricsCacheDao: LyricsCacheDao
) {

    companion object {
        private const val REQUEST_TIMEOUT_MS = 8000L
        private const val CACHE_MAX_SIZE = 500
        private const val LOG_FILE_NAME = "lyrics_debug.log"
    }

    // Track current request to allow cancellation
    private val currentCall = AtomicReference<Call?>(null)

    // Faster client with reduced timeouts for quicker song changes
    private val lyricsHttpClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .readTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .connectTimeout(2000L, TimeUnit.MILLISECONDS)
            .writeTimeout(1500L, TimeUnit.MILLISECONDS)
            .build()
    }

    suspend fun getLyrics(artist: String, title: String): LyricsResult {
        // Cancel any ongoing request first
        cancelCurrentRequest()

        return withContext(Dispatchers.IO) {
            val normalizedArtist = LyricsNormalizer.normalizeArtist(artist)
            val normalizedTitle = LyricsNormalizer.normalizeTitle(title)

            Timber.d("Starting lyrics search for: $artist - $title")
            writeToLogFile("=== NEW SONG CHANGE ===")
            writeToLogFile("Starting fresh lyrics search for: $artist - $title")

            // 1. Check database cache first (fast - no loading indicator needed)
            try {
                val cachedResult = lyricsCacheDao.getCachedLyrics(normalizedArtist, normalizedTitle)
                if (cachedResult != null && cachedResult.isSuccessful) {
                    Timber.d("Returning cached lyrics for: $artist - $title")
                    writeToLogFile("✓ CACHE HIT: Returning cached lyrics")
                    return@withContext LyricsResult(cachedResult.lyrics, cachedResult.source, true)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to read from lyrics cache")
            }

            // 2. Try to fetch lyrics from API (slow - show loading indicator)
            val result = fetchLyrics(normalizedArtist, normalizedTitle)

            // 3. Cache the result (both success and failure)
            try {
                manageCacheSize()
                val cacheEntry = LyricsCache(
                    artist = normalizedArtist,
                    title = normalizedTitle,
                    lyrics = result.lyrics,
                    source = result.source,
                    isSuccessful = result.isSuccessful
                )
                lyricsCacheDao.insertLyrics(cacheEntry)
                Timber.d("Cached result for: $artist - $title (success: ${result.isSuccessful})")
            } catch (e: Exception) {
                Timber.e(e, "Failed to write to lyrics cache")
            }

            if (!result.isSuccessful) {
                Timber.w("Failed to find lyrics for: $artist - $title")
            } else {
                Timber.d("Successfully found lyrics for: $artist - $title")
            }

            result
        }
    }

    private suspend fun fetchLyrics(artist: String, title: String): LyricsResult {
        if (artist.isBlank() || title.isBlank()) {
            return LyricsResult(null, "Lyrics API", false)
        }

        // Use centralized cleaning from LyricsNormalizer
        val cleanedArtist = LyricsNormalizer.cleanArtistName(artist)
        val cleanedTitle = LyricsNormalizer.cleanSongTitle(title)

        writeToLogFile("=== CLEANED NAMES ===")
        writeToLogFile("Original artist: '$artist' -> Cleaned: '$cleanedArtist'")
        writeToLogFile("Original title: '$title' -> Cleaned: '$cleanedTitle'")

        // Only one API call - no fallback to avoid duplicate requests
        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            fetchFromLyricsOvhAPI(cleanedArtist, cleanedTitle)
        } ?: run {
            writeToLogFile("✗ TIMEOUT: Request took longer than ${REQUEST_TIMEOUT_MS}ms")
            LyricsResult(null, "Timeout", false)
        }
    }

    private suspend fun fetchFromLyricsOvhAPI(artist: String, title: String): LyricsResult {
        return try {
            // Clean and encode artist and title for URL
            val encodedArtist = java.net.URLEncoder.encode(artist.trim(), "UTF-8")
            val encodedTitle = java.net.URLEncoder.encode(title.trim(), "UTF-8")

            val url = "https://api.lyrics.ovh/v1/$encodedArtist/$encodedTitle"

            writeToLogFile("=== NEW LYRICS REQUEST ===")
            writeToLogFile("Fetching from Lyrics.ovh API: $url")
            writeToLogFile("Original artist: '$artist', encoded: '$encodedArtist'")
            writeToLogFile("Original title: '$title', encoded: '$encodedTitle'")

            Timber.d("Fetching from Lyrics.ovh API: $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/plain, */*")
                .build()

            val call = lyricsHttpClient.newCall(request)
            // Store the call so it can be cancelled if user changes song
            currentCall.set(call)

            val response = call.execute()
            response.use {
                val responseCode = it.code

                writeToLogFile("Response code: $responseCode")
                Timber.d("Lyrics.ovh API response code: $responseCode")

                if (it.isSuccessful) {
                    val lyricsText = it.body?.string()
                    val responseLength = lyricsText?.length ?: 0

                    writeToLogFile("Raw response body length: $responseLength")
                    Timber.d("Raw response body length: $responseLength")

                    if (!lyricsText.isNullOrBlank()) {
                        val cleanedLyrics = cleanApiLyrics(lyricsText)
                        val cleanedLength = cleanedLyrics.length

                        writeToLogFile("Cleaned lyrics length: $cleanedLength")
                        Timber.d("Cleaned lyrics length: $cleanedLength")

                        if (cleanedLyrics.isNotBlank()) {
                            writeToLogFile("✓ SUCCESS: found lyrics ($cleanedLength chars)")
                            logLyricsResult(artist, title, true, cleanedLength, "SUCCESS")
                            return LyricsResult(cleanedLyrics, "Lyrics.ovh", true)
                        } else {
                            writeToLogFile("✗ FAILED: empty lyrics after cleaning")
                            logLyricsResult(artist, title, false, 0, "EMPTY_AFTER_CLEANING")
                        }
                    } else {
                        writeToLogFile("✗ FAILED: null/empty response")
                        logLyricsResult(artist, title, false, 0, "NULL_RESPONSE")
                    }
                } else {
                    val errorBody = it.body?.string()
                    writeToLogFile("✗ HTTP ERROR: $responseCode - ${it.message}")
                    logLyricsResult(artist, title, false, 0, "HTTP_$responseCode")
                }

                writeToLogFile("=== END REQUEST ===\n")
                LyricsResult(null, "Lyrics.ovh", false)
            }
        } catch (e: Exception) {
            if (e is java.io.IOException && e.message?.contains("Canceled") == true) {
                writeToLogFile("✓ REQUEST CANCELLED: User changed song")
                Timber.d("Lyrics request cancelled due to song change")
                LyricsResult(null, "Cancelled", false)
            } else {
                writeToLogFile("✗ EXCEPTION: ${e.message}")
                Timber.e(e, "✗ Lyrics.ovh API EXCEPTION: ${e.message}")
                logLyricsResult(artist, title, false, 0, "EXCEPTION: ${e.message}")
                LyricsResult(null, "Lyrics.ovh", false)
            }
        } finally {
            // Clear the current call reference
            currentCall.set(null)
        }
    }

    private fun writeToLogFile(message: String) {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        val logMessage = "[$timestamp] $message\n"

        try {
            // Write to Documents folder (accessible via file manager)
            val documentsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            val logFile = java.io.File(documentsDir, LOG_FILE_NAME)

            // Ensure the Documents directory exists
            documentsDir.mkdirs()

            // Append to the log file
            logFile.appendText(logMessage)

        } catch (e: Exception) {
            // Fallback to system log if file writing fails
            android.util.Log.e("LYRICS_FILE_LOG", "Failed to write to log file: ${e.message}")
            android.util.Log.i("LYRICS_FILE_LOG", message)

            // Try alternative location in app's external files directory
            try {
                val appExternalDir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Android/data/com.example.m/files")
                appExternalDir.mkdirs()
                val fallbackLogFile = java.io.File(appExternalDir, LOG_FILE_NAME)
                fallbackLogFile.appendText(logMessage)
            } catch (fallbackException: Exception) {
                android.util.Log.e("LYRICS_FILE_LOG", "Fallback logging also failed: ${fallbackException.message}")
            }
        }
    }

    private fun logLyricsResult(artist: String, title: String, success: Boolean, length: Int, reason: String) {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val logEntry = "[$timestamp] LYRICS_API: '$artist' - '$title' | Success: $success | Length: $length | Reason: $reason"
        Timber.i(logEntry)

        // Also log to system log for easier debugging
        android.util.Log.i("LYRICS_DEBUG", logEntry)
    }

    private fun cleanApiLyrics(lyricsText: String): String {
        return try {
            // Check if the response is JSON format
            val actualLyrics = if (lyricsText.trim().startsWith("{")) {
                // It's JSON, extract the lyrics field
                try {
                    val json = org.json.JSONObject(lyricsText)
                    json.optString("lyrics", "")
                } catch (e: Exception) {
                    writeToLogFile("Failed to parse JSON response: ${e.message}")
                    lyricsText // fallback to original text
                }
            } else {
                // It's plain text
                lyricsText
            }

            // Clean up the lyrics text while preserving spacing
            var cleaned = actualLyrics
                .replace("\\n", "\n")  // Convert literal \n to actual newlines
                .replace("\\r", "")    // Remove carriage returns
                .replace("Paroles de la chanson .* par .*\n".toRegex(), "") // Remove French headers

            // Only remove excessive blank lines (3+ consecutive newlines)
            cleaned = cleaned
                .replace(Regex("\\n{3,}"), "\n\n")  // Max 2 consecutive newlines
                // DON'T trim individual lines - preserve original spacing and indentation
                // DON'T filter blank lines - they might be intentional formatting

            // Only check for completely empty lyrics, don't impose arbitrary length limits
            if (cleaned.trim().isNotEmpty()) {
                writeToLogFile("Successfully cleaned lyrics: ${cleaned.length} chars (preserving spacing)")
                writeToLogFile("Cleaned preview: ${cleaned.take(100)}...")
                cleaned
            } else {
                writeToLogFile("Cleaned lyrics are completely empty")
                ""
            }
        } catch (e: Exception) {
            writeToLogFile("Failed to clean API lyrics: ${e.message}")
            Timber.e(e, "Failed to clean API lyrics")
            ""
        }
    }

    private suspend fun manageCacheSize() {
        try {
            val currentSize = lyricsCacheDao.getCacheSize()
            if (currentSize >= CACHE_MAX_SIZE) {
                val entriesToRemove = CACHE_MAX_SIZE / 4
                lyricsCacheDao.deleteOldestEntries(entriesToRemove)
                Timber.d("Cleaned up $entriesToRemove old lyrics cache entries")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to manage lyrics cache size")
        }
    }

    private fun cancelCurrentRequest() {
        // Cancel the ongoing HTTP call, if any
        val cancelledCall = currentCall.getAndSet(null)
        if (cancelledCall != null) {
            cancelledCall.cancel()
            writeToLogFile("✓ CANCELLED: Previous request cancelled for new song")
            Timber.d("Cancelled previous lyrics request")
        }
    }
}
