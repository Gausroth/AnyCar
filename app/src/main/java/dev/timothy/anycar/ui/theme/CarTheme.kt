package dev.timothy.anycar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.timothy.anycar.car.DayNight
import dev.timothy.anycar.graph
import java.time.LocalTime
import kotlinx.coroutines.delay

private val Amber = Color(0xFFF5A524)

// Night: near-black with amber and warm white, so there's no glare in a dark cabin (M4.4).
private val NightColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color.Black,
    secondary = Color(0xFFF2EEE6),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFF2EEE6),
    surface = Color(0xFF17191C),
    onSurface = Color(0xFFF2EEE6),
    surfaceContainerLow = Color(0xFF17191C),
    outline = Color(0xFF8A8A8A),
)

// Day: white with black text, readable in direct sunlight. Primary passes 4.5:1 on white.
private val DayColors = lightColorScheme(
    primary = Color(0xFF7A4F00),
    onPrimary = Color.White,
    secondary = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color(0xFFF2F2F2),
    onSurface = Color.Black,
    surfaceContainerLow = Color(0xFFF2F2F2),
    outline = Color(0xFF5A5A5A),
)

/** Day and night themes for the app screens, following the phone's dark mode. */
@Composable
fun CarTheme(night: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (night) NightColors else DayColors, content = content)
}

/**
 * The car screen's theme: follows the day/night setting, re-checks the clock every minute in auto mode,
 * and clamps the system font scale at 1.3× so the fixed button grid can't break (M4.4).
 */
@Composable
fun CarScreenTheme(content: @Composable () -> Unit) {
    val pref by LocalContext.current.graph.settings.dayNightPref.collectAsStateWithLifecycle(initialValue = "auto")
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            hour = LocalTime.now().hour
        }
    }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.3f))) {
        CarTheme(night = DayNight.isNight(pref, hour), content = content)
    }
}
