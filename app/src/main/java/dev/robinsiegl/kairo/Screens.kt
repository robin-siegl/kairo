package dev.robinsiegl.kairo

import android.text.format.DateUtils
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
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
        minimumFractionDigits = unit.defaultFractionDigits.coerceAtLeast(0)
        maximumFractionDigits = maxOf(minimumFractionDigits, amount.stripTrailingZeros().scale())
    }.format(amount)

@Composable
internal fun PaymentIcon(payment: Payment) {
    PaymentSymbolIcon(payment.icon)
}

@Composable
private fun PeriodBar(title: String, period: String, previous: () -> Unit, next: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = previous) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous $period")
        }
        Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = next) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next $period")
        }
    }
}

@Composable
private fun ScheduledTotal(label: String, amount: BigDecimal, currency: String) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(money(amount, currency), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun UpcomingChart(upcoming: List<Charge>, today: LocalDate, currency: String) {
    val midpoint = today.plusDays(7)
    val first = upcoming.filter { it.date < midpoint }.sumOf { it.amount }
    val second = upcoming.filter { it.date >= midpoint }.sumOf { it.amount }
    val maximum = maxOf(first, second)
    val context = LocalContext.current
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("14-day total", style = MaterialTheme.typography.titleMedium)
            Text(money(first + second, currency), style = MaterialTheme.typography.headlineSmall)
            listOf(today to first, midpoint to second).forEach { (start, amount) ->
                val dates = DateUtils.formatDateRange(
                    context,
                    (start.toEpochDay() * 24 + 12) * DateUtils.HOUR_IN_MILLIS,
                    (start.plusDays(6).toEpochDay() * 24 + 12) * DateUtils.HOUR_IN_MILLIS,
                    DateUtils.FORMAT_UTC or DateUtils.FORMAT_SHOW_DATE or
                        DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(dates, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(money(amount, currency), Modifier.weight(1f), textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelLarge)
                }
                val fraction = if (maximum.signum() == 0) 0f else amount.toFloat() / maximum.toFloat()
                Box(Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
            }
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
                Surface(shape = MaterialTheme.shapes.medium, color = paymentIconContainer(payment.icon)) {
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
        if (upcoming.isNotEmpty()) item { UpcomingChart(upcoming, today, book.preferences.defaultCurrency) }
        items(upcoming) { charge ->
            PaymentRow(charge.payment, formatDate(charge.date), money(charge.amount, book.preferences.defaultCurrency), open)
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
    manageCategories: () -> Unit = {},
) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Active") }
    val visible = book.payments.filter { payment ->
        val matches = listOf(payment.name, payment.category).any { it.contains(search, true) }
        val ended = payment.end?.let { it < today } == true ||
            (payment.termsOn(today).interval == Interval.Once && payment.termsOn(today).anchor < today)
        if (category != null) (if (category.isBlank()) payment.category.isBlank() else payment.category == category) else matches && when (filter) {
            "Trials" -> payment.isTrial(today)
            "Ended" -> ended
            else -> !ended
        }
    }.sortedBy { it.name.lowercase() }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (category != null) item {
            val month = YearMonth.from(today)
            val total = book.spending(month.atDay(1), month.atEndOfMonth()).byCategory[category] ?: BigDecimal.ZERO
            ScheduledTotal("Scheduled this month", total, book.preferences.defaultCurrency)
            Text("${visible.size} payments, including ended payments", style = MaterialTheme.typography.bodySmall)
        }
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
            item {
                TextButton(onClick = manageCategories) { Text("Manage categories") }
            }
            val categories = book.allCategories()
            if (categories.isNotEmpty()) item {
                Section("Categories")
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it.name }) { category ->
                        AssistChip(onClick = { openCategory(category.name) }, label = { Text(category.name) },
                            leadingIcon = { CategoryDot(category) })
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
                else -> payment.termsOn(today).interval.label
            }
            PaymentRow(
                payment,
                listOf(status, payment.category).filter { it.isNotBlank() }.joinToString(", "),
                money(payment.termsOn(today).amount.toBigDecimal(), book.preferences.defaultCurrency),
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
    view: String,
    selectView: (String) -> Unit,
) {
    val currency = book.preferences.defaultCurrency
    val yearView = view == "Year"
    val start = if (yearView) month.withMonth(1).atDay(1) else month.atDay(1)
    val end = if (yearView) start.plusYears(1).minusDays(1) else month.atEndOfMonth()
    val scheduled = remember(book.payments, start, end) {
        book.payments.flatMap { it.charges(start, end).toList() }.sortedBy { it.date }
    }
    val charges = remember(scheduled) { scheduled.groupBy { it.date } }
    val deadlines = book.payments.filter { it.trial && !it.cancelled }.mapNotNull { payment ->
        payment.cancelBy?.let { LocalDate.parse(it) to payment }
    }.groupBy({ it.first }, { it.second })
    val firstOffset = month.atDay(1).dayOfWeek.value - 1
    val dates = List(firstOffset) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Day", "Month", "Year").forEach { option ->
                    FilterChip(view == option, onClick = { selectView(option) }, label = { Text(option) })
                }
            }
            val period = if (yearView) "year" else "month"
            PeriodBar(
                if (yearView) month.year.toString() else month.format(DateTimeFormatter.ofPattern("LLLL yyyy")),
                period,
                previous = { changeMonth(if (yearView) month.minusYears(1) else month.minusMonths(1)) },
                next = { changeMonth(if (yearView) month.plusYears(1) else month.plusMonths(1)) },
            )
        }
        if (view == "Day") item {
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
        when (view) {
            "Year" -> {
                item { ScheduledTotal("Scheduled this year", scheduled.sumOf { it.amount }, currency) }
                val byMonth = scheduled.groupBy { YearMonth.from(it.date) }
                items((1..12).toList()) { number ->
                    val entry = month.withMonth(number)
                    SettingsRow(
                        entry.format(DateTimeFormatter.ofPattern("LLLL")),
                        money(byMonth[entry].orEmpty().sumOf { it.amount }, currency),
                    ) {
                        changeMonth(entry)
                        selectView("Month")
                    }
                }
            }
            "Month" -> {
                item { ScheduledTotal("Scheduled this month", scheduled.sumOf { it.amount }, currency) }
                val monthDeadlines = deadlines.filterKeys { it in start..end }
                val activeDays = (charges.keys + monthDeadlines.keys).distinct().sorted()
                if (activeDays.isEmpty()) item { Text("Nothing scheduled") }
                activeDays.forEach { day ->
                    item { Section(formatDate(day)) }
                    items(monthDeadlines[day].orEmpty()) { PaymentRow(it, "Cancel by", null, open) }
                    items(charges[day].orEmpty()) { PaymentRow(it.payment, "Payment due", money(it.amount, currency), open) }
                }
            }
            else -> {
                val dayDeadlines = deadlines[selected].orEmpty()
                val dayCharges = charges[selected].orEmpty()
                item { ScheduledTotal("Scheduled this day", dayCharges.sumOf { it.amount }, currency) }
                if (dayDeadlines.isEmpty() && dayCharges.isEmpty()) item { Text("Nothing scheduled") }
                items(dayDeadlines) { PaymentRow(it, "Cancel by", null, open) }
                items(dayCharges) { PaymentRow(it.payment, "Payment due", money(it.amount, currency), open) }
            }
        }
    }
}
