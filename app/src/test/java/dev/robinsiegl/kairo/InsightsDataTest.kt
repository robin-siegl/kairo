package dev.robinsiegl.kairo

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class InsightsDataTest {
    private fun payment(date: String, amount: String, interval: Interval, category: String = "") =
        Payment(name = "Example", terms = listOf(Terms(date, amount, interval)), category = category)

    @Test
    fun monthlyBreakdownCountsActualOccurrencesAndUncategorizedCharges() {
        val book = Book(payments = listOf(
            payment("2026-05-01", "10.00", Interval.Fortnightly, "Health"),
            payment("2026-05-15", "120.00", Interval.Yearly, "Fun"),
            payment("2026-05-20", "5.00", Interval.Once),
        ))
        val summary = book.spending(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-31"))
        assertEquals(BigDecimal("155.00"), summary.total)
        assertEquals(BigDecimal("30.00"), summary.byCategory["Health"])
        assertEquals(BigDecimal("120.00"), summary.byCategory["Fun"])
        assertEquals(BigDecimal("5.00"), summary.byCategory[""])
    }

    @Test
    fun forecastUsesHistoricTermsAndInclusiveEndDates() {
        val item = payment("2026-01-31", "10.00", Interval.Monthly).copy(
            terms = listOf(Terms("2026-01-31", "10.00", Interval.Monthly),
                Terms("2026-01-31", "20.00", Interval.Monthly, "2026-03-01")),
            lastActiveDay = "2026-03-31", cancelled = true,
        )
        val forecast = Book(payments = listOf(item)).yearForecast(YearMonth.of(2026, 1))
        assertEquals(listOf("10.00", "10.00", "20.00", "0"), forecast.take(4).map { it.second.toPlainString() })
        assertEquals(12, forecast.size)
        assertEquals(YearMonth.of(2026, 12), forecast.last().first)
    }

    @Test
    fun monthlyAverageSpreadsAnAnnualBillAcrossTwelveMonths() {
        assertEquals(BigDecimal("10.00"), monthlyAverage(BigDecimal("120.00"), 2))
        assertEquals(BigDecimal("8.33"), monthlyAverage(BigDecimal("100.00"), 2))
        assertEquals(BigDecimal("8"), monthlyAverage(BigDecimal("100"), 0))
    }

    @Test
    fun renameAndMergeKeepAmountsAndEndedPayments() {
        val first = payment("2026-01-01", "10.00", Interval.Monthly, "Old")
        val ended = first.copy(id = "ended", cancelled = true, lastActiveDay = "2026-02-01")
        val book = Book(payments = listOf(first, ended), categories = listOf(Category("Other", "Teal")))
        val renamed = book.saveCategory("Old", Category("New", "Rose"))
        assertTrue(renamed.payments.all { it.category == "New" })
        assertEquals(first.terms, renamed.payments.first().terms)
        val merged = renamed.mergeCategory("New", "Other")
        assertTrue(merged.payments.all { it.category == "Other" })
        assertEquals(listOf(Category("Other", "Teal")), merged.allCategories())
    }

    @Test
    fun legacyCategoriesAndUnusedCategoriesSurviveBackupAndMerge() {
        val old = decodeBook("""{"payments":[{"id":"old","name":"Example","category":"Health",
            "terms":[{"firstCharge":"2026-01-01","amount":"10.00","interval":"Monthly"}]}]}""")
        assertEquals(listOf(Category("Health")), old.allCategories())
        val local = old.saveCategory("Health", Category("Health", "Rose"))
        val incoming = Book(categories = listOf(Category("Health", "Teal"), Category("Unused", "Purple")))
        val merged = local.merge(incoming).book
        assertEquals("Rose", merged.allCategories().first { it.name == "Health" }.color)
        assertTrue(merged.allCategories().contains(Category("Unused", "Purple")))
        assertEquals(merged, decodeBook(encodeBook(merged)))
        assertEquals(merged, merged.merge(incoming).book)
    }

    @Test
    fun duplicateNamesCannotOverwriteAnotherCategory() {
        val book = Book(categories = listOf(Category("Health"), Category("Fun")))
        assertThrows(IllegalArgumentException::class.java) { book.saveCategory("Fun", Category("health")) }
    }
}
