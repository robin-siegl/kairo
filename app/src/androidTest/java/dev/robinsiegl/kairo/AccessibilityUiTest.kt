package dev.robinsiegl.kairo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AccessibilityUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun formSwitchesHaveTheirLabelAndCanBeChangedByTappingTheText() {
        compose.setContent { MaterialTheme { PaymentEditor(null, Book(), LocalDate.parse("2026-11-10")) {} } }
        compose.onNodeWithText("Currently on a free trial").performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("First paid charge").assertIsDisplayed()
    }

    @Test
    fun settingsSwitchesExposeTheirLabelsAndToggleState() {
        val prefs = mutableStateOf(Preferences())
        compose.setContent {
            MaterialTheme { PreferencesScreen(prefs.value, true, { prefs.value = it }, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("Remind me about trials").performScrollTo().assertIsOn().performClick().assertIsOff()
        compose.runOnIdle { assertFalse(prefs.value.trialReminders) }
    }

    @Test
    fun emptyRequiredFieldsHaveInlineErrorsAndFocusMovesOnlyWhenSaving() {
        compose.setContent { MaterialTheme { PaymentEditor(null, Book(), LocalDate.parse("2026-11-10")) {} } }
        compose.onNodeWithText("Save payment").performScrollTo().performClick()
        compose.onNodeWithText("Payment name").assertIsFocused()
        compose.onNodeWithText("Enter a payment name.").assertIsDisplayed()
        compose.onNodeWithText("Payment name").performTextInput("Example")
        compose.onNodeWithText("Payment name").assertIsFocused()
        compose.onNodeWithText("Save payment").performScrollTo().performClick()
        compose.onNodeWithText("Amount").assertIsFocused()
        compose.onNodeWithText("Enter a valid amount for EUR.").assertIsDisplayed()
    }
}
