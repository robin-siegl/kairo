package dev.robinsiegl.kairo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun CategoriesScreen(book: Book, today: LocalDate, open: (String) -> Unit, save: (Book) -> Boolean) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var previous by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf("Blue") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var merge by rememberSaveable { mutableStateOf(false) }
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    var removing by rememberSaveable { mutableStateOf(false) }
    val categories = book.allCategories()
    val month = YearMonth.from(today)
    val totals = remember(book, month) { book.spending(month.atDay(1), month.atEndOfMonth()).byCategory }
    fun edit(category: Category?) {
        previous = category?.name
        name = category?.name.orEmpty()
        color = category?.color ?: "Blue"
        error = null
        editing = true
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilledTonalButton(onClick = { edit(null) }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add category")
            }
            Text("Scheduled totals for ${month.format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy"))}",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
        }
        items(categories, key = { it.name }) { category ->
            val count = book.payments.count { it.category == category.name }
            ListItem(
                headlineContent = { Text(category.name) },
                supportingContent = { Text("$count payments, ${money(totals[category.name] ?: java.math.BigDecimal.ZERO, book.preferences.defaultCurrency)}") },
                leadingContent = { CategoryDot(category) },
                trailingContent = {
                    IconButton(onClick = { edit(category) }) { Icon(Icons.Default.Edit, "Edit ${category.name}") }
                },
                modifier = Modifier.clickable(role = Role.Button) { open(category.name) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
        }
        val uncategorized = book.payments.count { it.category.isBlank() }
        if (uncategorized > 0) item {
            SettingsRow("Uncategorized", "$uncategorized payments") { open("") }
        }
        if (categories.isEmpty()) item {
            Text("Create categories to organize your payments and compare scheduled costs.")
        }
    }
    if (editing && !merge && !removing) AlertDialog(
        onDismissRequest = { editing = false },
        title = { Text(if (previous == null) "Add category" else "Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it; error = null }, label = { Text("Category name") },
                    singleLine = true, isError = error != null,
                    supportingText = error?.let { { Text(it) } })
                Text("Color", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categoryColors) { option ->
                        FilterChip(color == option, onClick = { color = option },
                            label = { Text(option) }, leadingIcon = { CategoryDot(Category("", option)) })
                    }
                }
                if (previous != null) {
                    Text("Renaming updates every payment in this category.", style = MaterialTheme.typography.bodySmall)
                    if (categories.size > 1) TextButton(onClick = { target = null; merge = true }) {
                        Text("Merge into another category")
                    }
                    TextButton(onClick = { removing = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("Remove category")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val proposed = name.trim()
                error = when {
                    proposed.isEmpty() -> "Enter a category name."
                    categories.any { it.name != previous && it.name.equals(proposed, ignoreCase = true) } ->
                        "A category with this name already exists."
                    else -> null
                }
                if (error == null && save(book.saveCategory(previous, Category(proposed, color)))) editing = false
            }) { Text("Save category") }
        },
        dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
    )
    if (merge && previous != null) AlertDialog(
        onDismissRequest = { merge = false },
        title = { Text("Merge ${previous.orEmpty()}") },
        text = {
            Column {
                Text("All payments will move to the selected category, including ended payments. Its color will be kept.")
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(categories.filter { it.name != previous }, key = { it.name }) { category ->
                        ListItem(
                            headlineContent = { Text(category.name) },
                            leadingContent = { RadioButton(target == category.name, onClick = { target = category.name }) },
                            modifier = Modifier.clickable { target = category.name },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = target != null, onClick = {
                val destination = target ?: return@TextButton
                if (save(book.mergeCategory(requireNotNull(previous), destination))) {
                    merge = false
                    editing = false
                }
            }) { Text("Merge categories") }
        },
        dismissButton = { TextButton(onClick = { merge = false }) { Text("Cancel") } },
    )
    if (removing && previous != null) AlertDialog(
        onDismissRequest = { removing = false },
        title = { Text("Remove category?") },
        text = { Text("Payments in ${previous.orEmpty()} will become uncategorized.") },
        confirmButton = {
            TextButton(onClick = {
                if (save(book.copy(
                    categories = categories.filterNot { it.name == previous },
                    payments = book.payments.map { if (it.category == previous) it.copy(category = "") else it },
                ))) {
                    removing = false
                    editing = false
                }
            }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Remove") }
        },
        dismissButton = { TextButton(onClick = { removing = false }) { Text("Keep category") } },
    )
}
