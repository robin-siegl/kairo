package dev.robinsiegl.kairo

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import kotlinx.coroutines.launch
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
        val unit = Currency.getInstance(currency)
        this.currency = unit
        maximumFractionDigits = unit.defaultFractionDigits.coerceAtLeast(0)
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
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth in 1..96 && bounds.outHeight in 1..96) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } else null
            }.getOrNull()
        }
    }
    if (image != null) Image(image, contentDescription = null, modifier = Modifier.size(28.dp))
    else Icon(paymentIcons[payment.icon] ?: Icons.Default.Star, contentDescription = null)
}

@Composable
private fun MonthBar(month: YearMonth, change: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { change(month.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous month")
        }
        Text(
            month.format(DateTimeFormatter.ofPattern("LLLL yyyy")),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = { change(month.plusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next month")
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentRow(
    payment: Payment,
    detail: String,
    amount: String?,
    open: (String) -> Unit,
    quickAction: ((String, Boolean) -> Unit)? = null,
    canCancel: Boolean = false,
) {
    val shape = MaterialTheme.shapes.large
    val content: @Composable () -> Unit = {
        ListItem(
            headlineContent = { Text(payment.name) },
            supportingContent = { Text(detail) },
            leadingContent = {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { PaymentIcon(payment) }
                }
            },
            trailingContent = amount?.let { { Text(it) } },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.clip(shape).clickable(role = Role.Button, onClick = { open(payment.id) })
                .semantics {
                    if (quickAction != null) customActions = buildList {
                        add(CustomAccessibilityAction("Edit payment") { quickAction(payment.id, false); true })
                        if (canCancel) add(CustomAccessibilityAction("Mark as cancelled") { quickAction(payment.id, true); true })
                    }
                },
        )
    }
    if (quickAction == null) {
        content()
    } else {
        val state = rememberSwipeToDismissBoxState()
        val scope = rememberCoroutineScope()
        SwipeToDismissBox(
            state = state,
            modifier = Modifier.clip(shape),
            enableDismissFromEndToStart = canCancel,
            onDismiss = { direction ->
                scope.launch { state.reset() }
                if (direction != SwipeToDismissBoxValue.Settled) {
                    quickAction(payment.id, direction == SwipeToDismissBoxValue.EndToStart)
                }
            },
            backgroundContent = {
                val cancel = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
                Box(
                    Modifier.fillMaxSize().background(
                        if (cancel) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    ).padding(horizontal = 20.dp),
                    contentAlignment = if (cancel) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    Icon(if (cancel) Icons.Default.Close else Icons.Default.Edit, contentDescription = null,
                        tint = if (cancel) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
                }
            },
        ) { content() }
    }
}

@Composable
fun Overview(
    book: Book,
    today: LocalDate,
    notificationsOn: Boolean,
    open: (String) -> Unit,
    enableNotifications: () -> Unit,
) {
    val through = today.plusDays(13)
    val trials = book.payments.mapNotNull { payment ->
        payment.trialDeadline(today)?.takeIf { it <= through }?.let { payment to it }
    }.sortedBy { it.second }
    val upcoming = remember(book.payments, today) {
        book.payments.flatMap { it.charges(today, through).toList() }.sortedBy { it.date }
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
        item { Section("Next 14 days") }
        if (upcoming.isEmpty()) item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.DateRange, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        if (book.payments.isEmpty()) "Welcome to Kairo" else if (trials.isEmpty()) "You’re all clear" else "No payments due",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        if (book.payments.isEmpty()) "Add your first payment to see what’s coming up."
                        else "No payments are due in the next 14 days.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(upcoming) { charge ->
            PaymentRow(charge.payment, formatDate(charge.date), money(charge.amount, charge.terms.currency), open)
        }
    }
}

@Composable
fun Payments(
    book: Book,
    today: LocalDate,
    open: (String) -> Unit,
    openCategory: (String) -> Unit,
    quickAction: (String, Boolean) -> Unit,
    category: String? = null,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Active") }
    val visible = book.payments.filter { payment ->
        val matches = listOf(payment.name, payment.category).any { it.contains(search, true) }
        val ended = payment.end?.let { it < today } == true ||
            (payment.current.interval == Interval.Once && payment.current.anchor < today)
        if (category != null) payment.category == category else matches && when (filter) {
            "Trials" -> payment.isTrial(today)
            "Ended" -> ended
            else -> !ended
        }
    }.sortedBy { it.name.lowercase() }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (category == null) item {
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(),
                label = { Text("Search names or categories") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Active", "Trials", "Ended").forEach { option ->
                    FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option) })
                }
            }
        }
        if (category == null && search.isBlank()) {
            val categories = book.payments.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
            if (categories.isNotEmpty()) item {
                Section("Categories")
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { name ->
                        AssistChip(onClick = { openCategory(name) }, label = { Text(name) })
                    }
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
                quickAction,
                canCancel = !payment.cancelled && payment.nextCharge(today) != null,
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
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
