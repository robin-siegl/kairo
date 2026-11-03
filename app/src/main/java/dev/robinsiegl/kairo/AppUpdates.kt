package dev.robinsiegl.kairo

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val REPOSITORY = "robin-siegl/kairo"
private val stableTag = Regex("v(0|[1-9][0-9]{0,3})\\.(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})")

private sealed interface UpdateResult {
    data object Checking : UpdateResult
    data object UpToDate : UpdateResult
    data object Unpublished : UpdateResult
    data object Failed : UpdateResult
    data class Available(val version: String, val apk: String) : UpdateResult
}

private fun checkLatestRelease(): UpdateResult {
    val connection = URI("https://api.github.com/repos/$REPOSITORY/releases/latest").toURL()
        .openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 7_000
        connection.readTimeout = 7_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        when (connection.responseCode) {
            404 -> return UpdateResult.Unpublished
            200 -> Unit
            else -> return UpdateResult.Failed
        }
        val release = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        val tag = release.getString("tag_name")
        val match = stableTag.matchEntire(tag) ?: return UpdateResult.Failed
        val (major, minor, patch) = match.destructured
        val versionCode = major.toLong() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt()
        if (versionCode <= BuildConfig.VERSION_CODE) return UpdateResult.UpToDate

        val version = tag.removePrefix("v")
        val filename = "kairo-$version.apk"
        val apk = "https://github.com/$REPOSITORY/releases/download/$tag/$filename"
        val assets = release.getJSONArray("assets")
        if ((0 until assets.length()).none { assets.getJSONObject(it).optString("browser_download_url") == apk }) {
            return UpdateResult.Failed
        }
        return UpdateResult.Available(version, apk)
    } finally {
        connection.disconnect()
    }
}

@Composable
internal fun AppUpdates() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var status by remember { mutableStateOf<UpdateResult?>(null) }
    var dialogOpen by remember { mutableStateOf(false) }

    SettingsRow("App updates", "Check for a new Kairo release") { dialogOpen = true }
    if (dialogOpen) {
        val result = status
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text("App updates") },
            text = {
                Text(when (result) {
                    null -> "Kairo ${BuildConfig.VERSION_NAME} is installed."
                    UpdateResult.Checking -> "Checking GitHub for updates…"
                    UpdateResult.UpToDate -> "Kairo is up to date."
                    UpdateResult.Unpublished -> "There’s no published release to check yet."
                    UpdateResult.Failed -> "Couldn’t check for updates. Try again in a moment."
                    is UpdateResult.Available -> "Kairo ${result.version} is available."
                })
            },
            confirmButton = {
                if (result is UpdateResult.Available) {
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.apk))) }
                            .onFailure { status = UpdateResult.Failed }
                    }) { Text("Download APK") }
                } else {
                    TextButton(
                        enabled = result != UpdateResult.Checking,
                        onClick = {
                            status = UpdateResult.Checking
                            scope.launch {
                                status = withContext(Dispatchers.IO) {
                                    runCatching(::checkLatestRelease).getOrDefault(UpdateResult.Failed)
                                }
                            }
                        },
                    ) { Text("Check for updates") }
                }
            },
            dismissButton = { TextButton(onClick = { dialogOpen = false }) { Text("Close") } },
        )
    }
}
