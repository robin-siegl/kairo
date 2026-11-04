package dev.robinsiegl.kairo

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
        instrumentation.runOnMainSync {
            for (kind in WidgetKind.entries) {
                for (book in listOf(Book(), Book(payments = listOf(trial)))) {
                    val views = WidgetUpdates.createViews(context, AppWidgetManager.getInstance(context), 99999, kind, book)
                    val root = views.apply(context, null)
                    assertNotNull(root.findViewById<View>(R.id.widget_settings))
                    assertNotNull(root.findViewById<TextView>(R.id.widget_period).text)
                }
            }
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
