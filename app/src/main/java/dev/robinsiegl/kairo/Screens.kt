package dev.robinsiegl.kairo

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency

fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

fun money(amount: BigDecimal, currency: String): String =
    NumberFormat.getCurrencyInstance().apply {
        this.currency = Currency.getInstance(currency)
        maximumFractionDigits = this.currency.defaultFractionDigits.coerceAtLeast(0)
        minimumFractionDigits = maximumFractionDigits
    }.format(amount)

internal val paymentIcons = mapOf(
    "Payment" to Icons.Default.Star,
    "Home" to Icons.Default.Home,
    "Health" to Icons.Default.Favorite,
    "Entertainment" to Icons.Default.PlayArrow,
    "Work" to Icons.Default.Build,
    "Shopping" to Icons.Default.ShoppingCart,
)

@Composable
internal fun PaymentIcon(payment: Payment) {
    val image = remember(payment.image) {
        payment.image?.let {
            runCatching {
                val bytes = Base64.decode(it, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    if (image != null) Image(image, contentDescription = null, modifier = Modifier.size(28.dp))
    else Icon(paymentIcons[payment.icon] ?: Icons.Default.Star, contentDescription = null)
}

@Composable
private fun MonthBar(month: YearMonth, change: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { change(month.minusMonths(1)) }) { Text("Previous") }
        Text(
            month.format(DateTimeFormatter.ofPattern("LLLL yyyy")),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = { change(month.plusMonths(1)) }) { Text("Next") }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun PaymentRow(payment: Payment, detail: String, amount: String?, open: (String) -> Unit) {
    ListItem(
        headlineContent = { Text(payment.name) },
        supportingContent = { Text(detail) },
        leadingContent = { PaymentIcon(payment) },
        trailingContent = amount?.let { { Text(it) } },
        modifier = Modifier.clickable(role = Role.Button, onClick = { open(payment.id) }),
    )
}

@Composable
fun Overview(
    book: Book,
    month: YearMonth,
    today: LocalDate,
    notificationsOn: Boolean,
    changeMonth: (YearMonth) -> Unit,
    open: (String) -> Unit,
    enableNotifications: () -> Unit,
) {
    val charges = remember(book.payments, month) {
        book.payments.flatMap { it.charges(month.atDay(1), month.atEndOfMonth()).toList() }
    }
    val trials = book.payments.mapNotNull { payment -> payment.trialDeadline(today)?.let { payment to it } }
        .sortedBy { it.second }
    val upcoming = remember(book.payments, today) {
        book.payments.mapNotNull { it.nextCharge(today) }.sortedBy { it.date }.take(8)
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp)) {
        item { MonthBar(month, changeMonth) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Scheduled this month")
                    if (charges.isEmpty()) Text(
                        money(BigDecimal.ZERO, book.preferences.defaultCurrency),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    charges.groupBy { it.terms.currency }.toSortedMap().forEach { (currency, group) ->
                        Text(money(group.sumOf { it.amount }, currency), style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
        }
        if (trials.isNotEmpty()) {
            item { Section("Needs attention") }
            if (!notificationsOn && book.preferences.trialReminders) item {
                TextButton(onClick = enableNotifications) { Text("Turn on trial notifications") }
            }
            items(trials) { (payment, date) ->
                val title = when {
                    payment.cancelBy == null -> "First charge"
                    date < today -> "Cancellation deadline passed"
                    else -> "Cancel by"
                }
                PaymentRow(payment, "$title, ${formatDate(date)}", null, open)
            }
        }
        item { Section("Upcoming payments") }
        if (upcoming.isEmpty()) item {
            Text("Add a recurring payment or free trial to see what’s coming up.", Modifier.padding(vertical = 12.dp))
        }
        items(upcoming) { charge ->
            PaymentRow(charge.payment, formatDate(charge.date), money(charge.amount, charge.terms.currency), open)
        }
        if (charges.isNotEmpty()) {
            item { Section("By category") }
            items(charges.groupBy { it.payment.category.ifBlank { "Uncategorized" } }.toSortedMap().toList()) { (name, group) ->
                ListItem(
                    headlineContent = { Text(name) },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            group.groupBy { it.terms.currency }.toSortedMap().forEach { (currency, entries) ->
                                Text(money(entries.sumOf { it.amount }, currency))
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun Payments(book: Book, today: LocalDate, open: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Active") }
    val visible = book.payments.filter { payment ->
        val matches = listOf(payment.name, payment.category).plus(payment.tags).any { it.contains(search, true) }
        val ended = payment.end?.let { it < today } == true ||
            (payment.current.interval == Interval.Once && payment.current.anchor < today)
        matches && when (filter) {
            "Trials" -> payment.isTrial(today)
            "Ended" -> ended
            else -> !ended
        }
    }.sortedBy { it.name.lowercase() }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp)) {
        item {
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(),
                label = { Text("Search names, categories, or tags") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Active", "Trials", "Ended").forEach { option ->
                    FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option) })
                }
            }
        }
        if (visible.isEmpty()) item { Text("No matching payments", Modifier.padding(vertical = 16.dp)) }
        items(visible, key = { it.id }) { payment ->
            val status = when {
                payment.end?.let { it < today } == true -> "Ended"
                payment.cancelled -> "Cancelled"
                payment.isTrial(today) -> "Free trial"
                else -> payment.current.interval.label
            }
            PaymentRow(
                payment,
                listOf(status, payment.category).filter { it.isNotBlank() }.joinToString(", "),
                money(payment.current.amount.toBigDecimal(), payment.current.currency),
                open,
            )
        }
    }
}

@Composable
fun Calendar(
    book: Book,
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    changeMonth: (YearMonth) -> Unit,
    select: (LocalDate) -> Unit,
    open: (String) -> Unit,
) {
    val charges = remember(book.payments, month) {
        book.payments.flatMap { it.charges(month.atDay(1), month.atEndOfMonth()).toList() }.groupBy { it.date }
    }
    val deadlines = book.payments.filter { it.trial && !it.cancelled }.mapNotNull { payment ->
        payment.cancelBy?.let { LocalDate.parse(it) to payment }
    }.groupBy({ it.first }, { it.second })
    val firstOffset = month.atDay(1).dayOfWeek.value - 1
    val dates = List(firstOffset) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            MonthBar(month, changeMonth)
            Row(Modifier.fillMaxWidth()) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                }
            }
            dates.chunked(7).forEach { week ->
                Row {
                    repeat(7) { index ->
                        val date = week.getOrNull(index)
                        if (date == null) Spacer(Modifier.weight(1f))
                        else {
                            val chargeCount = charges[date]?.size ?: 0
                            val deadlineCount = deadlines[date]?.size ?: 0
                            Surface(
                                Modifier.weight(1f).heightIn(min = 48.dp)
                                    .selectable(date == selected, role = Role.Button) { select(date) }
                                    .semantics {
                                        contentDescription = "${formatDate(date)}, $chargeCount payments, $deadlineCount deadlines"
                                    },
                                color = if (date == selected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center) {
                                    Text(date.dayOfMonth.toString(), color = if (date == today)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    if (chargeCount + deadlineCount > 0) {
                                        Text(if (deadlineCount > 0) "!" else "•", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Section(formatDate(selected))
        }
        val dayDeadlines = deadlines[selected].orEmpty()
        val dayCharges = charges[selected].orEmpty()
        if (dayDeadlines.isEmpty() && dayCharges.isEmpty()) item {
            Text("Nothing scheduled", Modifier.padding(vertical = 12.dp))
        }
        items(dayDeadlines) { PaymentRow(it, "Cancel by", null, open) }
        items(dayCharges) { PaymentRow(it.payment, "Payment due", money(it.amount, it.terms.currency), open) }
    }
}
