package com.whirlpool.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightPalette = lightColorScheme(
    primary = ElectricBlue,
    secondary = Violet,
    tertiary = OrangeGlow,
    background = AppBackground,
    surface = SurfaceCard,
    onBackground = Ink,
)

private val DarkPalette = darkColorScheme(
    primary = ElectricBlue,
    secondary = Violet,
    tertiary = OrangeGlow,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkInk,
)

@Composable
fun WhirlpoolTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkPalette else LightPalette,
        typography = Typography,
        content = content,
    )
}
