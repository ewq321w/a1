package com.example.m.data

import android.content.Context
import com.example.m.ui.library.details.ArtistSortOrder
import com.example.m.ui.library.details.PlaylistSortOrder
import com.example.m.ui.library.SongSortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class PreferencesManager @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var lastLibraryView: String
        get() = prefs.getString("last_library_view", "Playlists") ?: "Playlists"
        set(value) = prefs.edit { putString("last_library_view", value) }

    var songsSortOrder: SongSortOrder
        get() {
            val orderName = prefs.getString("songs_sort_order", SongSortOrder.ARTIST.name)
            return try {
                SongSortOrder.valueOf(orderName ?: SongSortOrder.ARTIST.name)
            } catch (e: IllegalArgumentException) {
                // If the stored value is invalid, return a safe default to prevent a crash.
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