// file: com/example/m/data/database/AppDatabase.kt
package com.example.m.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
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
        LibraryGroup::class
    ],
    version = 31,
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
    abstract fun libraryGroupDao(): LibraryGroupDao

    class AppDatabaseCallback @Inject constructor(
        private val database: Provider<AppDatabase>
    ) : RoomDatabase.Callback() {
        private val applicationScope = CoroutineScope(Dispatchers.IO)
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
        }
    }

    companion object {
        private const val DATABASE_NAME = "music_app_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(
            context: Context,
            callback: AppDatabase.AppDatabaseCallback
        ): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Get the correct, persistent path for the database.
                val dbFile = context.getDatabasePath(DATABASE_NAME)

                // Ensure the parent directory exists.
                val parentDir = dbFile.parentFile
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs()
                }
                Timber.tag("AppDatabase")
                    .d("Database path explicitly set to: ${dbFile.absolutePath}")

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    dbFile.absolutePath // Pass the full, absolute path to the builder.
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .addCallback(callback)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromSourceType(value: SourceType): String = value.name

    @TypeConverter
    fun toSourceType(value: String): SourceType = SourceType.valueOf(value)

    @TypeConverter
    fun fromDownloadStatus(value: DownloadStatus): String = value.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus = DownloadStatus.valueOf(value)

    @TypeConverter
    fun fromStringList(list: List<String>?): String? = list?.joinToString(separator = "||")

    @TypeConverter
    fun toStringList(string: String?): List<String>? = string?.split("||")
}