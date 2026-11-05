package dev.robinsiegl.kairo

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

internal enum class WidgetKind(val title: String) {
    Upcoming("Upcoming payments"), Trials("Trial deadlines"), Next("Next payment"), Month("Month summary"),
}

internal data class WidgetEntry(val payment: Payment, val date: LocalDate, val amount: BigDecimal?)

internal fun Book.widgetEntries(kind: WidgetKind, today: LocalDate, days: Int): List<WidgetEntry> {
    if (kind == WidgetKind.Trials) return payments.mapNotNull { payment ->
        payment.trialDeadline(today)?.let { WidgetEntry(payment, it, null) }
    }.sortedWith(compareBy({ it.date }, { it.payment.name }))
    if (kind == WidgetKind.Next) return payments.mapNotNull { it.nextCharge(today) }
        .sortedWith(compareBy({ it.date }, { it.payment.name }))
        .take(1).map { WidgetEntry(it.payment, it.date, it.amount) }
    val month = YearMonth.from(today)
    val from = if (kind == WidgetKind.Month) month.atDay(1) else today
    val through = if (kind == WidgetKind.Month) month.atEndOfMonth() else today.plusDays(days.toLong() - 1)
    return payments.flatMap { it.charges(from, through).toList() }
        .sortedWith(compareBy({ it.date }, { it.payment.name }))
        .map { WidgetEntry(it.payment, it.date, it.amount) }
}
