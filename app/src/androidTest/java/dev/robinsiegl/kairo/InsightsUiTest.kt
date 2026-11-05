package dev.robinsiegl.kairo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsDisplayed
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class InsightsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun categoryCanBeCreatedWithoutAnExistingPayment() {
        val state = mutableStateOf(Book())
        compose.setContent {
            MaterialTheme {
                CategoriesScreen(state.value, LocalDate.parse("2026-05-01"), {}, { state.value = it; true })
            }
        }
        compose.onNodeWithText("Add category").performClick()
        compose.onNodeWithText("Category name").performTextInput("Health")
        compose.onNodeWithText("Save category").performClick()
        compose.onNodeWithText("Health").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(Category("Health")), state.value.categories) }
    }

    @Test
    fun insightsShowsAnEmptyMonthWithoutHidingCategoryManagement() {
        compose.setContent {
            MaterialTheme {
                InsightsScreen(Book(), LocalDate.parse("2026-05-01"), YearMonth.of(2026, 5), {}, {}, {})
            }
        }
        compose.onNodeWithText("Scheduled this month").assertIsDisplayed()
        compose.onNodeWithText("Manage").assertIsDisplayed()
    }
    @Test
    fun sharedCategoryEditorRenamesAndRecolorsTheOpenCategory() {
        val state = mutableStateOf(Book(
            categories = listOf(Category("Health")),
            payments = listOf(Payment(name = "Gym", category = "Health",
                terms = listOf(Terms("2026-05-01", "10.00", Interval.Monthly)))),
        ))
        val category = mutableStateOf("Health")
        val editing = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                if (editing.value) CategoryEditor(
                    state.value, category.value,
                    save = { updated, destination -> state.value = updated; category.value = destination; true },
                    dismiss = { editing.value = false },
                )
            }
        }
        compose.onNodeWithText("Category name").performTextReplacement("Fitness")
        compose.onNodeWithText("Teal").performClick()
        compose.onNodeWithText("Save category").performClick()
        compose.runOnIdle {
            assertEquals("Fitness", category.value)
            assertEquals("Fitness", state.value.payments.single().category)
            assertEquals(Category("Fitness", "Teal"), state.value.categories.single())
            assertEquals(false, editing.value)
        }
    }

}
