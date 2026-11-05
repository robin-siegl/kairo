package dev.robinsiegl.kairo

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PaymentChangesUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun schedulingAPriceKeepsTheCurrentPriceAndChangesTheNextCharge() {
        val today = LocalDate.parse("2026-11-05")
        val state = mutableStateOf(Payment(name = "Example",
            terms = listOf(Terms("2026-01-01", "9.99", Interval.Monthly))))
        compose.setContent {
            MaterialTheme {
                Column { PaymentPriceChanges(state.value, today, "EUR") { state.value = it; true } }
            }
        }
        compose.onNodeWithText("Schedule price change").performClick()
        compose.onNodeWithText("New amount").performTextReplacement("12.99")
        compose.onNodeWithText("Save price change").performClick()
        compose.runOnIdle {
            assertEquals("9.99", state.value.termsOn(today).amount)
            assertEquals("12.99", state.value.nextCharge(today)?.amount?.toPlainString())
        }
    }
}
