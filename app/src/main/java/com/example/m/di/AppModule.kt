package com.example.m.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import coil.ImageLoader
import coil.disk.DiskCache
import com.example.m.data.database.AppDatabase
import com.example.m.data.database.ArtistDao
import com.example.m.data.database.ArtistGroupDao
import com.example.m.data.database.DownloadQueueDao
import com.example.m.data.database.ListeningHistoryDao
import com.example.m.data.database.PlaybackStateDao
import com.example.m.data.database.PlaylistDao
import com.example.m.data.database.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
    fun provideListeningHistoryDao(database: AppDatabase): ListeningHistoryDao = database.listeningHistoryDao()

    @Singleton
    @Provides
    fun provideDownloadQueueDao(database: AppDatabase): DownloadQueueDao = database.downloadQueueDao()

    @Singleton
    @Provides
    fun providePlaybackStateDao(database: AppDatabase): PlaybackStateDao = database.playbackStateDao()

    @Singleton
    @Provides
    fun provideExoPlayer(@ApplicationContext context: Context): ExoPlayer = ExoPlayer.Builder(context).build()

    @OptIn(UnstableApi::class)
    @Singleton
    @Provides
    fun provideMediaSession(@ApplicationContext context: Context, player: ExoPlayer): MediaSession {
        val customPlayer = CustomPlayer(player)
        return MediaSession.Builder(context, customPlayer).build()
    }

    @Singleton
    @Provides
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.1)
                    .build()
            }
            .build()
    }
}