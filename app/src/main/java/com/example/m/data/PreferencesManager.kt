package com.example.m.data

import android.content.Context
import androidx.core.content.edit
import com.example.m.ui.library.SongSortOrder
import com.example.m.ui.library.details.ArtistSortOrder
import com.example.m.ui.library.details.PlaylistSortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var lastLibraryView: String
        get() = prefs.getString("last_library_view", "Playlists") ?: "Playlists"
        set(value) = prefs.edit { putString("last_library_view", value) }

    // FIX: Add property to store the active library group ID. 0L represents "All Music".
    var activeLibraryGroupId: Long
        get() = prefs.getLong("active_library_group_id", 0L)
        set(value) = prefs.edit { putLong("active_library_group_id", value) }

    var songsSortOrder: SongSortOrder
        get() {
            val orderName = prefs.getString("songs_sort_order", SongSortOrder.ARTIST.name)
            return try {
                SongSortOrder.valueOf(orderName ?: SongSortOrder.ARTIST.name)
            } catch (e: IllegalArgumentException) {
                SongSortOrder.ARTIST
            }
        }
        set(value) = prefs.edit { putString("songs_sort_order", value.name) }

    var artistSortOrder: ArtistSortOrder
        get() {
            val orderName = prefs.getString("artist_sort_order", ArtistSortOrder.TITLE.name)
            return try {
                ArtistSortOrder.valueOf(orderName ?: ArtistSortOrder.TITLE.name)
            } catch (e: IllegalArgumentException) {
                ArtistSortOrder.TITLE
            }
        }
        set(value) = prefs.edit { putString("artist_sort_order", value.name) }

    var playlistSortOrder: PlaylistSortOrder
        get() {
            val orderName = prefs.getString("playlist_sort_order", PlaylistSortOrder.CUSTOM.name)
            return try {
                PlaylistSortOrder.valueOf(orderName ?: PlaylistSortOrder.CUSTOM.name)
            } catch (e: IllegalArgumentException) {
                PlaylistSortOrder.CUSTOM
            }
        }
        set(value) = prefs.edit { putString("playlist_sort_order", value.name) }
}