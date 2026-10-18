package com.cyd.timemaster.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E88E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E7FF),
    secondary = Color(0xFF00897B),
    surface = Color(0xFFFDFDFD),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AC7FF),
    primaryContainer = Color(0xFF1B4B7A),
    secondary = Color(0xFF4DB6AC),
)

@Composable
fun TimeMasterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
