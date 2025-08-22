package com.example.m.ui.common

import com.example.m.data.database.Song
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Gets a high-quality thumbnail URL from a YouTube video ID.
 */
fun getHighQualityThumbnailUrl(videoId: String?): String {
    return if (!videoId.isNullOrBlank()) {
        "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
    } else {
        ""
    }
}

/**
 * Extension function on StreamInfoItem to easily get the best thumbnail.
 */
fun StreamInfoItem.getHighQualityThumbnailUrl(): String {
    val videoId = this.url?.substringAfter("v=")?.substringBefore('&')
    return getHighQualityThumbnailUrl(videoId)
}

/**
 * Extension function on Song to provide a consistent thumbnail URL.
 */
fun Song.getHighQualityThumbnailUrl(): String {
    return this.thumbnailUrl
}