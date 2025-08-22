package com.example.m.ui.common

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem

/**
 * Safely gets the banner URL from a ChannelInfo object.
 * The getBanners() method is on the ChannelInfo class directly.
 */
fun ChannelInfo.getBanner(): String? {
    // Return the URL of the highest resolution image available
    return this.getBanners()?.maxByOrNull { it.width }?.url
}

/**
 * Safely gets the avatar URL from a ChannelInfo object.
 * The getAvatars() method is on the ChannelInfo class directly.
 */
fun ChannelInfo.getAvatar(): String? {
    // Return the URL of the highest resolution image available
    return this.getAvatars()?.maxByOrNull { it.width }?.url
}

/**
 * Safely gets the thumbnail URL from a PlaylistInfoItem object.
 * The getThumbnails() method is inherited from the InfoItem parent class.
 */
fun PlaylistInfoItem.getThumbnail(): String? {
    // We explicitly cast to the parent class to ensure the method is found
    val asInfoItem = this as InfoItem
    // Return the URL of the highest resolution image available
    return asInfoItem.getThumbnails()?.maxByOrNull { it.width }?.url
}