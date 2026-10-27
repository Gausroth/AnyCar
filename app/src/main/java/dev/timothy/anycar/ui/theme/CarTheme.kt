package dev.timothy.anycar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Amber = Color(0xFFF5A524)

private val NightColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color.Black,
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFF2EEE6),
    surface = Color(0xFF1A1E24),
    onSurface = Color(0xFFF2EEE6),
)

private val DayColors = lightColorScheme(
    primary = Color(0xFF8A5A00),
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color(0xFFF2F2F2),
    onSurface = Color.Black,
)

/** Day and night themes for the app and the car screen. Typography minimums arrive with the car UI (M4). */
@Composable
fun CarTheme(night: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (night) NightColors else DayColors, content = content)
}
