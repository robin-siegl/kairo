package dev.robinsiegl.kairo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun ServicePicker(choose: (ServicePreset?) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query) { matchingServices(query) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Search services") }, leadingIcon = { Icon(Icons.Default.Search, null) })
            }
            item {
                Text("Choose a service, then enter your price and charge date.", style = MaterialTheme.typography.bodySmall)
            }
            if (matches.isEmpty()) item { Text("No matching services. You can add a custom payment.") }
            items(matches, key = { it.id }) { service ->
                ListItem(
                    modifier = Modifier.clip(MaterialTheme.shapes.large).clickable(role = Role.Button) { choose(service) },
                    headlineContent = { Text(service.name) },
                    supportingContent = { Text(service.category) },
                    leadingContent = {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { PaymentSymbolIcon(service.icon) }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { choose(null) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Custom payment") },
        )
    }
}
