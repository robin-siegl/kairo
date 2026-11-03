package dev.robinsiegl.kairo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun Form(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun Field(label: String, value: String, change: (String) -> Unit) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) })
}

@Composable
private fun Toggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, change)
    }
}

@Composable
private fun <T> Choice(label: String, value: T, options: List<T>, text: (T) -> String, settingsRow: Boolean = false, change: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        if (settingsRow) SettingsRow(label, text(value)) { expanded = true }
        else FormRow(label, text(value)) { expanded = true }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(text(option)) }, onClick = { change(option); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, value: String?, today: LocalDate, change: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    FormRow(label, value?.let { formatDate(LocalDate.parse(it)) } ?: "Choose date") { open = true }
    if (open) {
        val initial = LocalDate.parse(value ?: today.toString()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial,
            yearRange = 1900..(maxOf(today.year + 25, LocalDate.parse(value ?: today.toString()).year)),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        change(Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    open = false
                }, enabled = state.selectedDateMillis != null) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun CurrencyPicker(currency: String, settingsRow: Boolean = false, change: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    val showPicker = { search = ""; open = true }
    if (settingsRow) SettingsRow("Default currency", currency, showPicker)
    else FormRow("Currency", currency, showPicker)
    if (open) {
        val currencies = remember {
            Currency.getAvailableCurrencies().filter { it.defaultFractionDigits >= 0 }.sortedBy { it.currencyCode }
        }
        val matches = currencies.filter { it.currencyCode.contains(search, true) || it.displayName.contains(search, true) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Choose currency") },
            text = {
                Column {
                    Field("Search currency", search) { search = it }
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(matches.size) { index ->
                            val item = matches[index]
                            TextButton(onClick = { change(item.currencyCode); open = false }) {
                                Text("${item.currencyCode}  ${item.displayName}")
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}

private fun isWebLink(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

@Composable
fun PaymentEditor(payment: Payment?, book: Book, today: LocalDate, save: (Payment) -> Unit) {
    val old = payment?.current
    var name by rememberSaveable { mutableStateOf(payment?.name.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(old?.amount.orEmpty()) }
    var currency by rememberSaveable { mutableStateOf(old?.currency ?: book.preferences.defaultCurrency) }
    var interval by rememberSaveable { mutableStateOf(old?.interval ?: Interval.Monthly) }
    var start by rememberSaveable { mutableStateOf(old?.firstCharge ?: today.toString()) }
    var trial by rememberSaveable { mutableStateOf(payment?.isTrial(today) == true) }
    var cancelBy by rememberSaveable { mutableStateOf(payment?.cancelBy) }
    var end by rememberSaveable { mutableStateOf(payment?.lastActiveDay) }
    var category by rememberSaveable { mutableStateOf(payment?.category.orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(payment?.icon ?: "Payment") }
    var image by rememberSaveable { mutableStateOf(payment?.image) }
    var notes by rememberSaveable { mutableStateOf(payment?.notes.orEmpty()) }
    var link by rememberSaveable { mutableStateOf(payment?.manageLink.orEmpty()) }
    var remind by rememberSaveable { mutableStateOf(payment?.remind ?: false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var details by rememberSaveable { mutableStateOf(false) }
    var iconPickerOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) { runCatching { loadIcon(context, uri) } }
                .onSuccess { image = it; error = null }
                .onFailure { error = "Couldn’t read that image." }
        }
    }
    Form {
        Text("Payment details", style = MaterialTheme.typography.titleMedium)
        Field("Payment name", name) { name = it }
        OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(),
            label = { Text(if (trial) "Price after trial" else "Amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        CurrencyPicker(currency) { currency = it }
        Text("Schedule", style = MaterialTheme.typography.titleMedium)
        Choice("Repeats", interval, Interval.entries, { it.label }) { interval = it }
        Toggle("Currently on a free trial", trial) { trial = it }
        DateField(if (trial) "First paid charge" else "First charge", start, today) { start = it }
        if (trial) {
            DateField("Cancel by (optional)", cancelBy, today) { cancelBy = it }
            if (cancelBy != null) TextButton(onClick = { cancelBy = null }) { Text("Clear deadline") }
        }
        Toggle("Remind before regular renewals", remind) { remind = it }
        FormRow("Optional details", if (details) "Hide fields" else "Category, icon, and notes") { details = !details }
        if (details) {
            Field("Category", category) { category = it }
            val categories = book.payments.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
            if (categories.isNotEmpty()) Choice("Use existing category", category, categories, { it }) { category = it }
            FormRow("Payment icon", if (image == null) icon else "Custom image") { iconPickerOpen = true }
            Field("Manage or cancel link", link) { link = it }
            Field("Notes", notes) { notes = it }
            DateField("Last active day (optional)", end, today) { end = it }
            if (end != null) TextButton(onClick = { end = null }) { Text("Clear end date") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            val price = parseAmount(amount, currency)
            error = when {
                name.isBlank() -> "Enter a payment name."
                price == null -> "Enter a valid amount for $currency."
                link.isNotBlank() && !isWebLink(link.trim()) -> "Enter an http or https link."
                trial && cancelBy?.let { it > start } == true -> "The cancellation deadline must be on or before the first charge."
                !trial && end?.let { it < start } == true && payment?.trial != true -> "The end date must be on or after the first charge."
                else -> null
            }
            if (error == null && price != null) {
                val newTerms = Terms(start, price.toPlainString(), currency, interval)
                val terms = when {
                    payment == null || old == null -> listOf(newTerms)
                    old.copy(effectiveFrom = old.firstCharge) == newTerms -> payment.terms
                    payment.charges(payment.terms.first().anchor, today.minusDays(1)).none() -> listOf(newTerms)
                    else -> payment.terms.filter { it.effectiveDate < today } + newTerms.copy(effectiveFrom = today.toString())
                }
                val preservePastTrial = payment?.trial == true && !payment.isTrial(today) && !trial && old?.firstCharge == start
                save(Payment(
                    id = payment?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name.trim(),
                    terms = terms,
                    category = category.trim(),
                    tags = payment?.tags.orEmpty(),
                    icon = icon,
                    image = image,
                    notes = notes.trim(),
                    manageLink = link.trim(),
                    trial = trial || preservePastTrial,
                    cancelBy = cancelBy.takeIf { trial || preservePastTrial },
                    lastActiveDay = end,
                    cancelled = end != null && (payment?.cancelled == true || end!! < today.toString()),
                    remind = remind,
                ))
            }
        }) { Text("Save payment") }
    }
    if (iconPickerOpen) {
        AlertDialog(
            onDismissRequest = { iconPickerOpen = false },
            title = { Text("Choose an icon") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    paymentIcons.forEach { (label, symbol) ->
                        val selected = image == null && icon == label
                        ListItem(
                            headlineContent = { Text(label) },
                            leadingContent = { Icon(symbol, contentDescription = null) },
                            trailingContent = if (selected) {
                                { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            } else null,
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier.selectable(selected, role = Role.RadioButton) {
                                icon = label
                                image = null
                                iconPickerOpen = false
                            },
                        )
                    }
                    TextButton(onClick = {
                        iconPickerOpen = false
                        pickImage.launch("image/*")
                    }) { Text("Choose custom image") }
                }
            },
            confirmButton = { TextButton(onClick = { iconPickerOpen = false }) { Text("Close") } },
        )
    }

}

@Composable
fun PaymentDetails(
    payment: Payment,
    today: LocalDate,
    onEdit: () -> Unit,
    onSave: (Payment) -> Boolean,
    onOpenLink: (String) -> Unit,
    onCategory: (String) -> Unit = {},
    requestCancellation: Boolean = false,
    consumeCancellation: () -> Unit = {},
) {
    val next = payment.nextCharge(today)
    var cancelling by rememberSaveable { mutableStateOf(false) }
    var end by rememberSaveable { mutableStateOf(today.toString()) }
    fun startCancellation() {
        val charge = next ?: return
        end = charge.date.minusDays(1).toString()
        cancelling = true
    }
    LaunchedEffect(requestCancellation) {
        if (requestCancellation) {
            startCancellation()
            consumeCancellation()
        }
    }
    Form {
        PaymentIcon(payment)
        Text(money(payment.current.amount.toBigDecimal(), payment.current.currency), style = MaterialTheme.typography.headlineMedium)
        Text(payment.current.interval.label)
        if (payment.isTrial(today)) Text("Currently on a free trial")
        payment.trialDeadline(today)?.let {
            Text(if (payment.cancelBy != null) "Cancel by ${formatDate(it)}" else "First charge ${formatDate(it)}")
        }
        if (next != null) Text("Next charge ${formatDate(next.date)}")
        payment.end?.let {
            Text(when {
                it < today -> "Ended ${formatDate(it)}"
                payment.cancelled -> "Cancelled. Ends ${formatDate(it)}"
                else -> "Ends ${formatDate(it)}"
            })
        }
        if (payment.category.isNotBlank()) AssistChip(
            onClick = { onCategory(payment.category) }, label = { Text(payment.category) },
        )
        if (payment.notes.isNotBlank()) Text(payment.notes)
        if (isWebLink(payment.manageLink)) OutlinedButton(onClick = { onOpenLink(payment.manageLink) }) {
            Text("Open provider page")
        }
        Button(onClick = onEdit) { Text("Edit payment") }
        if (!payment.cancelled && next != null) OutlinedButton(onClick = ::startCancellation) { Text("Mark as cancelled") }
        if (payment.cancelled) TextButton(onClick = {
            onSave(payment.copy(cancelled = false, lastActiveDay = null))
        }) { Text("Undo cancellation") }
    }
    if (cancelling) {
        val lastDay = LocalDate.parse(end)
        AlertDialog(
            onDismissRequest = { cancelling = false },
            title = { Text("When does it end?") },
            text = {
                Column {
                    DateField("Last active day", end, today) { end = it }
                    if (next != null && lastDay < next.date) {
                        Text("The ${money(next.amount, next.terms.currency)} charge on ${formatDate(next.date)} will be removed from your forecast.")
                    } else if (next != null) {
                        Text("Charges through ${formatDate(lastDay)} will stay in your forecast.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (onSave(payment.copy(cancelled = true, lastActiveDay = end))) cancelling = false
                }) { Text("Save cancellation") }
            },
            dismissButton = { TextButton(onClick = { cancelling = false }) { Text("Back") } },
        )
    }
}

@Composable
private fun FormRow(title: String, summary: String, click: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(role = Role.Button, onClick = click),
    )
}

@Composable
internal fun SettingsRow(title: String, summary: String? = null, click: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = click),
    )
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
        }
    }
}

@Composable
fun PreferencesScreen(
    prefs: Preferences,
    notificationsOn: Boolean,
    save: (Preferences) -> Unit,
    enableNotifications: () -> Unit,
    export: () -> Unit,
    import: () -> Unit,
) {
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    Form {
        SettingsGroup("General") {
            CurrencyPicker(prefs.defaultCurrency, settingsRow = true) { save(prefs.copy(defaultCurrency = it)) }
        }
        SettingsGroup("Reminders") {
            SettingsRow("Notifications", if (notificationsOn) "Enabled" else "Review notification access", enableNotifications)
            Choice("Renewal lead time", prefs.renewalLead, (1..7).toList(), ::dayLabel, settingsRow = true) {
                save(prefs.copy(renewalLead = it))
            }
            ListItem(
                headlineContent = { Text("Remind me about trials") },
                trailingContent = {
                    Switch(
                        checked = prefs.trialReminders,
                        onCheckedChange = { save(prefs.copy(trialReminders = it)) },
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
            if (prefs.trialReminders) {
                Choice("Trial lead time", prefs.trialLead, (1..7).toList(), ::dayLabel, settingsRow = true) {
                    save(prefs.copy(trialLead = it))
                }
            }
            val time = LocalTime.of(prefs.reminderHour, prefs.reminderMinute)
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            SettingsRow("Reminder time", time) { pickingTime = true }
        }
        SettingsGroup("Backup") {
            SettingsRow("Export backup", "Save a copy of your payments and settings", export)
            SettingsRow("Merge from backup", "Add payments from a Kairo backup", import)
        }
        SettingsGroup("About") {
            ListItem(
                headlineContent = { Text("App version") },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
            if (!BuildConfig.DEBUG) AppUpdates()
        }
    }
    if (pickingTime) {
        ReminderTimeDialog(prefs, dismiss = { pickingTime = false }) { hour, minute ->
            save(prefs.copy(reminderHour = hour, reminderMinute = minute))
            pickingTime = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(prefs: Preferences, dismiss: () -> Unit, save: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = prefs.reminderHour, initialMinute = prefs.reminderMinute)
    var useKeyboard by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = dismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Reminder time", style = MaterialTheme.typography.titleLarge)
                BoxWithConstraints {
                    if (useKeyboard || maxWidth < 256.dp || maxHeight < 380.dp) TimeInput(state)
                    else TimePicker(state, layoutType = TimePickerLayoutType.Vertical)
                }
                TextButton(onClick = { useKeyboard = !useKeyboard }) {
                    Text(if (useKeyboard) "Use clock" else "Type time")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = dismiss) { Text("Cancel") }
                    TextButton(onClick = { save(state.hour, state.minute) }) { Text("Save") }
                }
            }
        }
    }
}

private fun dayLabel(days: Int) = if (days == 1) "1 day before" else "$days days before"
