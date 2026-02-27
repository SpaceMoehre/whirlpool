package com.whirlpool.app.ui

import com.whirlpool.engine.VideoItem

internal object DownloadedVideoIndex {
    fun key(channelId: String, videoId: String): String? {
        val normalizedChannelId = channelId.trim()
        val normalizedVideoId = videoId.trim()
        if (normalizedChannelId.isEmpty() || normalizedVideoId.isEmpty()) {
            return null
        }
        return "$normalizedChannelId::$normalizedVideoId"
    }

    fun resolveChannelId(video: VideoItem, fallbackChannelId: String): String {
        return video.network
            ?.trim()
            ?.takeIf { value -> value.isNotEmpty() }
            ?: fallbackChannelId
    }

    fun isDownloaded(
        downloadedVideoKeys: Set<String>,
        video: VideoItem,
        fallbackChannelId: String,
    ): Boolean {
        val channelId = resolveChannelId(video = video, fallbackChannelId = fallbackChannelId)
        val key = key(channelId = channelId, videoId = video.id) ?: return false
        return key in downloadedVideoKeys
    }

    fun collectDownloadedKeys(
        videos: List<VideoItem>,
        fallbackChannelId: String,
        hasDownloadedPath: (channelId: String, videoId: String) -> Boolean,
    ): Set<String> {
        if (videos.isEmpty()) {
            return emptySet()
        }
        return videos.mapNotNull { video ->
            val videoId = video.id.trim()
            if (videoId.isEmpty()) {
                return@mapNotNull null
            }
            val channelId = resolveChannelId(
                video = video,
                fallbackChannelId = fallbackChannelId,
            )
            if (hasDownloadedPath(channelId, videoId)) {
                key(channelId = channelId, videoId = videoId)
            } else {
                null
            }
        }.toSet()
    }
}
