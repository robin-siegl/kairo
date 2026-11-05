package dev.robinsiegl.kairo

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PaymentChangesTest {
    private val today = LocalDate.parse("2026-11-05")
    private val payment = Payment(name = "Example", terms = listOf(Terms("2026-01-31", "9.99", Interval.Monthly)))

    @Test
    fun scheduledPriceKeepsCurrentPriceAndOriginalBillingAnchor() {
        val changed = payment.schedulePrice("12.99", LocalDate.parse("2026-12-01"), today)
        assertEquals("9.99", changed.termsOn(today).amount)
        assertEquals(listOf("2026-11-30:9.99", "2026-12-31:12.99", "2027-01-31:12.99"),
            changed.charges(today, LocalDate.parse("2027-01-31")).map { "${it.date}:${it.amount}" }.toList())
    }

    @Test
    fun aTrialCanHaveANewPriceForItsFirstCharge() {
        val trial = payment.copy(terms = listOf(Terms("2026-11-13", "9.99", Interval.Monthly)), trial = true)
        val changed = trial.schedulePrice("12.99", LocalDate.parse("2026-11-13"), today)
        assertEquals("9.99", changed.termsOn(today).amount)
        assertEquals("12.99", changed.nextCharge(today)?.amount?.toPlainString())
        assertTrue(changed.isTrial(today))
    }

    @Test
    fun schedulingOutOfOrderAndReplacingADateKeepsAValidTimeline() {
        val december = payment.schedulePrice("12.99", LocalDate.parse("2026-12-01"), today)
        val november = december.schedulePrice("10.99", LocalDate.parse("2026-11-20"), today)
        val replaced = november.schedulePrice("11.99", LocalDate.parse("2026-11-20"), today)
        assertEquals(listOf("9.99", "11.99", "12.99"), replaced.terms.map { it.amount })
        assertEquals(replaced, decodeBook(encodeBook(Book(payments = listOf(replaced)))).payments.single())
    }

    @Test
    fun editingCurrentPriceOrScheduleKeepsScheduledPrices() {
        val scheduled = payment.schedulePrice("12.99", LocalDate.parse("2026-12-01"), today)
        val edited = scheduled.copy(terms = scheduled.updatedTerms(Terms("2026-01-31", "10.99", Interval.Monthly), today))
        assertEquals("10.99", edited.termsOn(today).amount)
        assertEquals("12.99", edited.termsOn(LocalDate.parse("2026-12-01")).amount)
        assertEquals("9.99", edited.termsOn(LocalDate.parse("2026-10-01")).amount)
        val weekly = edited.copy(terms = edited.updatedTerms(Terms("2026-01-31", "10.99", Interval.Weekly), today))
        assertEquals(Interval.Weekly, weekly.termsOn(LocalDate.parse("2026-12-01")).interval)
    }

    @Test
    fun priceChangesMustBeFutureAndWithinTheEndDate() {
        assertThrows(IllegalArgumentException::class.java) { payment.schedulePrice("12.99", today, today) }
        val ended = payment.copy(lastActiveDay = "2026-11-30")
        assertThrows(IllegalArgumentException::class.java) {
            ended.schedulePrice("12.99", LocalDate.parse("2026-12-01"), today)
        }
    }
}
