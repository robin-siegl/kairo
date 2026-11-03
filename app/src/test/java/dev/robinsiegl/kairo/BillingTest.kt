package dev.robinsiegl.kairo

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillingTest {
    @Test
    fun trialDeadlineAndFirstChargeAreIndependent() {
        val today = LocalDate.parse("2026-11-10")
        val trial = payment("2026-11-13", Interval.Monthly).copy(trial = true, cancelBy = "2026-11-11")
        assertEquals(LocalDate.parse("2026-11-11"), trial.trialDeadline(today))
        assertEquals(LocalDate.parse("2026-11-13"), trial.nextCharge(today)?.date)

        val cancelled = trial.copy(cancelled = true, lastActiveDay = "2026-11-12")
        assertNull(cancelled.trialDeadline(today))
        assertNull(cancelled.nextCharge(today))
    }

    @Test
    fun monthlyScheduleReturnsToOriginalAnchor() {
        val payment = payment("2026-01-31", Interval.Monthly)
        assertEquals(listOf("2026-02-28", "2026-03-31"), dates(payment, "2026-02-01", "2026-03-31"))
    }

    @Test
    fun leapDayReturnsAndLastActiveDayIsInclusive() {
        val payment = payment("2024-02-29", Interval.Yearly).copy(lastActiveDay = "2028-02-29")
        assertEquals(listOf("2027-02-28", "2028-02-29"), dates(payment, "2027-01-01", "2029-12-31"))
    }

    @Test
    fun fortnightlyScheduleCanChargeThreeTimesInOneMonth() {
        val payment = payment("2026-05-01", Interval.Fortnightly)
        assertEquals(listOf("2026-05-01", "2026-05-15", "2026-05-29"), dates(payment, "2026-05-01", "2026-05-31"))
    }

    @Test
    fun effectiveDateKeepsPastAmounts() {
        val payment = payment("2026-01-10", Interval.Monthly)
        val changed = payment.copy(terms = payment.terms + payment.current.copy(
            amount = "20.00", effectiveFrom = "2026-03-01",
        ))
        val amounts = changed.charges(LocalDate.parse("2026-02-01"), LocalDate.parse("2026-03-31"))
            .map { it.amount.toPlainString() }.toList()
        assertEquals(listOf("9.99", "20.00"), amounts)
    }

    @Test
    fun repeatedImportKeepsLocalEditsAndDoesNotDuplicate() {
        val local = payment("2026-05-01", Interval.Monthly)
        val added = payment("2026-05-01", Interval.Weekly)
        val incoming = Book(payments = listOf(local.copy(name = "Edited elsewhere"), added))
        val merge = Book(payments = listOf(local)).merge(incoming)
        assertEquals(1, merge.conflicts)
        assertEquals(listOf(local, added), merge.book.payments)
        assertEquals(0, merge.book.merge(incoming).added)
    }

    private fun payment(start: String, interval: Interval) =
        Payment(name = "Example", terms = listOf(Terms(start, "9.99", "EUR", interval)))

    private fun dates(payment: Payment, from: String, through: String) =
        payment.charges(LocalDate.parse(from), LocalDate.parse(through)).map { it.date.toString() }.toList()
}
