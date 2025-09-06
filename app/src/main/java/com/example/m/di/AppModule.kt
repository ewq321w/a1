// file: com/example/m/di/AppModule.kt
package com.example.m.di

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import coil.ImageLoader
import coil.disk.DiskCache
import coil.request.CachePolicy
import com.example.m.MainActivity
import com.example.m.data.database.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Singleton

@UnstableApi
private class CustomPlayer(player: Player) : ForwardingPlayer(player) {
    companion object {
        private const val SEEK_TO_PREVIOUS_THRESHOLD_MS = 5000L // 5 seconds
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

    // FIX: Add the missing provider for the new LibraryGroupDao
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
    fun provideCoilOkHttpClient(
        @ApplicationContext context: Context
    ): OkHttpClient {
        val cacheSize = 100L * 1024 * 1024 // 100 MB
        val cache = Cache(File(context.cacheDir, "coil_okhttp_cache"), cacheSize)
        return OkHttpClient.Builder()
            .cache(cache)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Singleton
    @Provides
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @Named("Coil") okHttpClient: OkHttpClient
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            .allowRgb565(true)
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache_coil"))
                    .maxSizePercent(0.1)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }
}