package dev.robinsiegl.kairo

import java.time.LocalDate

fun Payment.termsOn(date: LocalDate): Terms =
    terms.lastOrNull { it.effectiveDate <= date } ?: terms.first()

internal fun Payment.schedulePrice(amount: String, date: LocalDate, today: LocalDate): Payment {
    require(parseStoredAmount(amount) != null && date > today)
    require(end == null || date <= end)
    val first = terms.first()
    require(date >= first.effectiveDate || first.anchor >= date)
    val baseline = termsOn(date)
    val history = if (first.effectiveDate >= date && first.anchor >= date) {
        listOf(first.copy(effectiveFrom = today.toString())) + terms.drop(1)
    } else terms
    val changed = baseline.copy(amount = amount, effectiveFrom = date.toString())
    return copy(terms = (history.filterNot { it.effectiveDate == date } + changed).sortedBy { it.effectiveDate })
}

internal fun Payment.updatedTerms(replacement: Terms, today: LocalDate): List<Terms> {
    val active = termsOn(today)
    if (active.copy(effectiveFrom = active.firstCharge) == replacement) return terms
    val historicalCharges = charges(terms.first().anchor, today.minusDays(1)).any()
    val future = terms.filter { it.effectiveDate > today && it != active }.map {
        it.copy(firstCharge = replacement.firstCharge, interval = replacement.interval)
    }
    if (!historicalCharges) {
        val first = terms.first()
        val effective = if (first.effectiveDate > today && first.anchor < first.effectiveDate) {
            first.effectiveDate
        } else minOf(today, replacement.anchor)
        return listOf(replacement.copy(effectiveFrom = effective.toString())) + future
    }
    return terms.filter { it.effectiveDate < today } + replacement.copy(effectiveFrom = today.toString()) + future
}
