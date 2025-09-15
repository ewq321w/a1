// file: com/example/m/managers/ThumbnailProcessor.kt
package com.example.m.managers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import coil.ImageLoader
import coil.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class ThumbnailProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader
) {
    private val cache = LruCache<String, List<String>>(100)

    suspend fun process(urls: List<String>): List<String> = withContext(Dispatchers.IO) {
        if (urls.isEmpty()) return@withContext emptyList()
        val cacheKey = urls.joinToString().hashCode().toString()
        cache.get(cacheKey)?.let { return@withContext it }

        val uniqueUrls = urls.filter { it.isNotBlank() }.distinct().take(20)
        if (uniqueUrls.size <= 2) {
            cache.put(cacheKey, uniqueUrls)
            return@withContext uniqueUrls
        }

        val urlToHashMap = uniqueUrls.map { url ->
            async {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(50, 50)
                    .build()
                val bitmap = (imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
                val hash = bitmap?.let {
                    val byteBuffer = ByteBuffer.allocate(it.byteCount)
                    it.copyPixelsToBuffer(byteBuffer)
                    byteBuffer.array().contentHashCode()
                }
                url to hash
            }
        }.awaitAll()

        val trulyUniqueUrls = urlToHashMap
            .distinctBy { it.second }
            .map { it.first }

        val result = when {
            trulyUniqueUrls.size <= 2 -> trulyUniqueUrls.take(1)
            trulyUniqueUrls.size == 3 -> trulyUniqueUrls + trulyUniqueUrls.first()
            else -> trulyUniqueUrls.take(4)
        }

        cache.put(cacheKey, result)
        result
    }

    suspend fun getCroppedSquareBitmap(imageUrl: String?): ByteArray? {
        if (imageUrl.isNullOrBlank()) return null
        return try {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false) // Important for manipulation
                .build()
            val result = (imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap ?: return null

            // Crop to a square from the center
            val width = result.width
            val height = result.height
            val size = min(width, height)
            val x = (width - size) / 2
            val y = (height - size) / 2
            val squaredBitmap = Bitmap.createBitmap(result, x, y, size, size)

            val stream = ByteArrayOutputStream()
            squaredBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        } catch (e: Exception) {
            Timber.e(e, "Failed to crop square bitmap for URL: $imageUrl")
            null
        }
    }
}