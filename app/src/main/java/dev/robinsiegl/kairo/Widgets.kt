package dev.robinsiegl.kairo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

internal data class WidgetOptions(val days: Int = 14, val hideAmounts: Boolean = false, val wallpaper: Boolean = false)

internal fun widgetOptions(context: Context, id: Int): WidgetOptions {
    val prefs = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    return WidgetOptions(if (prefs.getInt("$id.days", 14) == 7) 7 else 14,
        prefs.getBoolean("$id.hide", false), prefs.getBoolean("$id.wallpaper", false))
}

open class KairoWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_CONFIGURATION_CHANGED, Intent.ACTION_USER_UNLOCKED)) {
            refresh(context)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        refresh(context)

    override fun onDeleted(context: Context, ids: IntArray) {
        val prefs = context.getSharedPreferences("widgets", Context.MODE_PRIVATE).edit()
        ids.forEach { id -> listOf("days", "hide", "wallpaper").forEach { prefs.remove("$id.$it") } }
        prefs.apply()
    }

    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        val prefs = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
        val options = oldIds.map { widgetOptions(context, it) }
        val edit = prefs.edit()
        newIds.forEachIndexed { index, id ->
            val value = options[index]
            edit.putInt("$id.days", value.days).putBoolean("$id.hide", value.hideAmounts)
                .putBoolean("$id.wallpaper", value.wallpaper)
        }
        edit.apply()
    }

    private fun refresh(context: Context) {
        val pending = goAsync()
        WidgetUpdates.enqueue(context) { pending.finish() }
    }
}

class UpcomingWidget : KairoWidgetProvider()
class TrialWidget : KairoWidgetProvider()
class NextPaymentWidget : KairoWidgetProvider()
class MonthWidget : KairoWidgetProvider()

internal object WidgetUpdates {
    private val executor = Executors.newSingleThreadExecutor()
    private val providers = listOf(
        UpcomingWidget::class.java to WidgetKind.Upcoming,
        TrialWidget::class.java to WidgetKind.Trials,
        NextPaymentWidget::class.java to WidgetKind.Next,
        MonthWidget::class.java to WidgetKind.Month,
    )

    fun enqueue(context: Context, finished: () -> Unit = {}) {
        val app = context.applicationContext
        executor.execute {
            try {
                val manager = AppWidgetManager.getInstance(app)
                val active = providers.map { (provider, kind) ->
                    manager.getAppWidgetIds(ComponentName(app, provider)) to kind
                }
                if (active.all { it.first.isEmpty() }) return@execute
                val result = runCatching { Storage(app).read() }
                active.forEach { (ids, kind) ->
                    ids.forEach { id ->
                        manager.updateAppWidget(id, createViews(app, manager, id, kind, result.getOrNull()))
                    }
                }
            } catch (error: Exception) {
                Log.e("Kairo", "Could not update widgets", error)
            } finally {
                finished()
            }
        }
    }

    private fun launch(context: Context, id: Int, payment: String? = null, month: Boolean = false): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("kairo://widget/$id/${payment ?: if (month) "month" else "overview"}"))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (payment != null) intent.putExtra("payment", payment)
        if (month) intent.putExtra("calendar_month", YearMonth.now().toString())
        return PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    internal fun createViews(context: Context, manager: AppWidgetManager, id: Int, kind: WidgetKind, book: Book?): RemoteViews {
        val options = widgetOptions(context, id)
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val background = if (options.wallpaper && Build.VERSION.SDK_INT >= 31) {
            context.getColor(if (dark) android.R.color.system_accent1_800 else android.R.color.system_accent1_100)
        } else Color.parseColor(if (dark) "#152B58" else "#E7EEFF")
        val foreground = if (options.wallpaper && Build.VERSION.SDK_INT >= 31) {
            context.getColor(if (dark) android.R.color.system_accent1_50 else android.R.color.system_accent1_900)
        } else Color.parseColor(if (dark) "#FFFFFF" else "#14254A")
        val views = RemoteViews(context.packageName, R.layout.widget_kairo)
        views.setInt(R.id.widget_background, "setColorFilter", background)
        views.setTextViewText(R.id.widget_title, kind.title)
        listOf(R.id.widget_title, R.id.widget_period, R.id.widget_total).forEach { views.setTextColor(it, foreground) }
        views.setInt(R.id.widget_settings, "setColorFilter", foreground)
        val settings = Intent(context, WidgetSettingsActivity::class.java)
            .setData(Uri.parse("kairo://widget/$id/settings"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        views.setOnClickPendingIntent(R.id.widget_settings, PendingIntent.getActivity(context, id, settings,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        views.setOnClickPendingIntent(R.id.widget_root, launch(context, id, month = kind == WidgetKind.Month))
        views.removeAllViews(R.id.widget_rows)
        if (book == null) {
            views.setTextViewText(R.id.widget_period, "Open Kairo to review your data")
            views.setViewVisibility(R.id.widget_total, View.GONE)
            return views
        }
        val today = LocalDate.now()
        val entries = book.widgetEntries(kind, today, options.days)
        val currency = book.preferences.defaultCurrency
        val height = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
        val rowCount = ((height - if (kind == WidgetKind.Trials) 102 else 132) / 56).coerceIn(1, 8)
        val compact = height < 200 && kind in listOf(WidgetKind.Next, WidgetKind.Upcoming)
        if (compact) {
            views.setTextViewText(R.id.widget_title, WidgetKind.Next.title)
            entries.firstOrNull()?.let {
                views.setOnClickPendingIntent(R.id.widget_root, launch(context, id, it.payment.id))
            }
        }
        val visible = if (kind == WidgetKind.Month || compact) emptyList() else entries.take(if (kind == WidgetKind.Next) 1 else rowCount)
        val period = when (kind) {
            WidgetKind.Month -> YearMonth.from(today).format(DateTimeFormatter.ofPattern("LLLL yyyy"))
            WidgetKind.Trials -> if (entries.isEmpty()) "No trials need your attention" else "${entries.size} trials to review"
            WidgetKind.Next -> if (entries.isEmpty()) "No upcoming payments" else "Next scheduled charge"
            else -> "${formatDate(today)} – ${formatDate(today.plusDays(options.days.toLong() - 1))}"
        }
        views.setTextViewText(R.id.widget_period, if (compact && entries.isNotEmpty()) {
            val entry = entries.first()
            "${entry.payment.name}, ${formatDate(entry.date)}"
        } else period)
        val total = entries.sumOf { it.amount ?: BigDecimal.ZERO }
        val summary = when {
            kind == WidgetKind.Trials -> ""
            kind == WidgetKind.Next && entries.isEmpty() -> "You’re all clear"
            options.hideAmounts -> "Amounts hidden"
            else -> money(if (compact) entries.firstOrNull()?.amount ?: BigDecimal.ZERO else total, currency)
        }
        views.setTextViewText(R.id.widget_total, summary)
        views.setViewVisibility(R.id.widget_total, if (kind == WidgetKind.Trials) View.GONE else View.VISIBLE)
        if (kind == WidgetKind.Month) {
            val remaining = entries.filter { it.date >= today }.sumOf { it.amount ?: BigDecimal.ZERO }
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            row.setViewVisibility(R.id.widget_icon, View.GONE)
            row.setTextViewText(R.id.widget_name, "Still due this month")
            row.setTextViewText(R.id.widget_date, "Including today")
            row.setTextViewText(R.id.widget_amount, if (options.hideAmounts) "" else money(remaining, currency))
            listOf(R.id.widget_name, R.id.widget_date, R.id.widget_amount).forEach { row.setTextColor(it, foreground) }
            row.setOnClickPendingIntent(R.id.widget_row, launch(context, id, month = true))
            views.addView(R.id.widget_rows, row)
        }
        visible.forEach { entry ->
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            row.setImageViewResource(R.id.widget_icon, paymentSymbol(entry.payment.icon).drawable)
            row.setInt(R.id.widget_icon, "setColorFilter", foreground)
            row.setTextViewText(R.id.widget_name, entry.payment.name)
            val date = when (entry.date) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> formatDate(entry.date)
            }
            val label = if (kind == WidgetKind.Trials && entry.date < today) {
                "Deadline passed, $date"
            } else if (kind == WidgetKind.Trials) {
                val prefix = if (entry.payment.cancelBy != null) "Cancel by" else "First charge"
                "$prefix $date"
            } else date
            row.setTextViewText(R.id.widget_date, label)
            row.setTextViewText(R.id.widget_amount,
                if (options.hideAmounts || entry.amount == null) "" else money(entry.amount, currency))
            listOf(R.id.widget_name, R.id.widget_date, R.id.widget_amount).forEach { row.setTextColor(it, foreground) }
            row.setOnClickPendingIntent(R.id.widget_row, launch(context, id, entry.payment.id))
            views.addView(R.id.widget_rows, row)
        }
        if (kind == WidgetKind.Upcoming && entries.isEmpty()) {
            views.setTextViewText(R.id.widget_total, "You’re all clear")
        }
        return views
    }
}
