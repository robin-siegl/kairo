package dev.robinsiegl.kairo

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppearanceUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun explicitThemesUpdateImmediatelyAndTurningOffDeviceColorsRestoresBrandBlue() {
        val prefs = mutableStateOf(Preferences(theme = ThemeMode.Light))
        var primary = Color.Unspecified
        compose.setContent {
            KairoTheme(prefs.value) {
                primary = MaterialTheme.colorScheme.primary
                Surface {}
            }
        }
        compose.runOnIdle { assertEquals(Color(0xFF3367DB), primary); prefs.value = prefs.value.copy(theme = ThemeMode.Dark) }
        compose.runOnIdle { assertEquals(Color(0xFFADC6FF), primary); prefs.value = prefs.value.copy(dynamicColor = true) }
        compose.runOnIdle { prefs.value = prefs.value.copy(dynamicColor = false, theme = ThemeMode.Light) }
        compose.runOnIdle { assertEquals(Color(0xFF3367DB), primary) }
    }
}
