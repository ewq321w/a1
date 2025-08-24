package com.example.m.data.repository

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
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
    val songsNextPage: Page?
)

data class PlaylistPage(
    val playlistInfo: PlaylistInfo,
    val nextPage: Page?
)

data class MorePlaylistItemsResult(
    val items: List<StreamInfoItem>,
    val nextPage: Page?
)

data class SimplePage(
    val items: List<InfoItem>,
    val nextPage: Page?
)

@Singleton
class YoutubeRepository @Inject constructor() {

    private val streamInfoCache = LruCache<String, StreamInfo>(100)

    // Internal state to hold the handlers for pagination
    private var lastSearchQueryHandler: SearchQueryHandler? = null
    private var lastArtistSongsTabHandler: Any? = null

    suspend fun getPlaylistDetails(playlistUrl: String): PlaylistPage? {
        return withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val playlistInfo = PlaylistInfo.getInfo(service, playlistUrl)
                PlaylistPage(playlistInfo, playlistInfo.nextPage)
            } catch (e: Exception) {
                e.printStackTrace()
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
                e.printStackTrace()
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

    suspend fun search(query: String, filter: String): SimplePage? {
        return withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val contentFilter = if (filter == "all") emptyList() else listOf(filter)
                val queryHandler = service.searchQHFactory.fromQuery(query, contentFilter, null)

                // Store the handler for subsequent "load more" calls
                lastSearchQueryHandler = queryHandler

                val searchInfo = SearchInfo.getInfo(service, queryHandler)
                SimplePage(searchInfo.relatedItems, searchInfo.nextPage)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun getMoreSearchResults(page: Page): ListExtractor.InfoItemsPage<InfoItem>? {
        val handler = lastSearchQueryHandler ?: return null
        return withContext(Dispatchers.IO) {
            try {
                SearchInfo.getMoreItems(ServiceList.YouTube, handler, page)
            } catch (e: Exception) {
                e.printStackTrace()
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
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun getMoreArtistSongs(nextPage: Page): ListExtractor.InfoItemsPage<InfoItem>? {
        val handler = lastArtistSongsTabHandler
        if (handler !is ListLinkHandler) return null

        return withContext(Dispatchers.IO) {
            try {
                ChannelTabInfo.getMoreItems(ServiceList.YouTube, handler, nextPage)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private suspend fun getPaginatedArtistSongs(channelInfo: ChannelInfo): Triple<List<StreamInfoItem>, Page?, Any?> {
        val service = ServiceList.YouTube
        var songs = emptyList<StreamInfoItem>()
        var songsNextPage: Page? = null
        var songsTabHandler: Any? = null

        val tracksTabHandler = channelInfo.tabs.find { it.contentFilters.contains(ChannelTabs.TRACKS) }
        if (tracksTabHandler != null) {
            try {
                val tabInfo = ChannelTabInfo.getInfo(service, tracksTabHandler)
                songs = tabInfo.relatedItems.filterIsInstance<StreamInfoItem>()
                songsNextPage = tabInfo.nextPage
                songsTabHandler = tracksTabHandler
            } catch (e: Exception) { e.printStackTrace() }
        }

        if (songs.isEmpty()) {
            val videosTabHandler = channelInfo.tabs.find { it.contentFilters.contains(ChannelTabs.VIDEOS) }
            if (videosTabHandler != null) {
                try {
                    val tabInfo = ChannelTabInfo.getInfo(service, videosTabHandler)
                    songs = tabInfo.relatedItems.filterIsInstance<StreamInfoItem>()
                    songsNextPage = tabInfo.nextPage
                    songsTabHandler = videosTabHandler
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
        return Triple(songs, songsNextPage, songsTabHandler)
    }

    suspend fun getMusicArtistDetails(channelUrl: String): ArtistDetails? {
        return withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val channelInfo = ChannelInfo.getInfo(service, channelUrl)

                val (songs, songsNextPage, songsTabHandler) = getPaginatedArtistSongs(channelInfo)
                lastArtistSongsTabHandler = songsTabHandler

                // Album logic remains unchanged (loads all)
                var albums = emptyList<PlaylistInfoItem>()
                val albumTabHandler = channelInfo.tabs.find { it.contentFilters.contains(ChannelTabs.ALBUMS) }
                if (albumTabHandler != null) {
                    try {
                        val tabInfo = ChannelTabInfo.getInfo(service, albumTabHandler)
                        val albumList = tabInfo.relatedItems.filterIsInstance<PlaylistInfoItem>().toMutableList()
                        var nextPage = tabInfo.nextPage
                        while(nextPage != null) {
                            val page = ChannelTabInfo.getMoreItems(service, albumTabHandler, nextPage)
                            albumList.addAll(page.items.filterIsInstance<PlaylistInfoItem>())
                            nextPage = page.nextPage
                        }
                        albums = albumList
                    } catch (e: Exception) { e.printStackTrace() }
                }

                ArtistDetails(channelInfo, songs, albums, songsNextPage)
            } catch (e: Exception) {
                e.printStackTrace()
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
                lastArtistSongsTabHandler = songsTabHandler

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
                    } catch (e: Exception) { e.printStackTrace() }
                }

                ArtistDetails(channelInfo, songs, playlists, songsNextPage)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
