package com.example.m.managers

import androidx.media3.common.MediaItem
import com.example.m.data.repository.YoutubeRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

sealed class PaginatableListContext {
    data class Search(val handler: SearchQueryHandler, var nextPage: Page?) : PaginatableListContext()
    data class ArtistTab(val handler: ListLinkHandler, var nextPage: Page?) : PaginatableListContext()
}

@Singleton
class PlaybackListManager @Inject constructor(
    private val youtubeRepository: YoutubeRepository
) {
    private var currentContext: PaginatableListContext? = null
    private val mutex = Mutex()

    fun setCurrentListContext(handler: Any?, nextPage: Page?) {
        currentContext = when (handler) {
            is SearchQueryHandler -> PaginatableListContext.Search(handler, nextPage)
            is ListLinkHandler -> PaginatableListContext.ArtistTab(handler, nextPage)
            else -> null
        }
    }

    fun clearCurrentListContext() {
        currentContext = null
    }

    suspend fun fetchNextPageStreamInfoItems(): List<StreamInfoItem>? = mutex.withLock {
        val context = currentContext ?: return@withLock null

        val pageToFetch = when (context) {
            is PaginatableListContext.Search -> context.nextPage
            is PaginatableListContext.ArtistTab -> context.nextPage
        }
        if (pageToFetch == null) {
            clearCurrentListContext()
            return@withLock null
        }

        val resultPage = when (context) {
            is PaginatableListContext.Search -> youtubeRepository.getMoreSearchResults(context.handler, pageToFetch)
            is PaginatableListContext.ArtistTab -> youtubeRepository.getMoreArtistSongs(context.handler, pageToFetch)
        }

        if (resultPage != null && resultPage.items.isNotEmpty()) {
            when (context) {
                is PaginatableListContext.Search -> context.nextPage = resultPage.nextPage
                is PaginatableListContext.ArtistTab -> context.nextPage = resultPage.nextPage
            }
            if (resultPage.nextPage == null) {
                clearCurrentListContext()
            }

            return@withLock resultPage.items.filterIsInstance<StreamInfoItem>()
        } else {
            clearCurrentListContext()
            return@withLock null
        }
    }
}