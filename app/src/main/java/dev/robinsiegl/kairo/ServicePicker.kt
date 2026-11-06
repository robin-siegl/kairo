package dev.robinsiegl.kairo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ServicePicker(choose: (ServicePreset?) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query) { matchingServices(query) }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Start from a service or add your own payment.")
        OutlinedButton(onClick = { choose(null) }, modifier = Modifier.fillMaxWidth()) { Text("Add a custom payment") }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Search services") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        Text("Presets fill in a few details. You choose the price and charge date.", style = MaterialTheme.typography.bodySmall)
        if (matches.isEmpty()) Text("No matching services. You can add a custom payment.")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(matches, key = { it.id }) { service ->
                ListItem(
                    modifier = Modifier.clickable { choose(service) },
                    headlineContent = { Text(service.name) },
                    supportingContent = { Text(service.category) },
                    leadingContent = { PaymentSymbolIcon(service.icon) },
                )
            }
        }
    }
}
