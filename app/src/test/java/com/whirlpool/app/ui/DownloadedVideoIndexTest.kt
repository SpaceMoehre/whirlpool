package com.whirlpool.app.ui

import com.whirlpool.engine.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadedVideoIndexTest {
    @Test
    fun key_trimsValuesAndRejectsBlankTokens() {
        assertEquals("catflix::video-1", DownloadedVideoIndex.key(" catflix ", " video-1 "))
        assertEquals(null, DownloadedVideoIndex.key("", "video-1"))
        assertEquals(null, DownloadedVideoIndex.key("catflix", ""))
    }

    @Test
    fun resolveChannelId_prefersVideoNetworkAndFallsBack() {
        val networkVideo = sampleVideo(id = "v1", network = "dogflix")
        val fallbackVideo = sampleVideo(id = "v2", network = null)

        assertEquals("dogflix", DownloadedVideoIndex.resolveChannelId(networkVideo, "catflix"))
        assertEquals("catflix", DownloadedVideoIndex.resolveChannelId(fallbackVideo, "catflix"))
    }

    @Test
    fun isDownloaded_usesResolvedKey() {
        val downloaded = setOf("catflix::v1")
        val video = sampleVideo(id = "v1", network = null)

        assertTrue(DownloadedVideoIndex.isDownloaded(downloaded, video, fallbackChannelId = "catflix"))
        assertFalse(DownloadedVideoIndex.isDownloaded(downloaded, video, fallbackChannelId = "dogflix"))
    }

    @Test
    fun collectDownloadedKeys_includesOnlyResolvedAndPresentVideos() {
        val videos = listOf(
            sampleVideo(id = "v1", network = "catflix"),
            sampleVideo(id = "v2", network = "dogflix"),
            sampleVideo(id = " ", network = "catflix"),
        )

        val lookup = setOf("catflix::v1")
        val keys = DownloadedVideoIndex.collectDownloadedKeys(
            videos = videos,
            fallbackChannelId = "fallback",
        ) { channelId, videoId ->
            "$channelId::$videoId" in lookup
        }

        assertEquals(setOf("catflix::v1"), keys)
    }

    private fun sampleVideo(id: String, network: String?): VideoItem {
        return VideoItem(
            id = id,
            title = "Video $id",
            pageUrl = "https://example.com/watch?v=$id",
            durationSeconds = null,
            imageUrl = null,
            network = network,
            authorName = null,
            extractor = null,
            viewCount = null,
            rawJson = null,
        )
    }
}
