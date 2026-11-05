package dev.robinsiegl.kairo

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class CategoryNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun withCategory(action: () -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = Storage(context)
        val original = storage.read()
        val book = Book(
            categories = listOf(Category("Health")),
            payments = listOf(Payment(
                name = "Gym", category = "Health",
                terms = listOf(Terms(LocalDate.now().toString(), "10.00", Interval.Monthly)),
            )),
        )
        try {
            storage.write(book)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
                compose.onNodeWithText("Payments").performClick()
                action()
            }
        } finally {
            storage.write(original)
        }
    }

    private fun removeCategory() {
        compose.onNodeWithContentDescription("Edit category").performClick()
        compose.onNodeWithText("Remove category").performClick()
        compose.onNodeWithText("Remove").performClick()
    }

    @Test
    fun removingCategoryReturnsToCategoryManagement() = withCategory {
        compose.onNodeWithText("Manage categories").performClick()
        compose.onNodeWithText("Health").performClick()
        removeCategory()
        compose.onNodeWithText("Categories").assertIsDisplayed()
        compose.onNodeWithText("Add category").assertIsDisplayed()
    }

    @Test
    fun removingCategoryReturnsToThePaymentThatOpenedIt() = withCategory {
        compose.onNodeWithText("Gym").performClick()
        compose.onNodeWithText("Health").performClick()
        removeCategory()
        compose.onNodeWithText("Gym").assertIsDisplayed()
        compose.onNodeWithText("Next charge", substring = true).assertIsDisplayed()
    }
}
