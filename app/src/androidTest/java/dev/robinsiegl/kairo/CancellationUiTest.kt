package dev.robinsiegl.kairo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class CancellationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun cancellingTrialPrefillsDayBeforeFirstChargeAndRemovesIt() {
        val state = mutableStateOf(Payment(
            name = "Focus Plus",
            terms = listOf(Terms("2026-11-13", "9.99", Interval.Monthly)),
            trial = true,
            cancelBy = "2026-11-11",
        ))
        val today = LocalDate.parse("2026-11-10")
        compose.setContent {
            MaterialTheme {
                PaymentDetails(state.value, today, "EUR", { state.value = it; true }, {}, { false })
            }
        }
        compose.onNodeWithText("Mark as cancelled").performClick()
        compose.onNodeWithText("${formatDate(today.plusDays(2))}").assertIsDisplayed()
        compose.onNodeWithText("Save cancellation").performClick()
        compose.onNodeWithText("Next charge", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Cancelled. Ends ${formatDate(today.plusDays(2))}").assertIsDisplayed()
    }
}
