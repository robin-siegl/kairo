package dev.robinsiegl.kairo

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.serialization.Serializable

internal val categoryColors = listOf("Blue", "Teal", "Purple", "Rose", "Amber", "Slate")

@Serializable
data class Category(val name: String, val color: String = "Blue")

fun Book.allCategories(): List<Category> {
    val stored = categories.associateBy { it.name }
    return (categories.map { it.name } + payments.map { it.category }.filter(String::isNotBlank))
        .distinct().sortedBy { it.lowercase() }.map { stored[it] ?: Category(it) }
}

internal fun Book.saveCategory(previous: String?, category: Category): Book {
    val name = category.name.trim()
    require(name.isNotEmpty() && category.color in categoryColors)
    require(allCategories().none { it.name != previous && it.name.equals(name, ignoreCase = true) })
    return copy(
        categories = allCategories().filterNot { it.name == previous } + category.copy(name = name),
        payments = payments.map {
            if (previous != null && it.category == previous) it.copy(category = name) else it
        },
    )
}

internal fun Book.mergeCategory(source: String, target: String): Book {
    require(source != target && allCategories().any { it.name == source } && allCategories().any { it.name == target })
    return copy(
        categories = allCategories().filterNot { it.name == source },
        payments = payments.map { if (it.category == source) it.copy(category = target) else it },
    )
}

internal data class SpendingSummary(val total: BigDecimal, val byCategory: Map<String, BigDecimal>)

internal fun Book.spending(from: LocalDate, through: LocalDate): SpendingSummary {
    val grouped = payments.asSequence().flatMap { it.charges(from, through) }
        .groupingBy { it.payment.category.ifBlank { "" } }.fold(BigDecimal.ZERO) { sum, charge -> sum + charge.amount }
    return SpendingSummary(grouped.values.fold(BigDecimal.ZERO) { sum, amount -> sum + amount }, grouped)
}

internal fun Book.yearForecast(firstMonth: YearMonth): List<Pair<YearMonth, BigDecimal>> =
    (0L..11L).map { offset ->
        val month = firstMonth.plusMonths(offset)
        month to spending(month.atDay(1), month.atEndOfMonth()).total
    }

internal fun monthlyAverage(total: BigDecimal, currencyDigits: Int): BigDecimal =
    total.divide(BigDecimal(12), currencyDigits.coerceAtLeast(0), RoundingMode.HALF_UP)
