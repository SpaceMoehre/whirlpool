package com.whirlpool.app.theme

import androidx.compose.material3.MaterialTheme
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

@Composable
fun WhirlpoolTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightPalette,
        typography = Typography,
        content = content,
    )
}
