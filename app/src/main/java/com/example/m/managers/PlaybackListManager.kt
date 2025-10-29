package com.example.m.managers

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
    data class Playlist(val playlistUrl: String, var nextPage: Page?) : PaginatableListContext()
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
            is String -> PaginatableListContext.Playlist(handler, nextPage) // String = playlist URL
            else -> null
        }
        timber.log.Timber.d("PlaybackListManager: Context set - type=${currentContext?.javaClass?.simpleName}, hasNextPage=${nextPage != null}")
    }

    fun clearCurrentListContext() {
        timber.log.Timber.d("PlaybackListManager: Context cleared")
        currentContext = null
    }

    fun hasMorePages(): Boolean {
        val context = currentContext ?: return false
        val hasMore = when (context) {
            is PaginatableListContext.Search -> context.nextPage != null
            is PaginatableListContext.ArtistTab -> context.nextPage != null
            is PaginatableListContext.Playlist -> context.nextPage != null
        }
        timber.log.Timber.d("PlaybackListManager: hasMorePages=$hasMore")
        return hasMore
    }

    suspend fun fetchNextPageStreamInfoItems(): List<StreamInfoItem>? = mutex.withLock {
        val context = currentContext
        timber.log.Timber.d("PlaybackListManager: fetchNextPage called - context=$context")

        if (context == null) {
            timber.log.Timber.d("PlaybackListManager: No context available")
            return@withLock null
        }

        val pageToFetch = when (context) {
            is PaginatableListContext.Search -> context.nextPage
            is PaginatableListContext.ArtistTab -> context.nextPage
            is PaginatableListContext.Playlist -> context.nextPage
        }

        if (pageToFetch == null) {
            timber.log.Timber.d("PlaybackListManager: No next page available")
            clearCurrentListContext()
            return@withLock null
        }

        timber.log.Timber.d("PlaybackListManager: Fetching next page...")

        when (context) {
            is PaginatableListContext.Search -> {
                val resultPage = youtubeRepository.getMoreSearchResults(context.handler, pageToFetch)
                if (resultPage != null && resultPage.items.isNotEmpty()) {
                    context.nextPage = resultPage.nextPage
                    timber.log.Timber.d("PlaybackListManager: Search - Fetched ${resultPage.items.size} items, hasNextPage=${resultPage.nextPage != null}")
                    if (resultPage.nextPage == null) {
                        clearCurrentListContext()
                    }
                    return@withLock resultPage.items.filterIsInstance<StreamInfoItem>()
                } else {
                    timber.log.Timber.d("PlaybackListManager: Search - No results")
                    clearCurrentListContext()
                    return@withLock null
                }
            }
            is PaginatableListContext.ArtistTab -> {
                val resultPage = youtubeRepository.getMoreArtistSongs(context.handler, pageToFetch)
                if (resultPage != null && resultPage.items.isNotEmpty()) {
                    context.nextPage = resultPage.nextPage
                    timber.log.Timber.d("PlaybackListManager: ArtistTab - Fetched ${resultPage.items.size} items, hasNextPage=${resultPage.nextPage != null}")
                    if (resultPage.nextPage == null) {
                        clearCurrentListContext()
                    }
                    return@withLock resultPage.items.filterIsInstance<StreamInfoItem>()
                } else {
                    timber.log.Timber.d("PlaybackListManager: ArtistTab - No results")
                    clearCurrentListContext()
                    return@withLock null
                }
            }
            is PaginatableListContext.Playlist -> {
                val result = youtubeRepository.getMorePlaylistItems(context.playlistUrl, pageToFetch)
                if (result != null && result.items.isNotEmpty()) {
                    context.nextPage = result.nextPage
                    timber.log.Timber.d("PlaybackListManager: Playlist - Fetched ${result.items.size} items, hasNextPage=${result.nextPage != null}")
                    if (result.nextPage == null) {
                        clearCurrentListContext()
                    }
                    return@withLock result.items
                } else {
                    timber.log.Timber.d("PlaybackListManager: Playlist - No results")
                    clearCurrentListContext()
                    return@withLock null
                }
            }
        }
    }
}