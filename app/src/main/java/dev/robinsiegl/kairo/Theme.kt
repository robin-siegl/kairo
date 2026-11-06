package dev.robinsiegl.kairo

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun KairoTheme(preferences: Preferences, content: @Composable () -> Unit) {
    val dark = when (preferences.theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val colors = if (preferences.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme(
        primary = Color(0xFFADC6FF),
        onPrimary = Color(0xFF002E6A),
        primaryContainer = Color(0xFF1B449C),
        onPrimaryContainer = Color(0xFFD8E5FF),
        secondaryContainer = Color(0xFF3C4665),
        onSecondaryContainer = Color(0xFFDDE3FF),
    ) else lightColorScheme(
        primary = Color(0xFF3367DB),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD8E5FF),
        onPrimaryContainer = Color(0xFF101B37),
        secondaryContainer = Color(0xFFE1E7F7),
        onSecondaryContainer = Color(0xFF242F4A),
    )
    MaterialTheme(colorScheme = colors, content = content)
}
