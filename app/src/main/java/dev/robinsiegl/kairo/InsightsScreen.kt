package dev.robinsiegl.kairo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency

internal fun categoryColor(color: String): Color = Color(when (color) {
    "Teal" -> 0xFF008577
    "Purple" -> 0xFF8553B4
    "Rose" -> 0xFFBE466B
    "Amber" -> 0xFFB37400
    "Slate" -> 0xFF61748B
    else -> 0xFF3367DB
})

@Composable
internal fun CategoryDot(category: Category) {
    Box(Modifier.size(12.dp).clip(CircleShape).background(categoryColor(category.color)))
}

@Composable
internal fun InsightsScreen(
    book: Book,
    today: LocalDate,
    selected: YearMonth,
    selectMonth: (YearMonth) -> Unit,
    openCategory: (String) -> Unit,
    manageCategories: () -> Unit,
) {
    val chartState = rememberLazyListState(initialFirstVisibleItemIndex = (selected.monthValue - 3).coerceAtLeast(0))
    LaunchedEffect(selected) {
        val index = selected.monthValue - 1
        if (chartState.layoutInfo.visibleItemsInfo.none { it.index == index }) chartState.scrollToItem(index)
    }
    val currency = book.preferences.defaultCurrency
    val summary = remember(book, selected) { book.spending(selected.atDay(1), selected.atEndOfMonth()) }
    val year = remember(book, selected.year) { book.yearForecast(YearMonth.of(selected.year, 1)) }
    val through = today.plusYears(1).minusDays(1)
    val ahead = remember(book, today) { book.spending(today, through).total }
    val categories = book.allCategories().associateBy { it.name }
    val maximum = year.maxOf { it.second }.takeIf { it.signum() > 0 } ?: BigDecimal.ONE
    val categoryMaximum = summary.byCategory.values.maxOrNull()?.takeIf { it.signum() > 0 } ?: BigDecimal.ONE
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectMonth(selected.minusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous month")
                }
                Text(selected.format(DateTimeFormatter.ofPattern("LLLL yyyy")), Modifier.weight(1f),
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { selectMonth(selected.plusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next month")
                }
            }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Scheduled this month", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(money(summary.total, currency), style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Based on your scheduled charges", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        item {
            Text("${selected.year} by month", style = MaterialTheme.typography.titleMedium)
            Text("Tap a month to see its breakdown", style = MaterialTheme.typography.bodySmall)
            LazyRow(Modifier.fillMaxWidth().height(160.dp), state = chartState, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(year, key = { it.first.toString() }) { (month, amount) ->
                    Column(
                        Modifier.width(48.dp).fillMaxHeight()
                            .semantics(mergeDescendants = true) {
                                contentDescription = "${month.format(DateTimeFormatter.ofPattern("LLLL yyyy"))}, ${money(amount, currency)}"
                            }
                            .clickable(role = Role.Button) { selectMonth(month) },
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height((amount.toFloat() / maximum.toFloat() * 116f).coerceAtLeast(2f).dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(if (month == selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.secondaryContainer),
                        )
                        Text(month.monthValue.toString(), style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                    }
                }
            }
            Text("Year total ${money(year.sumOf { it.second }, currency)}", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Categories", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = manageCategories) { Text("Manage") }
            }
        }
        if (summary.byCategory.isEmpty()) item {
            Text("No payments scheduled for this month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(summary.byCategory.entries.sortedByDescending { it.value }, key = { it.key }) { entry ->
            val category = categories[entry.key] ?: Category(entry.key)
            Surface(
                shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { openCategory(entry.key) },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CategoryDot(category)
                        Text(entry.key.ifBlank { "Uncategorized" }, Modifier.weight(1f))
                        Text(money(entry.value, currency), style = MaterialTheme.typography.labelLarge)
                    }
                    LinearProgressIndicator(
                        progress = { entry.value.toFloat() / categoryMaximum.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = categoryColor(category.color),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        drawStopIndicator = {},
                    )
                }
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The year ahead", style = MaterialTheme.typography.titleMedium)
                    Text("${formatDate(today)} – ${formatDate(through)}", style = MaterialTheme.typography.bodySmall)
                    Text(money(ahead, currency), style = MaterialTheme.typography.headlineMedium)
                    val average = monthlyAverage(ahead, Currency.getInstance(currency).defaultFractionDigits)
                    Text("${money(average, currency)} monthly average")
                    Text("Average = the scheduled 12-month total divided by 12. Annual and one-time charges are included.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
