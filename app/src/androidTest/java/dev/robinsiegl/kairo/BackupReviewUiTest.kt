package dev.robinsiegl.kairo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackupReviewUiTest {
    @get:Rule val compose = createComposeRule()
    private val payment = Payment(id = "one", name = "Example", terms = listOf(Terms("2026-01-01", "9.99", Interval.Monthly)))

    @Test
    fun verifyingAFileCannotMergeIt() {
        var saved = false
        var dismissed = false
        compose.setContent {
            MaterialTheme { BackupReviewDialog(Book(), Book(payments = listOf(payment)), true, { dismissed = true }) { saved = true; true } }
        }
        compose.onNodeWithText("Backup verified").assertIsDisplayed()
        compose.onNodeWithText("Merge backup").assertDoesNotExist()
        compose.onNodeWithText("Close").performClick()
        compose.runOnIdle { assertTrue(dismissed); assertFalse(saved) }
    }

    @Test
    fun conflictingCurrenciesDoNotOfferAMergeButton() {
        var saved = false
        val incoming = Book(payments = listOf(payment.copy(id = "two")), preferences = Preferences(defaultCurrency = "USD"))
        compose.setContent {
            MaterialTheme { BackupReviewDialog(Book(payments = listOf(payment)), incoming, false, {}) { saved = true; true } }
        }
        compose.onNodeWithText("Currencies cannot be combined.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Merge backup").assertDoesNotExist()
        compose.runOnIdle { assertFalse(saved) }
    }
}
