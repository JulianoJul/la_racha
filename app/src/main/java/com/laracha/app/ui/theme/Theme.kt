package com.laracha.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import com.laracha.app.data.AppPreferences
import com.laracha.app.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF437A54),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBDD),
    onPrimaryContainer = Color(0xFF163720),
    secondary = Color(0xFF52685A),
    secondaryContainer = Color(0xFFE3EAE2),
    onSecondaryContainer = Color(0xFF26382B),
    tertiary = Color(0xFF52685A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE3EAE2),
    onTertiaryContainer = Color(0xFF26382B),
    background = Color(0xFFF4F5F1),
    onBackground = Color(0xFF26322A),
    surface = Color.White,
    onSurface = Color(0xFF26322A),
    surfaceVariant = Color(0xFFE8ECE6),
    onSurfaceVariant = Color(0xFF526056),
    outline = Color(0xFFC7CDC5),
    error = Color(0xFFB34741),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF92D9A5),
    onPrimary = Color(0xFF163720),
    primaryContainer = Color(0xFF31543A),
    onPrimaryContainer = Color(0xFFDCEBDD),
    secondary = Color(0xFFB6CDBB),
    secondaryContainer = Color(0xFF35483A),
    onSecondaryContainer = Color(0xFFE3EAE2),
    tertiary = Color(0xFFB6CDBB),
    onTertiary = Color(0xFF26382B),
    tertiaryContainer = Color(0xFF35483A),
    onTertiaryContainer = Color(0xFFE3EAE2),
    background = Color(0xFF19241C),
    onBackground = Color(0xFFF4F7F2),
    surface = Color(0xFF26392D),
    onSurface = Color(0xFFF4F7F2),
    surfaceVariant = Color(0xFF35483A),
    onSurfaceVariant = Color(0xFFB6CDBB),
    outline = Color(0xFF64776B),
    error = Color(0xFFFFA19A),
)

@Composable
fun HabitTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val view = LocalView.current
    val background = if (darkTheme) DarkColors.background else LightColors.background
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = background.toArgb()
            window.navigationBarColor = background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

@Composable
fun HabitAppTheme(preferences: AppPreferences, content: @Composable () -> Unit) {
    val mode by preferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    HabitTheme(darkTheme = darkTheme, content = content)
}
