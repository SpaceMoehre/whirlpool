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
}
