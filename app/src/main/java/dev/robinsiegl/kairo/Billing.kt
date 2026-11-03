package dev.robinsiegl.kairo

import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
enum class Interval(val label: String, val days: Long = 0, val months: Long = 0) {
    Once("One-time"),
    Weekly("Weekly", days = 7),
    Fortnightly("Every 2 weeks", days = 14),
    Monthly("Monthly", months = 1),
    Bimonthly("Every 2 months", months = 2),
    Quarterly("Quarterly", months = 3),
    Semiannual("Every 6 months", months = 6),
    Yearly("Yearly", months = 12),
}

@Serializable
data class Terms(
    val firstCharge: String,
    val amount: String,
    val currency: String,
    val interval: Interval,
    val effectiveFrom: String = firstCharge,
) {
    val anchor get() = LocalDate.parse(firstCharge)
    val effectiveDate get() = LocalDate.parse(effectiveFrom)
}

@Serializable
data class Payment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val terms: List<Terms>,
    val category: String = "",
    val tags: List<String> = emptyList(),
    val icon: String = "Payment",
    val image: String? = null,
    val notes: String = "",
    val manageLink: String = "",
    val trial: Boolean = false,
    val cancelBy: String? = null,
    val lastActiveDay: String? = null,
    val cancelled: Boolean = false,
    val remind: Boolean = false,
) {
    val current get() = terms.last()
    val end get() = lastActiveDay?.let(LocalDate::parse)

    fun isTrial(today: LocalDate) = trial && !cancelled && today <= current.anchor &&
        (end == null || today <= end)

    fun trialDeadline(today: LocalDate): LocalDate? =
        if (isTrial(today)) cancelBy?.let(LocalDate::parse) ?: current.anchor else null
}

data class Charge(val payment: Payment, val date: LocalDate, val terms: Terms) {
    val amount: BigDecimal get() = terms.amount.toBigDecimal()
}

fun Payment.charges(from: LocalDate, through: LocalDate): Sequence<Charge> =
    terms.asSequence().flatMapIndexed { index, term ->
        val start = maxOf(from, term.anchor, term.effectiveDate)
        val nextChange = terms.getOrNull(index + 1)?.effectiveDate?.minusDays(1)
        val limit = minOf(through, end ?: through, nextChange ?: through)
        if (start > limit) return@flatMapIndexed emptySequence()

        val interval = term.interval
        val anchor = term.anchor
        val startIndex = when {
            interval.days > 0 -> ChronoUnit.DAYS.between(anchor, start) / interval.days
            interval.months > 0 -> ChronoUnit.MONTHS.between(
                anchor.withDayOfMonth(1), start.withDayOfMonth(1),
            ) / interval.months
            else -> 0
        }
        generateSequence(startIndex) { if (interval == Interval.Once) null else it + 1 }
            .map { offset ->
                if (interval.days > 0) anchor.plusDays(offset * interval.days)
                else anchor.plusMonths(offset * interval.months)
            }
            .takeWhile { it <= limit }
            .filter { it >= start }
            .map { Charge(this, it, term) }
    }

fun Payment.nextCharge(today: LocalDate): Charge? =
    charges(today, maxOf(today, current.anchor, current.effectiveDate).plusYears(1)).firstOrNull()

@Serializable
data class Preferences(
    val defaultCurrency: String = "EUR",
    val renewalLead: Int = 2,
    val trialLead: Int = 3,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val trialReminders: Boolean = true,
)

@Serializable
data class Book(
    val version: Int = 1,
    val payments: List<Payment> = emptyList(),
    val preferences: Preferences = Preferences(),
)

data class Merge(val book: Book, val added: Int, val conflicts: Int)

fun Book.merge(incoming: Book): Merge {
    val existing = payments.associateBy { it.id }
    val added = incoming.payments.filter { it.id !in existing }
    val conflicts = incoming.payments.count { existing[it.id]?.let { local -> local != it } == true }
    return Merge(copy(
        payments = payments + added,
        preferences = if (this == Book()) incoming.preferences else preferences,
    ), added.size, conflicts)
}

fun parseAmount(input: String, currency: String): BigDecimal? {
    val value = input.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    val digits = Currency.getInstance(currency).defaultFractionDigits.coerceAtLeast(0)
    return value.takeIf { it.signum() >= 0 && it.scale() in 0..digits && it.precision() <= 14 }
}
