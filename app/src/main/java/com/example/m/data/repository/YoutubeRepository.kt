package com.example.m.data.repository

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class MusicSearchResults(
    val songs: List<StreamInfoItem>,
    val albums: List<PlaylistInfoItem>,
    val artists: List<ChannelInfoItem>
)

data class ArtistDetails(
    val channelInfo: ChannelInfo,
    val songs: List<StreamInfoItem>,
    val albums: List<PlaylistInfoItem>,
    val songsNextPage: Page?,
    val songsTabHandler: ListLinkHandler? = null
)

data class PlaylistPage(
    val playlistInfo: PlaylistInfo,
    val nextPage: Page?
)

data class MorePlaylistItemsResult(
    val items: List<StreamInfoItem>,
    val nextPage: Page?
)

data class SearchPage(
    val items: List<InfoItem>,
    val nextPage: Page?,
    val queryHandler: SearchQueryHandler?
)

@Singleton
class YoutubeRepository @Inject constructor() {

    private val streamInfoCache = LruCache<String, StreamInfo>(100)

    suspend fun getPlaylistDetails(playlistUrl: String): PlaylistPage? {
        return withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val playlistInfo = PlaylistInfo.getInfo(service, playlistUrl)
                PlaylistPage(playlistInfo, playlistInfo.nextPage)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get playlist details for URL: $playlistUrl")
                null
            }
        }
    }

    suspend fun getMorePlaylistItems(playlistUrl: String, nextPage: Page): MorePlaylistItemsResult? {
        return withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val page = PlaylistInfo.getMoreItems(service, playlistUrl, nextPage)
                val items = page.items.filterIsInstance<StreamInfoItem>()
                MorePlaylistItemsResult(items, page.nextPage)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get more playlist items for URL: $playlistUrl")
                null
            }
        }
    }

    suspend fun searchMusic(query: String): MusicSearchResults {
        val songsResult = search(query, "music_songs")
        val albumsResult = search(query, "playlists")
        val artistsResult = search(query, "channels")

        val songs = songsResult?.items?.filterIsInstance<StreamInfoItem>() ?: emptyList()
        val albums = albumsResult?.items?.filterIsInstance<PlaylistInfoItem>() ?: emptyList()
        val artists = artistsResult?.items?.filterIsInstance<ChannelInfoItem>() ?: emptyList()

        return MusicSearchResults(songs, albums, artists)
    }

    suspend fun search(query: String, filter: String): SearchPage? {
        return withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val contentFilter = if (filter == "all") emptyList() else listOf(filter)
                val queryHandler = service.searchQHFactory.fromQuery(query, contentFilter, null)

                val searchInfo = SearchInfo.getInfo(service, queryHandler)
                SearchPage(searchInfo.relatedItems, searchInfo.nextPage, queryHandler)
            } catch (e: Exception) {
                Timber.e(e, "Search failed for query: $query")
                null
            }
        }
    }

    suspend fun getMoreSearchResults(
        queryHandler: SearchQueryHandler,
        page: Page
    ): ListExtractor.InfoItemsPage<InfoItem>? {
        return withContext(Dispatchers.IO) {
            try {
                SearchInfo.getMoreItems(ServiceList.YouTube, queryHandler, page)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get more search results.")
                null
            }
        }
    }


    suspend fun getStreamInfo(url: String): StreamInfo? {
        streamInfoCache.get(url)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val result = StreamInfo.getInfo(ServiceList.YouTube, url)
                streamInfoCache.put(url, result)
                result
            } catch (e: Exception) {
                Timber.e(e, "Failed to get stream info for URL: $url")
                null
            }
        }
    }

    suspend fun getRelatedStreams(videoUrl: String): List<StreamInfoItem>? {
        return withContext(Dispatchers.IO) {
            try {
                val streamInfo = getStreamInfo(videoUrl)
                streamInfo?.relatedStreams?.filterIsInstance<StreamInfoItem>()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get related streams for URL: $videoUrl")
                null
            }
        }
    }

    suspend fun getStreamCommentCount(url: String): Int? {
        return withContext(Dispatchers.IO) {
            try {
                // Get CommentsInfo which actually has the total comment count
                val commentsInfo = getComments(url)
                if (commentsInfo != null && !commentsInfo.isCommentsDisabled) {
                    // Use the actual comment count from CommentsInfo
                    val totalCount = commentsInfo.commentsCount
                    Timber.d("Found actual comment count: $totalCount for URL: $url")
                    totalCount
                } else {
                    Timber.d("Comments disabled or not available for URL: $url")
                    0 // Comments disabled or not available
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get comment count for URL: $url")
                null
            }
        }
    }

    suspend fun getComments(url: String): CommentsInfo? {
        return withContext(Dispatchers.IO) {
            try {
                CommentsInfo.getInfo(ServiceList.YouTube, url)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get comments for URL: $url")
                null
            }
        }
    }

    suspend fun getMoreComments(commentsInfo: CommentsInfo, page: Page): ListExtractor.InfoItemsPage<CommentsInfoItem>? {
        return withContext(Dispatchers.IO) {
            try {
                CommentsInfo.getMoreItems(ServiceList.YouTube, commentsInfo, page)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get more comments")
                null
            }
        }
    }

    suspend fun getCommentReplies(repliesPage: Page): ListExtractor.InfoItemsPage<CommentsInfoItem>? {
        return withContext(Dispatchers.IO) {
            try {
                // Use the URL-based overload to avoid ambiguity
                CommentsInfo.getMoreItems(ServiceList.YouTube, repliesPage.url, repliesPage)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get comment replies")
                null
            }
        }
    }

    suspend fun getMoreArtistSongs(handler: ListLinkHandler, nextPage: Page): ListExtractor.InfoItemsPage<InfoItem>? {
        return withContext(Dispatchers.IO) {
            try {
                ChannelTabInfo.getMoreItems(ServiceList.YouTube, handler, nextPage)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get more artist songs.")
                null
            }
        }
    }

    private suspend fun getPaginatedArtistSongs(channelInfo: ChannelInfo): Triple<List<StreamInfoItem>, Page?, ListLinkHandler?> {
        val service = ServiceList.YouTube
        var songs = emptyList<StreamInfoItem>()
        var songsNextPage: Page? = null
        var songsTabHandler: ListLinkHandler? = null

        val tracksTabHandler = channelInfo.tabs.find { it.contentFilters.contains(ChannelTabs.TRACKS) }
        if (tracksTabHandler != null) {
            try {
                val tabInfo = ChannelTabInfo.getInfo(service, tracksTabHandler)
                songs = tabInfo.relatedItems.filterIsInstance<StreamInfoItem>()
                songsNextPage = tabInfo.nextPage
                songsTabHandler = tracksTabHandler
            } catch (e: Exception) { Timber.e(e) }
        }

        if (songs.isEmpty()) {
            val videosTabHandler = channelInfo.tabs.find { it.contentFilters.contains(ChannelTabs.VIDEOS) }
            if (videosTabHandler != null) {
                try {
                    val tabInfo = ChannelTabInfo.getInfo(service, videosTabHandler)
                    songs = tabInfo.relatedItems.filterIsInstance<StreamInfoItem>()
                    songsNextPage = tabInfo.nextPage
                    songsTabHandler = videosTabHandler
                } catch (e: Exception) { Timber.e(e) }
            }
        }
        return Triple(songs, songsNextPage, songsTabHandler)
    }

    private suspend fun fetchAllSearchResults(query: String, fetchAllPages: Boolean): List<PlaylistInfoItem> {
        val initialSearchResult = search(query = query, filter = "music_albums")
        if (initialSearchResult == null) return emptyList()

        val albumList = initialSearchResult.items
            .filterIsInstance<PlaylistInfoItem>().toMutableList()

        if (fetchAllPages) {
            var nextPage = initialSearchResult.nextPage
            val queryHandler = initialSearchResult.queryHandler
            if (queryHandler != null) {
                while (nextPage != null) {
                    val page = getMoreSearchResults(queryHandler, nextPage)
                    if (page != null) {
                        albumList.addAll(page.items.filterIsInstance<PlaylistInfoItem>())
                        nextPage = page.nextPage
                    } else {
                        nextPage = null
                    }
                }
            }
        }
        return albumList
    }

    suspend fun getAlbumsForArtist(artistName: String, fetchAllPages: Boolean): List<PlaylistInfoItem> {
        return withContext(Dispatchers.IO) {
            val plainName = artistName.removeSuffix(" - Topic").trim()
            val topicName = "$plainName - Topic"

            // Run searches for both plain name and topic name in parallel for efficiency.
            val plainNameResultsDeferred = async { fetchAllSearchResults(plainName, fetchAllPages) }
            val topicNameResultsDeferred = async { fetchAllSearchResults(topicName, fetchAllPages) }

            val plainNameResults = plainNameResultsDeferred.await()
            val topicNameResults = topicNameResultsDeferred.await()

            // Combine the results and remove duplicates by their URL.
            val combinedResults = (plainNameResults + topicNameResults).distinctBy { it.url }

            // Filter the combined list to ensure the uploader is the artist, improving accuracy.
            combinedResults.filter { album ->
                album.uploaderName?.contains(plainName, ignoreCase = true) == true
            }
        }
    }

    suspend fun getMusicArtistDetails(channelUrl: String, fetchAllPages: Boolean): ArtistDetails? {
        return withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val channelInfo = ChannelInfo.getInfo(service, channelUrl)

                // 1. Try to get albums from the "Albums" tab first. This is most reliable for normal channels.
                var albums = mutableListOf<PlaylistInfoItem>()
                val albumTabHandler = channelInfo.tabs.find { it.contentFilters.contains(ChannelTabs.ALBUMS) }
                if (albumTabHandler != null) {
                    try {
                        val tabInfo = ChannelTabInfo.getInfo(service, albumTabHandler)
                        albums.addAll(tabInfo.relatedItems.filterIsInstance<PlaylistInfoItem>())
                        if (fetchAllPages) {
                            var nextPage = tabInfo.nextPage
                            while (nextPage != null) {
                                val page = ChannelTabInfo.getMoreItems(service, albumTabHandler, nextPage)
                                albums.addAll(page.items.filterIsInstance<PlaylistInfoItem>())
                                nextPage = page.nextPage
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e)
                    }
                }

                // 2. If no albums were found via tabs, or if it's a Topic channel, use the comprehensive search-based method.
                if (albums.isEmpty() || channelInfo.name.endsWith(" - Topic")) {
                    val searchAlbums = getAlbumsForArtist(channelInfo.name, fetchAllPages)
                    // Add albums from search that weren't already found via the tab
                    albums.addAll(searchAlbums)
                    albums = albums.distinctBy { it.url }.toMutableList()
                }

                // 3. Get popular songs for all music channels.
                var songs = emptyList<StreamInfoItem>()
                val plainArtistName = channelInfo.name.removeSuffix(" - Topic").trim()
                if (plainArtistName.isNotBlank()) {
                    val searchResultPage = search(query = plainArtistName, filter = "music_songs")
                    val unfilteredSongs = searchResultPage?.items?.filterIsInstance<StreamInfoItem>() ?: emptyList()
                    songs = unfilteredSongs.filter { streamInfoItem ->
                        streamInfoItem.uploaderName.equals(plainArtistName, ignoreCase = true)
                    }
                }

                val songsNextPage: Page? = null
                ArtistDetails(channelInfo, songs, albums, songsNextPage, null)

            } catch (e: Exception) {
                Timber.e(e, "Failed to get music artist details for URL: $channelUrl")
                null
            }
        }
    }

    suspend fun getVideoCreatorDetails(channelUrl: String): ArtistDetails? {
        return withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val channelInfo = ChannelInfo.getInfo(service, channelUrl)

                val (songs, songsNextPage, songsTabHandler) = getPaginatedArtistSongs(channelInfo)

                // Playlist logic remains unchanged (loads all)
                var playlists = emptyList<PlaylistInfoItem>()
                val playlistsTabHandler = channelInfo.tabs.find { it.contentFilters.contains(ChannelTabs.PLAYLISTS) }
                if (playlistsTabHandler != null) {
                    try {
                        val tabInfo = ChannelTabInfo.getInfo(service, playlistsTabHandler)
                        val playlistItems = tabInfo.relatedItems.filterIsInstance<PlaylistInfoItem>().toMutableList()
                        var nextPage = tabInfo.nextPage
                        while(nextPage != null) {
                            val page = ChannelTabInfo.getMoreItems(service, playlistsTabHandler, nextPage)
                            playlistItems.addAll(page.items.filterIsInstance<PlaylistInfoItem>())
                            nextPage = page.nextPage
                        }
                        playlists = playlistItems
                    } catch (e: Exception) { Timber.e(e) }
                }

                ArtistDetails(channelInfo, songs, playlists, songsNextPage, songsTabHandler)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get video creator details for URL: $channelUrl")
                null
            }
        }
    }
}