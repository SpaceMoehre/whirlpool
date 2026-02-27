package com.whirlpool.app.data

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineRepositoryParsingTest {
    @Test
    fun sourceUrlCandidates_defaultsToHttpsAndFallsBackToHttp() {
        val candidates = sourceUrlCandidates("example.com")
        assertEquals(listOf("https://example.com", "http://example.com"), candidates)
    }

    @Test
    fun sourceUrlCandidates_keepsExplicitScheme() {
        val candidates = sourceUrlCandidates("http://localhost:8080/")
        assertEquals(listOf("http://localhost:8080"), candidates)
    }

    @Test
    fun normalizeConfiguredBaseUrl_trimsAndDefaultsScheme() {
        assertEquals("", normalizeConfiguredBaseUrl("  "))
        assertEquals("https://example.com/path", normalizeConfiguredBaseUrl("example.com/path/"))
        assertEquals("http://example.com", normalizeConfiguredBaseUrl("http://example.com/"))
    }

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

    @Test
    fun sourceUrlCandidates_rejectsUnsafeSchemesAndControlCharacters() {
        val javascriptCandidates = sourceUrlCandidates("javascript:alert(1)")
        val fileCandidates = sourceUrlCandidates("file:///sdcard/private.db")
        val contentCandidates = sourceUrlCandidates("content://com.android.contacts/contacts")
        val crlfCandidates = sourceUrlCandidates("https://example.com\r\nHost:evil.test")

        assertTrue(javascriptCandidates.isEmpty())
        assertTrue(fileCandidates.isEmpty())
        assertTrue(contentCandidates.isEmpty())
        assertTrue(crlfCandidates.isEmpty())
    }

    @Test
    fun normalizeConfiguredBaseUrl_rejectsCredentialedOrMalformedUrls() {
        assertEquals("", normalizeConfiguredBaseUrl("https://user:pass@example.com"))
        assertEquals("", normalizeConfiguredBaseUrl("https:///missing-host"))
        assertEquals("", normalizeConfiguredBaseUrl("http://exa mple.com"))
    }

    @Test
    fun parseResolutionPayload_rejectsUnsupportedStreamUrlScheme() {
        val payload = """{"streamUrl":"file:///data/local/tmp/secret.mp4"}"""
        val error = runCatching {
            parseResolutionPayload(payload, "https://example.com/watch?v=abc123")
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertTrue(error?.message?.contains("unsupported", ignoreCase = true) == true)
    }

    @Test
    fun parseResolutionPayload_dropsInjectedHeaderValues() {
        val payload = """
            {
              "streamUrl": "https://video.example.com/safe.m3u8",
              "requestHeaders": {
                "Accept": "*/*",
                "User-Agent": "safe\r\nX-Evil: yes",
                "X-Bad\r\nInjected": "value"
              }
            }
        """.trimIndent()

        val parsed = parseResolutionPayload(payload, "https://example.com/watch?v=abc123")

        assertEquals("*/*", parsed.requestHeaders["Accept"])
        assertFalse(parsed.requestHeaders.containsKey("User-Agent"))
        assertFalse(parsed.requestHeaders.keys.any { key -> key.contains("X-Bad") })
    }

    @Test
    fun parseDownloadPayload_readsSavedPathAndDiagnostics() {
        val payload = """
            {
              "id": "vid123",
              "title": "Sample Download",
              "pageUrl": "https://example.com/watch?v=vid123",
              "savedPath": "/data/user/0/com.whirlpool.app/files/downloads/sample-vid123.mp4",
              "savedName": "sample-vid123.mp4",
              "ytDlpVersion": "2026.02.01",
              "diagnostics": [
                "debug: first",
                "warning: second"
              ]
            }
        """.trimIndent()

        val parsed = parseDownloadPayload(payload, "https://fallback.invalid/watch?v=vid123")

        assertEquals("vid123", parsed.id)
        assertEquals("Sample Download", parsed.title)
        assertEquals("/data/user/0/com.whirlpool.app/files/downloads/sample-vid123.mp4", parsed.savedPath)
        assertEquals("sample-vid123.mp4", parsed.savedName)
        assertEquals("2026.02.01", parsed.ytDlpVersion)
        assertEquals(2, parsed.diagnostics.size)
    }

    @Test
    fun parseDownloadPayload_rejectsMissingSavedPath() {
        val payload = """{"id":"abc","savedName":"abc.mp4"}"""
        val error = runCatching {
            parseDownloadPayload(payload, "https://example.com/watch?v=abc")
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertTrue(error?.message?.contains("saved file path", ignoreCase = true) == true)
    }

    @Test
    fun downloadedVideoPreferenceKey_usesChannelAndId() {
        val first = downloadedVideoPreferenceKey("catflix", "video-1")
        val second = downloadedVideoPreferenceKey("dogflix", "video-1")
        val escaped = downloadedVideoPreferenceKey("cat flix", "id/with/slash")

        assertTrue(first != null && first.startsWith("downloads.video."))
        assertTrue(second != null && second.startsWith("downloads.video."))
        assertTrue(first != second)
        assertTrue(escaped?.contains("cat+flix") == true)
        assertTrue(escaped?.contains("id%2Fwith%2Fslash") == true)
        assertEquals(null, downloadedVideoPreferenceKey("", "video-1"))
        assertEquals(null, downloadedVideoPreferenceKey("catflix", ""))
    }
}
