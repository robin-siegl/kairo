package dev.robinsiegl.kairo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun BackupReviewDialog(
    local: Book,
    incoming: Book,
    verificationOnly: Boolean,
    dismiss: () -> Unit,
    save: (Book) -> Boolean,
) {
    val result = remember(local, incoming) { runCatching { local.merge(incoming) } }
    val merge = result.getOrNull()
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (verificationOnly) "Backup verified" else "Review backup") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${incoming.payments.size} payments, ${incoming.allCategories().size} categories")
                Text("Currency: ${incoming.preferences.defaultCurrency}")
                if (verificationOnly) {
                    Text("This file is a readable Kairo backup. Your payments and settings have not been changed.")
                } else if (merge == null) {
                    Text(result.exceptionOrNull()?.message ?: "This backup cannot be merged.", color = MaterialTheme.colorScheme.error)
                    Text("Your payments have not been changed.")
                } else {
                    Text("${merge.added} new payments")
                    Text("${incoming.payments.size - merge.added - merge.conflicts} unchanged payments")
                    Text("${merge.conflicts} changed payments will keep the version on this device.")
                    val conflicts = incoming.payments.filter { item -> local.payments.any { it.id == item.id && it != item } }
                    if (conflicts.isNotEmpty()) Text(conflicts.take(5).joinToString("\n") { it.name } +
                        if (conflicts.size > 5) "\nAnd ${conflicts.size - 5} more" else "")
                    Text("Existing category colors stay on this device.")
                    Text(if (local == Book()) "The backup's app settings will be restored." else "Your app settings stay on this device.")
                    if (merge.book.preferences.defaultCurrency != local.preferences.defaultCurrency && local != Book()) {
                        Text("Currency will change to ${merge.book.preferences.defaultCurrency} to match the imported payments.")
                    }
                }
                Text("JSON backups include payments, categories and app settings. Widget setup is managed separately by Android backup.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            if (!verificationOnly && merge != null) TextButton(onClick = { if (save(merge.book)) dismiss() }) { Text("Merge backup") }
            else TextButton(onClick = dismiss) { Text("Close") }
        },
        dismissButton = {
            if (!verificationOnly && merge != null) TextButton(onClick = dismiss) { Text("Cancel") }
        },
    )
}
