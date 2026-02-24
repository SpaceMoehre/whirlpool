package com.whirlpool.app.data

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineRepositoryParsingTest {
    @Test
    fun parseResolutionPayload_readsStreamUrlAndHeaders() {
        val payload = """
            {
              "id": "abc123",
              "title": "Sample",
              "pageUrl": "https://example.com/watch?v=abc123",
              "streamUrl": "https://video.example.com/stream.m3u8",
              "requestHeaders": {
                "User-Agent": "UA-Test",
                "Referer": "https://example.com/"
              },
              "extractor": "youtube",
              "formatId": "18",
              "ext": "mp4",
              "protocol": "https",
              "durationSeconds": 120,
              "ytDlpVersion": "2026.01.01",
              "diagnostics": [
                "debug: first",
                "warning: second"
              ]
            }
        """.trimIndent()

        val parsed = parseResolutionPayload(payload, "https://fallback.invalid")

        assertEquals("abc123", parsed.id)
        assertEquals("https://video.example.com/stream.m3u8", parsed.streamUrl)
        assertEquals("UA-Test", parsed.requestHeaders["User-Agent"])
        assertEquals("https://example.com/", parsed.requestHeaders["Referer"])
        assertEquals("18", parsed.formatId)
        assertEquals("mp4", parsed.ext)
        assertEquals(120u, parsed.durationSeconds)
        assertEquals("2026.01.01", parsed.ytDlpVersion)
        assertEquals(2, parsed.diagnostics.size)
    }

    @Test
    fun parseResolutionPayload_rejectsMissingStreamUrl() {
        val payload = """{"id":"abc123","title":"No stream"}"""
        val error = runCatching {
            parseResolutionPayload(payload, "https://example.com/watch?v=abc123")
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertTrue(error?.message?.contains("stream url", ignoreCase = true) == true)
    }
}
