package com.whirlpool.app.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightPalette = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    secondary = Violet,
    onSecondary = Color.White,
    tertiary = OrangeGlow,
    onTertiary = Color.Black,
    background = AppBackground,
    onBackground = Ink,
    surface = SurfaceCard,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE8E7EE),
    onSurfaceVariant = Color(0xFF5E5D66),
    outline = Color(0xFF7A7884),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkPalette = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.Black,
    secondary = Violet,
    onSecondary = Color.White,
    tertiary = OrangeGlow,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = Color(0xFF26262C),
    onSurfaceVariant = Color(0xFFB4B7C0),
    outline = Color(0xFF8B8D98),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun WhirlpoolTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkPalette else LightPalette
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
