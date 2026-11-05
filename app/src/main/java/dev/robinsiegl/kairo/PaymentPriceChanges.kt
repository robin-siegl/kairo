package dev.robinsiegl.kairo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
internal fun PaymentPriceChanges(payment: Payment, today: LocalDate, currency: String, save: (Payment) -> Boolean) {
    var scheduling by rememberSaveable { mutableStateOf(false) }
    var history by rememberSaveable { mutableStateOf(false) }
    var amount by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(today.plusDays(1).toString()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    val upcoming = payment.terms.drop(1).filter { it.effectiveDate > today }
    upcoming.firstOrNull()?.let {
        Text("${money(it.amount.toBigDecimal(), currency)} from ${formatDate(it.effectiveDate)}",
            color = MaterialTheme.colorScheme.primary)
    }
    if (payment.nextCharge(today) != null) DetailAction("Schedule price change", Icons.Default.DateRange) {
        amount = payment.termsOn(today).amount
        date = (payment.nextCharge(today)?.date?.takeIf { it > today } ?: today.plusDays(1)).toString()
        error = null
        scheduling = true
    }
    TextButton(onClick = { history = true }) { Text("Price history") }
    if (scheduling) AlertDialog(
        onDismissRequest = { scheduling = false },
        title = { Text("Schedule price change") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    amount, { amount = it; error = null },
                    label = { Text("New amount") }, singleLine = true,
                    trailingIcon = { Text(currency, Modifier.padding(horizontal = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                DateField("Applies from", date, today) { date = it; error = null }
                Text("Charges on or after this date use the new price. The billing dates stay the same.",
                    style = MaterialTheme.typography.bodySmall)
                if (payment.terms.any { it.effectiveFrom == date }) Text(
                    "This replaces the price already scheduled for this date.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val price = parseAmount(amount, currency)
                val effective = LocalDate.parse(date)
                error = when {
                    price == null -> "Enter a valid amount for $currency."
                    effective <= today -> "Choose a date after today."
                    payment.end?.let { effective > it } == true -> "Choose a date on or before the last active day."
                    effective < payment.terms.first().effectiveDate && payment.terms.first().anchor < effective ->
                        "Choose a date on or after ${formatDate(payment.terms.first().effectiveDate)}."
                    else -> null
                }
                if (error == null && price != null && save(payment.schedulePrice(price.toPlainString(), effective, today))) {
                    scheduling = false
                }
            }) { Text("Save price change") }
        },
        dismissButton = { TextButton(onClick = { scheduling = false }) { Text("Cancel") } },
    )
    if (history && removing == null) AlertDialog(
        onDismissRequest = { history = false },
        title = { Text("Price history") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(payment.terms, key = { _, terms -> terms.effectiveFrom }) { index, terms ->
                    ListItem(
                        headlineContent = { Text(money(terms.amount.toBigDecimal(), currency)) },
                        supportingContent = { Text(
                            (if (terms.effectiveDate > today) "Scheduled from " else "From ") +
                                formatDate(terms.effectiveDate) + ", " + terms.interval.label,
                        ) },
                        trailingContent = if (index > 0 && terms.effectiveDate > today) {
                            { TextButton(onClick = { removing = terms.effectiveFrom }) { Text("Remove") } }
                        } else null,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { history = false }) { Text("Close") } },
    )
    if (removing != null) AlertDialog(
        onDismissRequest = { removing = null },
        title = { Text("Remove scheduled price?") },
        text = { Text("The preceding price will continue until the next scheduled change.") },
        confirmButton = {
            TextButton(onClick = {
                if (save(payment.copy(terms = payment.terms.filterNot { it.effectiveFrom == removing }))) removing = null
            }) { Text("Remove change") }
        },
        dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep change") } },
    )
}
