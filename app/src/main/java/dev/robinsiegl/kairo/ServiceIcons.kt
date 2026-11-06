package dev.robinsiegl.kairo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

// A neutral plate keeps fixed-color marks separate from wallpaper-derived accent colors.
@Composable
internal fun paymentIconContainer(id: String): Color =
    if (paymentSymbol(id).brand) Color(0xFF202124) else MaterialTheme.colorScheme.primaryContainer

@Composable
internal fun PaymentSymbolIcon(id: String) {
    val symbol = paymentSymbol(id)
    Icon(painterResource(symbol.drawable), contentDescription = null, modifier = Modifier.size(24.dp),
        tint = if (symbol.brand) Color.Unspecified else LocalContentColor.current)
}

@Composable
internal fun ServiceIconCredits() {
    var open by rememberSaveable { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    SettingsRow("Service icon credits", "Sources and brand information") { open = true }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Service icons") },
        text = {
            Column {
                Text("Proton service marks identify payments you enter yourself. Kairo is not affiliated with Proton.")
                Spacer(Modifier.height(12.dp))
                Text("Icons use CC0 renditions from Simple Icons, based on the Proton media kit. Other services use generic symbols.")
                TextButton(onClick = { runCatching { uri.openUri("https://proton.me/") } }) { Text("Visit proton.me") }
                TextButton(onClick = { runCatching { uri.openUri("https://proton.me/media/kit") } }) { Text("Proton media kit") }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
    )
}
