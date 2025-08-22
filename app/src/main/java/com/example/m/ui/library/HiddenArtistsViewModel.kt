package com.example.m.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m.data.database.Artist
import com.example.m.data.database.ArtistDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HiddenArtistsViewModel @Inject constructor(
    private val artistDao: ArtistDao
) : ViewModel() {

    val hiddenArtists: StateFlow<List<Artist>> = artistDao.getHiddenArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun unhideArtist(artist: Artist) {
        viewModelScope.launch(Dispatchers.IO) {
            artistDao.unhideArtist(artist.artistId)
        }
    }
}