package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = RangoLimeGreen,
    onPrimary = Color.Black,
    secondary = RangoDesertGold,
    onSecondary = Color.Black,
    tertiary = RangoSandWarm,
    background = RangoTealSky,
    surface = RangoCardBg,
    onBackground = RangoTextWhite,
    onSurface = RangoTextWhite,
    surfaceVariant = RangoHorizon,
    onSurfaceVariant = RangoTextMuted,
    error = RangoDangerRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = RangoLimeGreen,
    onPrimary = Color.Black,
    secondary = RangoDesertGold,
    tertiary = RangoSandWarm,
    background = Color(0xFFA6E3FF), // Day sky theme
    surface = Color.White,
    onBackground = Color(0xFF0F2633),
    onSurface = Color(0xFF0F2633),
    error = RangoDangerRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // We force the gorgeous Rango dark-sky dashboard look by default
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
