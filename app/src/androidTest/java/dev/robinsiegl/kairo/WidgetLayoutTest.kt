package dev.robinsiegl.kairo

import android.content.res.Configuration
import android.appwidget.AppWidgetManager
import android.view.View
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WidgetLayoutTest {
    @Test
    fun everyWidgetInflatesForEmptyAndPopulatedData() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val today = LocalDate.now()
        val trial = Payment(
            name = "Focus",
            terms = listOf(Terms(today.plusDays(3).toString(), "9.99", Interval.Monthly)),
            trial = true,
            cancelBy = today.plusDays(1).toString(),
        )
        val id = 99999
        try {
            instrumentation.runOnMainSync {
                for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                    val config = Configuration(context.resources.configuration)
                    config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
                    val themed = context.createConfigurationContext(config)
                    for (wallpaper in listOf(false, true)) {
                        context.getSharedPreferences("widgets", 0).edit().putBoolean("$id.wallpaper", wallpaper).commit()
                        for (kind in WidgetKind.entries) {
                            for (book in listOf(Book(), Book(payments = listOf(trial)))) {
                                val views = WidgetUpdates.createViews(themed, AppWidgetManager.getInstance(context), id, kind, book)
                                val root = views.apply(themed, null)
                                assertNotNull(root.findViewById<View>(R.id.widget_settings))
                                assertNotNull(root.findViewById<TextView>(R.id.widget_period).text)
                            }
                        }
                    }
                }
            }
        } finally {
            context.getSharedPreferences("widgets", 0).edit().remove("$id.wallpaper").commit()
        }
    }

    @Test
    fun hiddenAmountsDoNotAppearInWidgetText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val id = 99998
        context.getSharedPreferences("widgets", 0).edit().putBoolean("$id.hide", true).commit()
        try {
            instrumentation.runOnMainSync {
                val book = Book(payments = listOf(Payment(
                    name = "Focus",
                    terms = listOf(Terms(LocalDate.now().toString(), "987.65", Interval.Monthly)),
                )))
                val views = WidgetUpdates.createViews(context, AppWidgetManager.getInstance(context), id, WidgetKind.Next, book)
                val root = views.apply(context, null)
                assertEquals("Amounts hidden", root.findViewById<TextView>(R.id.widget_total).text.toString())
            }
        } finally {
            context.getSharedPreferences("widgets", 0).edit().remove("$id.hide").commit()
        }
    }
}
