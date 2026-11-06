package dev.robinsiegl.kairo

import java.time.LocalDate
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class ReminderPlanTest {
    private val now = ZonedDateTime.parse("2026-11-05T10:00:00+01:00[Europe/Vienna]")
    private val trial = Payment(name = "Trial",
        terms = listOf(Terms("2026-11-13", "9.99", Interval.Monthly)), trial = true, cancelBy = "2026-11-11")

    @Test
    fun secondTrialReminderUsesCancellationDeadline() {
        val events = reminderEvents(Book(payments = listOf(trial),
            preferences = Preferences(secondTrialReminder = true)), now)
        assertEquals(listOf("2026-11-08", "2026-11-10"), events.map { it.at.toLocalDate().toString() })
        assertEquals("${trial.id}:true:2026-11-11", events.first().key)
        assertTrue(events.last().key.endsWith(":followup"))
    }

    @Test
    fun aOneDayLeadDoesNotCreateDuplicateTrialReminders() {
        val events = reminderEvents(Book(payments = listOf(trial),
            preferences = Preferences(trialLead = 1, secondTrialReminder = true)), now)
        assertEquals(1, events.size)
    }

    @Test
    fun tomorrowActionStopsBeforeRenewalButAllowsTheCancellationDay() {
        val tomorrow = now.toLocalDate().plusDays(1)
        val regular = trial.copy(trial = false, remind = true,
            terms = listOf(Terms(tomorrow.toString(), "9.99", Interval.Monthly)))
        assertFalse(reminderEvents(Book(payments = listOf(regular)), now).first().canSnooze(now.toLocalDate()))
        val withDeadline = trial.copy(cancelBy = tomorrow.toString())
        assertTrue(reminderEvents(Book(payments = listOf(withDeadline)), now).first().canSnooze(now.toLocalDate()))
        val withoutDeadline = regular.copy(trial = true, cancelBy = null)
        assertFalse(reminderEvents(Book(payments = listOf(withoutDeadline)), now).first().canSnooze(now.toLocalDate()))
    }

    @Test
    fun editedOrCancelledEventsDropStaleSnoozesButUnexpiredLateSnoozesCatchUp() {
        val late = now.plusDays(5)
        val snooze = SnoozedReminder(trial.id, "2026-11-11", true, "2026-11-09")
        val book = Book(payments = listOf(trial))
        assertEquals(listOf(snooze), validSnoozes(listOf(snooze), reminderEvents(book, late), late.toLocalDate()))
        val cancelled = book.copy(payments = listOf(trial.copy(cancelled = true)))
        assertTrue(validSnoozes(listOf(snooze), reminderEvents(cancelled, late), late.toLocalDate()).isEmpty())
        val edited = book.copy(payments = listOf(trial.copy(cancelBy = "2026-11-12")))
        assertTrue(validSnoozes(listOf(snooze), reminderEvents(edited, late), late.toLocalDate()).isEmpty())
    }

    @Test
    fun renewalPlanningIncludesTheNextUnsentWeeklyCharge() {
        val regular = trial.copy(trial = false, remind = true,
            terms = listOf(Terms("2026-11-05", "9.99", Interval.Weekly)))
        val events = reminderEvents(Book(payments = listOf(regular), preferences = Preferences(renewalLead = 7)), now)
        assertEquals(listOf("2026-11-05", "2026-11-12", "2026-11-19"), events.map { it.event.toString() })
        assertTrue(events.last().at > now)
    }

    @Test
    fun remindersFollowLocalTimeAcrossDaylightSaving() {
        val spring = ZonedDateTime.parse("2027-03-27T10:00:00+01:00[Europe/Vienna]")
        val payment = trial.copy(cancelBy = "2027-03-31",
            terms = listOf(Terms("2027-04-02", "9.99", Interval.Monthly)))
        val event = reminderEvents(Book(payments = listOf(payment)), spring).first()
        assertEquals(LocalDate.parse("2027-03-28"), event.at.toLocalDate())
        assertEquals(9, event.at.hour)
        assertEquals(2 * 3600, event.at.offset.totalSeconds)
    }
}
