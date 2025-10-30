// file: com/example/m/data/database/DatabaseBackupManager.kt
package com.example.m.data.database

import android.content.Context
import android.net.Uri
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase
) {

    companion object {
        private const val TAG = "DatabaseBackupManager"
        private const val DATABASE_NAME = "music_app_database"
        private const val BACKUP_FILE_EXTENSION = ".db"
    }

    /**
     * Export database to a file URI (for SAF - Storage Access Framework)
     */
    suspend fun exportDatabaseToUri(destinationUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Force checkpoint to ensure all data is written to the database file
            // This does NOT close the database, just ensures pending writes are committed
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()

            val dbFile = context.getDatabasePath(DATABASE_NAME)

            if (!dbFile.exists()) {
                return@withContext Result.failure(Exception("Database file not found"))
            }

            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                FileInputStream(dbFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@withContext Result.failure(Exception("Could not open output stream"))

            Timber.tag(TAG).d("Database exported successfully to $destinationUri")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to export database")
            Result.failure(e)
        }
    }

    /**
     * Import database from a file URI
     * NOTE: The app MUST be restarted after import for changes to take effect
     */
    suspend fun importDatabaseFromUri(sourceUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            val walFile = File(dbFile.parentFile, "$DATABASE_NAME-wal")
            val shmFile = File(dbFile.parentFile, "$DATABASE_NAME-shm")

            // Close the database completely
            try {
                database.close()
                Timber.tag(TAG).d("Database closed for import")
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Error closing database, continuing...")
            }

            // Create backup of current database before importing
            val backupFile = File(dbFile.parentFile, "${DATABASE_NAME}_backup_${System.currentTimeMillis()}.db")
            if (dbFile.exists()) {
                dbFile.copyTo(backupFile, overwrite = true)
                Timber.tag(TAG).d("Created backup of current database at ${backupFile.path}")
            }

            // Read imported database to a temp file first for validation
            val tempFile = File(dbFile.parentFile, "${DATABASE_NAME}_temp_import")
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@withContext Result.failure(Exception("Could not open input stream"))

            // Basic validation: check if it's a valid SQLite database
            val isValid = try {
                // Create a test database to validate the file
                val testDbPath = File(dbFile.parentFile, DATABASE_NAME + "_test")
                tempFile.copyTo(testDbPath, overwrite = true)

                val testDb = Room.databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    DATABASE_NAME + "_test"
                ).build()

                // Try to query the database
                testDb.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sqlite_master").use { cursor ->
                    cursor.moveToFirst()
                }
                testDb.close()

                // Clean up test database
                testDbPath.delete()
                File(testDbPath.parentFile, "${DATABASE_NAME}_test-wal").delete()
                File(testDbPath.parentFile, "${DATABASE_NAME}_test-shm").delete()

                true
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Validation failed")
                false
            }

            if (!isValid) {
                // Cleanup temp file and backup
                tempFile.delete()
                backupFile.delete()
                return@withContext Result.failure(Exception("Invalid database file. Import cancelled."))
            }

            // Delete WAL and SHM files
            walFile.delete()
            shmFile.delete()

            // Replace current database with imported one
            tempFile.copyTo(dbFile, overwrite = true)
            tempFile.delete()

            // Delete backup on success (user can make another backup if needed)
            backupFile.delete()

            Timber.tag(TAG).d("Database imported successfully from $sourceUri - app restart required")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to import database")
            Result.failure(e)
        }
    }

    /**
     * Get suggested backup filename with timestamp
     */
    fun getSuggestedBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        return "M_backup_$timestamp$BACKUP_FILE_EXTENSION"
    }

    /**
     * Get database file size in bytes
     */
    fun getDatabaseSize(): Long {
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        return if (dbFile.exists()) dbFile.length() else 0
    }

    /**
     * Get formatted database size string
     */
    fun getFormattedDatabaseSize(): String {
        val sizeBytes = getDatabaseSize()
        return when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            else -> "%.2f MB".format(sizeBytes / (1024.0 * 1024.0))
        }
    }
}

