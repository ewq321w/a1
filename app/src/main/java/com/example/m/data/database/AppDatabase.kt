// file: com/example/m/data/database/AppDatabase.kt
package com.example.m.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
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
        LibraryGroup::class,
        LyricsCache::class,
        SearchHistory::class
    ],
    version = 36, // Increased from 35 to 36 for search history
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
    abstract fun lyricsCacheDao(): LyricsCacheDao
    abstract fun searchHistoryDao(): SearchHistoryDao

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

        // Migration from version 32 to 33: Clear lyrics_cache table
        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Clear all lyrics cache to allow testing with fresh data
                database.execSQL("DELETE FROM lyrics_cache")
                Timber.d("Cleared lyrics cache for fresh testing")
            }
        }

        // Migration from version 33 to 34: Clear lyrics_cache table again for improved testing
        val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Clear all lyrics cache to test with the improved normalizer and spacing fixes
                database.execSQL("DELETE FROM lyrics_cache")
                Timber.d("Cleared lyrics cache for testing improved normalizer (v34)")
            }
        }

        // Migration from version 34 to 35: Add listening time tracking to playback state
        val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new listening time tracking columns to playback_state table
                try {
                    database.execSQL("ALTER TABLE playback_state ADD COLUMN accumulatedListeningTime INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE playback_state ADD COLUMN playCountIncrements INTEGER NOT NULL DEFAULT 0")
                    Timber.d("Added listening time tracking columns to playback_state table")
                } catch (e: Exception) {
                    // Columns might already exist or table might not exist yet
                    Timber.w(e, "Could not add listening time columns (might already exist)")
                }
            }
        }

        // Migration from version 35 to 36: Add search history table
        val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS search_history (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            query TEXT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            searchCount INTEGER NOT NULL DEFAULT 1
                        )
                    """)
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_search_history_query ON search_history(query)")
                    Timber.d("Created search_history table")
                } catch (e: Exception) {
                    Timber.w(e, "Could not create search_history table (might already exist)")
                }
            }
        }

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
                    .addMigrations(MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36)
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