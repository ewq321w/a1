package com.example.m.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import coil.ImageLoader
import coil.disk.DiskCache
import coil.intercept.Interceptor
import coil.request.CachePolicy
import com.example.m.data.database.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

@UnstableApi
private class CustomPlayer(player: Player) : ForwardingPlayer(player) {
    companion object {
        private const val SEEK_TO_PREVIOUS_THRESHOLD_MS = 5000L // 5 seconds
    }

    override fun seekToPrevious() {
        if (currentPosition > SEEK_TO_PREVIOUS_THRESHOLD_MS || !hasPreviousMediaItem()) {
            seekTo(0)
        } else {
            super.seekToPreviousMediaItem()
        }
    }

    override fun seekToPreviousMediaItem() {
        seekToPrevious()
    }
}

private fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
    return when {
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
        else -> false
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Singleton
    @Provides
    fun provideSongDao(database: AppDatabase): SongDao = database.songDao()

    @Singleton
    @Provides
    fun providePlaylistDao(database: AppDatabase): PlaylistDao = database.playlistDao()

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
        return MediaSession.Builder(context, customPlayer).build()
    }

    @Singleton
    @Provides
    @Named("Coil")
    fun provideCoilOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    @Singleton
    @Provides
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @Named("Coil") okHttpClient: OkHttpClient
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache_coil"))
                    .maxSizePercent(0.1) // Use 10% of app space for cache
                    .build()
            }
            .crossfade(true)
            .components {
                add(Interceptor { chain ->
                    val request = chain.request
                    if (!isNetworkAvailable(context)) {
                        val newRequest = request.newBuilder()
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.DISABLED)
                            .build()
                        chain.proceed(newRequest)
                    } else {
                        chain.proceed(request)
                    }
                })
            }
            .build()
    }
}