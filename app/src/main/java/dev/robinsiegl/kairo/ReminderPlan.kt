package dev.robinsiegl.kairo

import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.serialization.Serializable

internal data class ReminderEvent(
    val payment: Payment,
    val event: LocalDate,
    val trial: Boolean,
    val finalTrial: Boolean,
    val at: ZonedDateTime,
) {
    val key: String get() = "${payment.id}:$trial:$event" + if (finalTrial) ":followup" else ""
    val identity: String get() = "${payment.id}:$trial:$event"
    fun canSnooze(today: LocalDate): Boolean {
        val lastDay = if (trial && payment.cancelBy != null) event else event.minusDays(1)
        return today.plusDays(1) <= lastDay
    }
}

@Serializable
internal data class SnoozedReminder(val paymentId: String, val event: String, val trial: Boolean, val dueDate: String) {
    val identity: String get() = "$paymentId:$trial:$event"
}

internal fun reminderEvents(book: Book, now: ZonedDateTime): List<ReminderEvent> {
    val prefs = book.preferences
    val today = now.toLocalDate()
    return book.payments.flatMap { payment ->
        val trial = payment.isTrial(today)
        if ((trial && !prefs.trialReminders) || (!trial && !payment.remind)) return@flatMap emptyList()
        val dates = if (trial) listOfNotNull(payment.trialDeadline(today)?.takeIf { it >= today })
        else payment.nextCharge(today)?.let { first ->
            // A seven-day lead can overlap two weekly charges. Keep the following charge as well.
            payment.charges(first.date, first.date.plusYears(1)).take(3).map { it.date }.toList()
        }.orEmpty()
        val leads = if (trial && prefs.secondTrialReminder && prefs.trialLead != 1) listOf(prefs.trialLead, 1)
        else listOf(if (trial) prefs.trialLead else prefs.renewalLead)
        dates.flatMap { event ->
            leads.mapIndexed { index, lead ->
                ReminderEvent(payment, event, trial, trial && index > 0,
                    event.minusDays(lead.toLong()).atTime(prefs.reminderHour, prefs.reminderMinute).atZone(now.zone))
            }
        }
    }.sortedBy { it.at }
}

internal fun validSnoozes(snoozes: List<SnoozedReminder>, events: List<ReminderEvent>, today: LocalDate): List<SnoozedReminder> =
    snoozes.filter { snooze ->
        val due = runCatching { LocalDate.parse(snooze.dueDate) }.getOrNull()
        val event = events.firstOrNull { it.identity == snooze.identity }
        due != null && event != null && event.event >= today &&
            due <= if (event.trial && event.payment.cancelBy != null) event.event else event.event.minusDays(1)
    }
