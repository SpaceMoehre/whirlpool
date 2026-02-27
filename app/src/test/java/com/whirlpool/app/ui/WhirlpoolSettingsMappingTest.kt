package com.whirlpool.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhirlpoolSettingsMappingTest {
    @Test
    fun applySetting_updatesBooleanAndTextValues() {
        val base = AppSettings()
        val withHaptics = applySetting(base, SettingKeys.ENABLE_HAPTICS, "true")
        val withTheme = applySetting(withHaptics, SettingKeys.THEME, "Light")

        assertTrue(withTheme.enableHaptics)
        assertEquals("Light", withTheme.theme)
    }

    @Test
    fun applySetting_keepsCurrentOnInvalidBoolean() {
        val base = AppSettings(enableAnalytics = true)
        val updated = applySetting(base, SettingKeys.PRIVACY_ENABLE_ANALYTICS, "not-a-bool")

        assertTrue(updated.enableAnalytics)
    }

    @Test
    fun encodeDecodeStringList_roundtrip() {
        val raw = listOf("cats", "dogs", "  birds  ")
        val encoded = encodeStringList(raw)
        val decoded = decodeStringList(encoded)

        assertEquals(listOf("cats", "dogs", "birds"), decoded)
    }

    @Test
    fun encodeDecodeFilterSelection_roundtrip() {
        val selection = linkedMapOf(
            "sort" to setOf("latest"),
            "duration" to emptySet(),
            "format option" to setOf("mp4", "webm"),
        )

        val encoded = encodeFilterSelection(selection)
        val decoded = decodeFilterSelection(encoded)

        assertEquals(
            linkedMapOf(
                "duration" to emptySet(),
                "format option" to setOf("mp4", "webm"),
                "sort" to setOf("latest"),
            ),
            decoded,
        )
    }

    @Test
    fun decodeFilterSelection_invalidPayload_returnsBestEffort() {
        val decoded = decodeFilterSelection("sort=latest&&bad%zz=oops&duration=")

        assertEquals(setOf("latest"), decoded["sort"])
        assertEquals(emptySet<String>(), decoded["duration"])
    }
}
