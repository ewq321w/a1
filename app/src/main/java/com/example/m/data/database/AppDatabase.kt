package com.example.m.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Song::class,
        Playlist::class,
        PlaylistSongCrossRef::class,
        ListeningHistory::class,
        DownloadQueueItem::class,
        PlaybackStateEntity::class,
        Artist::class,
        ArtistSongCrossRef::class,
        ArtistGroup::class
    ],
    version = 24, // <<< Database version is updated
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun listeningHistoryDao(): ListeningHistoryDao
    abstract fun downloadQueueDao(): DownloadQueueDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun artistDao(): ArtistDao
    abstract fun artistGroupDao(): ArtistGroupDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "music_app_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class Converters {
    @androidx.room.TypeConverter
    fun fromSourceType(value: SourceType): String {
        return value.name
    }

    @androidx.room.TypeConverter
    fun toSourceType(value: String): SourceType {
        return SourceType.valueOf(value)
    }

    @androidx.room.TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return list?.joinToString(separator = "||")
    }

    @androidx.room.TypeConverter
    fun toStringList(string: String?): List<String>? {
        return string?.split("||")
    }
}