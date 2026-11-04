package dev.robinsiegl.kairo

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class WidgetSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || AppWidgetManager.getInstance(this).getAppWidgetInfo(id) == null) {
            finish()
            return
        }
        val options = widgetOptions(this, id)
        val upcoming = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className == UpcomingWidget::class.java.name
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme(
                primary = Color(0xFFADC6FF), onPrimary = Color(0xFF002E6A),
                primaryContainer = Color(0xFF1B449C), onPrimaryContainer = Color(0xFFD8E5FF),
            ) else lightColorScheme(
                primary = Color(0xFF3367DB), onPrimary = Color.White,
                primaryContainer = Color(0xFFD8E5FF), onPrimaryContainer = Color(0xFF101B37),
            )) {
                Surface(Modifier.fillMaxSize()) {
                    var days by rememberSaveable { mutableStateOf(options.days) }
                    var hide by rememberSaveable { mutableStateOf(options.hideAmounts) }
                    var wallpaper by rememberSaveable { mutableStateOf(options.wallpaper) }
                    Column(
                        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Widget settings", style = MaterialTheme.typography.headlineSmall)
                        if (upcoming) {
                            Text("Upcoming period")
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf(7, 14).forEach {
                                    FilterChip(selected = days == it, onClick = { days = it }, label = { Text("$it days") })
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Hide amounts", Modifier.weight(1f))
                            Switch(hide, { hide = it })
                        }
                        if (Build.VERSION.SDK_INT >= 31) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Use wallpaper colors", Modifier.weight(1f))
                            Switch(wallpaper, { wallpaper = it })
                        }
                        Text("Colors follow your phone’s light or dark mode.")
                        Spacer(Modifier.weight(1f))
                        Button(onClick = {
                            getSharedPreferences("widgets", MODE_PRIVATE).edit()
                                .putInt("$id.days", days).putBoolean("$id.hide", hide)
                                .putBoolean("$id.wallpaper", wallpaper).apply()
                            WidgetUpdates.enqueue(this@WidgetSettingsActivity)
                            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                            finish()
                        }, modifier = Modifier.fillMaxWidth()) { Text("Save widget") }
                        TextButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                    }
                }
            }
        }
    }
}
