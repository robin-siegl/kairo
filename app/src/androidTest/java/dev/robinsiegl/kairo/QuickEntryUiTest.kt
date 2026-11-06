package dev.robinsiegl.kairo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QuickEntryUiTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.parse("2026-11-06")

    @Test
    fun choosingAServiceFillsTheDraftAndSavesOnlyAfterEnteringAPrice() {
        var saved: Payment? = null
        compose.setContent {
            var draft by remember { mutableStateOf<PaymentDraft?>(null) }
            MaterialTheme {
                if (draft == null) ServicePicker { draft = it?.draft(today) }
                else PaymentEditor(null, Book(), today, draft) { saved = it }
            }
        }
        compose.onNodeWithText("Search services").performTextInput("Spotify")
        compose.onNodeWithText("Spotify").performClick()
        compose.onNodeWithText("Payment name").assertTextContains("Spotify")
        compose.onNodeWithText("Save payment").performScrollTo().performClick()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithText("Amount").performScrollTo().performTextInput("12.99")
        compose.onNodeWithText("Save payment").performScrollTo().performClick()
        compose.runOnIdle {
            val payment = requireNotNull(saved)
            assertEquals("Spotify", payment.name)
            assertEquals("12.99", payment.terms.single().amount)
            assertEquals("https://www.spotify.com/account/", payment.manageLink)
            assertFalse(payment.trial)
        }
    }

    @Test
    fun duplicateCanBeSavedAsAnIndependentPaymentWithoutOldCancellationOrPrices() {
        val original = Payment(name = "Example", trial = true, cancelBy = "2026-11-07", cancelled = true,
            lastActiveDay = "2026-11-08", remind = true, terms = listOf(Terms("2026-11-10", "9.99", Interval.Monthly)))
        var saved: Payment? = null
        compose.setContent {
            MaterialTheme { PaymentEditor(null, Book(), today, original.duplicateDraft(today)) { saved = it } }
        }
        compose.onNodeWithText("Save payment").performScrollTo().performClick()
        compose.runOnIdle {
            val duplicate = requireNotNull(saved)
            assertNotEquals(original.id, duplicate.id)
            assertEquals("Example copy", duplicate.name)
            assertFalse(duplicate.trial)
            assertFalse(duplicate.cancelled)
            assertFalse(duplicate.remind)
            assertNull(duplicate.cancelBy)
            assertNull(duplicate.lastActiveDay)
            assertEquals(1, duplicate.terms.size)
        }
    }
}
