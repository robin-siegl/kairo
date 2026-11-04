package dev.robinsiegl.kairo

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDataTest {
    private val today = LocalDate.parse("2026-05-14")
    private fun payment(date: String, interval: Interval = Interval.Once) =
        Payment(name = "Example", terms = listOf(Terms(date, "10.00", interval)))

    @Test
    fun upcomingWindowIncludesTodayAndItsFinalDay() {
        val book = Book(payments = listOf(payment("2026-05-14"), payment("2026-05-20"), payment("2026-05-21")))
        assertEquals(2, book.widgetEntries(WidgetKind.Upcoming, today, 7).size)
    }

    @Test
    fun trialsUseCancellationDeadlineAndSkipCancelledEntries() {
        val trial = payment("2026-05-22").copy(trial = true, cancelBy = "2026-05-19")
        val book = Book(payments = listOf(trial, trial.copy(id = "cancelled", cancelled = true)))
        assertEquals(listOf(LocalDate.parse("2026-05-19")), book.widgetEntries(WidgetKind.Trials, today, 14).map { it.date })
    }

    @Test
    fun nextPaymentCanBeOutsideTheUpcomingWindow() {
        val book = Book(payments = listOf(payment("2027-01-01")))
        assertTrue(book.widgetEntries(WidgetKind.Upcoming, today, 14).isEmpty())
        assertEquals(LocalDate.parse("2027-01-01"), book.widgetEntries(WidgetKind.Next, today, 14).single().date)
    }

    @Test
    fun monthTotalsCountEachOccurrenceAndRespectCancellationEnd() {
        val recurring = payment("2026-05-01", Interval.Fortnightly)
        val book = Book(payments = listOf(recurring))
        val entries = book.widgetEntries(WidgetKind.Month, today, 14)
        assertEquals(BigDecimal("30.00"), entries.sumOf { it.amount!! })
        assertEquals(BigDecimal("20.00"), entries.filter { it.date >= today }.sumOf { it.amount!! })
        val cancelled = Book(payments = listOf(recurring.copy(cancelled = true, lastActiveDay = "2026-05-14")))
        assertEquals(1, cancelled.widgetEntries(WidgetKind.Month, today, 14).size)
    }
}
