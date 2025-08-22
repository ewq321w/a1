package com.example.m.data.repository

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
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
    val albums: List<PlaylistInfoItem>
)

@Singleton
class YoutubeRepository @Inject constructor() {

    private val searchCache = LruCache<String, List<InfoItem>>(20)
    private val streamInfoCache = LruCache<String, StreamInfo>(100)
    private val artistDetailsCache = LruCache<String, ArtistDetails>(10)
    private val playlistInfoCache = LruCache<String, PlaylistInfo>(20)

    suspend fun getPlaylistDetails(playlistUrl: String): PlaylistInfo? {
        playlistInfoCache.get(playlistUrl)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val playlistInfo = PlaylistInfo.getInfo(service, playlistUrl)
                var nextPage = playlistInfo.nextPage
                val allItems = playlistInfo.relatedItems.toMutableList()

                while (nextPage != null) {
                    val page = PlaylistInfo.getMoreItems(service, playlistUrl, nextPage)
                    allItems.addAll(page.items)
                    nextPage = page.nextPage
                }
                playlistInfo.relatedItems = allItems
                playlistInfoCache.put(playlistUrl, playlistInfo)
                playlistInfo
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun searchMusic(query: String): MusicSearchResults = coroutineScope {
        val songsDeferred = async { search(query, "music_songs").filterIsInstance<StreamInfoItem>() }
        val albumsDeferred = async { search(query, "playlists").filterIsInstance<PlaylistInfoItem>() }
        val artistsDeferred = async { search(query, "channels").filterIsInstance<ChannelInfoItem>() }

        MusicSearchResults(
            songs = songsDeferred.await(),
            albums = albumsDeferred.await(),
            artists = artistsDeferred.await()
        )
    }

    suspend fun search(query: String, filter: String): List<InfoItem> {
        val cacheKey = "$query-$filter"
        searchCache.get(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val contentFilter = if (filter == "all") emptyList() else listOf(filter)
                val queryHandler = service.searchQHFactory.fromQuery(query, contentFilter, null)

                val searchInfo = SearchInfo.getInfo(service, queryHandler)
                val results = searchInfo.relatedItems
                searchCache.put(cacheKey, results)
                results
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
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

    private fun filterOfficialAlbums(playlists: List<PlaylistInfoItem>, artistName: String): List<PlaylistInfoItem> {
        val cleanArtistName = artistName.replace(" - Topic", "", ignoreCase = true).trim()
        return playlists.filter { playlist ->
            val uploader = playlist.uploaderName ?: ""
            val playlistName = playlist.name ?: ""

            val uploaderMatch = uploader.equals(cleanArtistName, ignoreCase = true) || uploader.equals(artistName, ignoreCase = true)
            val isNotMix = !listOf("mix", "favorites", "liked", "playlist", "shuffled").any { playlistName.contains(it, ignoreCase = true) }

            uploaderMatch && isNotMix
        }.sortedByDescending { it.streamCount }
    }

    suspend fun getMusicArtistDetails(channelUrl: String): ArtistDetails? {
        val cacheKey = "music-$channelUrl"
        artistDetailsCache.get(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val channelInfo = ChannelInfo.getInfo(service, channelUrl)
                val channelName = channelInfo.name ?: return@withContext null
                val isTopicChannel = channelName.endsWith(" - Topic", ignoreCase = true)

                var songs: List<StreamInfoItem>
                var albums = emptyList<PlaylistInfoItem>()

                // --- SONG LOGIC ---
                if (isTopicChannel) {
                    songs = search(channelName, "music_songs").filterIsInstance<StreamInfoItem>()
                } else {
                    var songList = emptyList<StreamInfoItem>()
                    val tracksTabHandler = channelInfo.tabs.find { it.contentFilters.contains(ChannelTabs.TRACKS) }
                    if (tracksTabHandler != null) {
                        try {
                            songList = ChannelTabInfo.getInfo(service, tracksTabHandler)
                                .relatedItems.filterIsInstance<StreamInfoItem>()
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    if (songList.isEmpty()) {
                        val videosTabHandler = channelInfo.tabs.find { it.contentFilters.contains(ChannelTabs.VIDEOS) }
                        if (videosTabHandler != null) {
                            try {
                                songList = ChannelTabInfo.getInfo(service, videosTabHandler)
                                    .relatedItems.filterIsInstance<StreamInfoItem>()
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                    songs = songList
                }

                // --- ALBUM LOGIC ---
                if (isTopicChannel) {
                    val cleanArtistName = channelName.replace(" - Topic", "", ignoreCase = true).trim()
                    val rawAlbums = search("$cleanArtistName albums", "playlists").filterIsInstance<PlaylistInfoItem>()
                    albums = rawAlbums.filter { playlist ->
                        val playlistName = playlist.name ?: ""
                        !listOf("mix", "favorites", "liked", "shuffled").any { playlistName.contains(it, ignoreCase = true) }
                    }
                } else {
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
                    if (albums.isEmpty()) {
                        val playlistTabHandler = channelInfo.tabs.find { it.contentFilters.contains(ChannelTabs.PLAYLISTS) }
                        if (playlistTabHandler != null) {
                            try {
                                val tabInfo = ChannelTabInfo.getInfo(service, playlistTabHandler)
                                val albumList = tabInfo.relatedItems.filterIsInstance<PlaylistInfoItem>().toMutableList()
                                var nextPage = tabInfo.nextPage
                                while(nextPage != null) {
                                    val page = ChannelTabInfo.getMoreItems(service, playlistTabHandler, nextPage)
                                    albumList.addAll(page.items.filterIsInstance<PlaylistInfoItem>())
                                    nextPage = page.nextPage
                                }
                                albums = filterOfficialAlbums(albumList, channelName)
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                    if (albums.isEmpty()) {
                        val rawAlbums = search("$channelName albums", "playlists").filterIsInstance<PlaylistInfoItem>()
                        albums = filterOfficialAlbums(rawAlbums, channelName)
                    }
                }

                val details = ArtistDetails(channelInfo, songs, albums)
                artistDetailsCache.put(cacheKey, details)
                details
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun getVideoCreatorDetails(channelUrl: String): ArtistDetails? {
        val cacheKey = "video-$channelUrl"
        artistDetailsCache.get(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val channelInfo = ChannelInfo.getInfo(service, channelUrl)
                val channelName = channelInfo.name ?: return@withContext null

                var videos = emptyList<StreamInfoItem>()
                val videosTabHandler = channelInfo.tabs.find { it.contentFilters.contains(ChannelTabs.VIDEOS) }
                if (videosTabHandler != null) {
                    try {
                        videos = ChannelTabInfo.getInfo(service, videosTabHandler)
                            .relatedItems.filterIsInstance<StreamInfoItem>()
                    } catch (e: Exception) { e.printStackTrace() }
                }

                var playlists = emptyList<PlaylistInfoItem>()
                val playlistsTabHandler = channelInfo.tabs.find { it.contentFilters.contains(ChannelTabs.PLAYLISTS) }
                if (playlistsTabHandler != null) {
                    try {
                        val tabInfo = ChannelTabInfo.getInfo(service, playlistsTabHandler)
                        playlists = tabInfo.relatedItems.filterIsInstance<PlaylistInfoItem>().toMutableList()
                        var nextPage = tabInfo.nextPage
                        while(nextPage != null) {
                            val page = ChannelTabInfo.getMoreItems(service, playlistsTabHandler, nextPage)
                            playlists.addAll(page.items.filterIsInstance<PlaylistInfoItem>())
                            nextPage = page.nextPage
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }

                if (videos.isEmpty()) {
                    videos = search(channelName, "all").filterIsInstance<StreamInfoItem>()
                }
                if (playlists.isEmpty()) {
                    playlists = search("$channelName playlists", "playlists").filterIsInstance<PlaylistInfoItem>()
                }

                val details = ArtistDetails(channelInfo, songs = videos, albums = playlists)
                artistDetailsCache.put(cacheKey, details)
                details
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}