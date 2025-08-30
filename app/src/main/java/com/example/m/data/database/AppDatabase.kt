package com.example.m.data.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

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
        ArtistGroup::class,
        ArtistSongGroup::class,
        ArtistSongGroupSongCrossRef::class,
        LibraryGroup::class // FIX: Add the new entity
    ],
    version = 28, // FIX: Increment the database version
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
    abstract fun libraryGroupDao(): LibraryGroupDao // FIX: Add the new DAO

    // FIX: Callback to pre-populate the database with a default group
    class AppDatabaseCallback @Inject constructor(
        private val database: Provider<AppDatabase>
    ) : RoomDatabase.Callback() {
        private val applicationScope = CoroutineScope(Dispatchers.IO)
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            applicationScope.launch {
                val dao = database.get().libraryGroupDao()
                dao.insertGroup(LibraryGroup(groupId = 1, name = "My Library"))
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(
            context: Context,
            callback: AppDatabaseCallback // FIX: Accept the callback
        ): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbFile = context.getDatabasePath("music_app_database")
                if (dbFile.parentFile != null && !dbFile.parentFile.exists()) {
                    dbFile.parentFile.mkdirs()
                }
                Log.d("AppDatabase", "Database path determined by system: ${dbFile.absolutePath}")

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "music_app_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(callback) // FIX: Add the callback to the builder
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