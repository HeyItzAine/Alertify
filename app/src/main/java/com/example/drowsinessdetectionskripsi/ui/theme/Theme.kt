package com.example.drowsinessdetectionskripsi.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = ColorPrimary,
    secondary = ColorAccent,
    tertiary = ColorSuccess,
    background = ColorBg,
    surface = ColorCard,
    onPrimary = ColorText,
    onSecondary = ColorBg,
    onTertiary = ColorBg,
    onBackground = ColorText,
    onSurface = ColorText,
    error = ColorDanger,
)

@Composable
fun DrowsinessDetectionSkripsiTheme(
    lightMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val colors = if (lightMode) lightColorScheme(
        primary = Color(0xFF4657D8),
        secondary = Color(0xFF007F91),
        background = Color.White,
        surface = Color(0xFFF1F2F4),
        surfaceVariant = Color(0xFFE5E7EB),
        onBackground = Color(0xFF172033),
        onSurface = Color(0xFF172033),
        onSurfaceVariant = Color(0xFF333844),
        error = Color(0xFFB42332)
    ) else DarkColorScheme
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = lightMode
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
