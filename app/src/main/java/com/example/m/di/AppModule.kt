// file: com/example/m/di/AppModule.kt
package com.example.m.di

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import coil.ImageLoader
import coil.decode.DataSource
import coil.disk.DiskCache
import coil.intercept.Interceptor
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageResult
import coil.request.SuccessResult
import com.example.m.MainActivity
import com.example.m.data.database.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Singleton
import androidx.core.graphics.drawable.toDrawable

@Singleton
class Rgb565CacheInterceptor @Inject constructor() : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val result = chain.proceed(request)

        // We only care about successful network results.
        if (result !is SuccessResult || result.dataSource != DataSource.NETWORK) {
            return result
        }

        val originalBitmap = (result.drawable as? BitmapDrawable)?.bitmap

        // If we can't get a bitmap or it's already RGB_565, we're done.
        if (originalBitmap == null || originalBitmap.config == Bitmap.Config.RGB_565) {
            return result
        }

        // Perform the conversion. This is a suspending function running on a background thread.
        val convertedBitmap = try {
            withContext(Dispatchers.Default) {
                originalBitmap.copy(Bitmap.Config.RGB_565, false)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to convert bitmap to RGB_565.")
            return result // Return original on failure
        }

        // Create a new drawable with the converted bitmap.
        val newDrawable = convertedBitmap.toDrawable(request.context.resources)

        // Return a new result. Coil will now display and cache this RGB_565 version.
        return result.copy(drawable = newDrawable)
    }
}


@UnstableApi
private class CustomPlayer(player: Player) : ForwardingPlayer(player) {
    companion object {
        private const val SEEK_TO_PREVIOUS_THRESHOLD_MS = 5000L
    }

    override fun seekToNext() {
        val wasPaused = !playWhenReady
        super.seekToNext()
        if (wasPaused) {
            play()
        }
    }

    override fun seekToPrevious() {
        val wasPaused = !playWhenReady
        if (currentPosition > SEEK_TO_PREVIOUS_THRESHOLD_MS || !hasPreviousMediaItem()) {
            seekTo(0)
        } else {
            super.seekToPreviousMediaItem()
        }
        if (wasPaused) {
            play()
        }
    }

    override fun seekToPreviousMediaItem() {
        seekToPrevious()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideAppDatabaseCallback(
        database: Provider<AppDatabase>
    ): AppDatabase.AppDatabaseCallback {
        return AppDatabase.AppDatabaseCallback(database)
    }

    @Singleton
    @Provides
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        callback: AppDatabase.AppDatabaseCallback
    ): AppDatabase {
        return AppDatabase.getDatabase(context, callback)
    }

    @Singleton
    @Provides
    fun provideSongDao(database: AppDatabase): SongDao = database.songDao()

    @Singleton
    @Provides
    fun providePlaylistDao(database: AppDatabase): PlaylistDao = database.playlistDao()

    @Singleton
    @Provides
    fun provideLibraryGroupDao(database: AppDatabase): LibraryGroupDao = database.libraryGroupDao()

    @Singleton
    @Provides
    fun provideArtistDao(database: AppDatabase): ArtistDao = database.artistDao()

    @Singleton
    @Provides
    fun provideArtistGroupDao(database: AppDatabase): ArtistGroupDao = database.artistGroupDao()

    @Singleton
    @Provides
    fun provideListeningHistoryDao(database: AppDatabase): ListeningHistoryDao =
        database.listeningHistoryDao()

    @Singleton
    @Provides
    fun provideDownloadQueueDao(database: AppDatabase): DownloadQueueDao =
        database.downloadQueueDao()

    @Singleton
    @Provides
    fun providePlaybackStateDao(database: AppDatabase): PlaybackStateDao =
        database.playbackStateDao()

    @Singleton
    @Provides
    fun provideLyricsCacheDao(database: AppDatabase): LyricsCacheDao =
        database.lyricsCacheDao()

    @Singleton
    @Provides
    fun provideSearchHistoryDao(database: AppDatabase): SearchHistoryDao =
        database.searchHistoryDao()

    @Singleton
    @Provides
    fun provideExoPlayer(@ApplicationContext context: Context): ExoPlayer =
        ExoPlayer.Builder(context).build()

    @OptIn(UnstableApi::class)
    @Singleton
    @Provides
    fun provideMediaSession(@ApplicationContext context: Context, player: ExoPlayer): MediaSession {
        val customPlayer = CustomPlayer(player)

        val sessionActivityPendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )

        return MediaSession.Builder(context, customPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    @Singleton
    @Provides
    @Named("Coil")
    fun provideCoilOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .build()
    }

    @Singleton
    @Provides
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @Named("Coil") okHttpClient: OkHttpClient,
        interceptor: Rgb565CacheInterceptor
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(interceptor)
            }
            .okHttpClient(okHttpClient)
            .allowRgb565(true)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache_coil_v2"))
                    .maxSizePercent(0.1)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .respectCacheHeaders(false) // Ignore server headers to prevent frequent network validation
            .build()
    }
}