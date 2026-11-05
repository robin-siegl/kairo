package dev.robinsiegl.kairo

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class ReminderDeliveryTest {
    @Test
    fun snoozingPrimaryReminderDoesNotConsumeTheFinalTrialReminder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("delivered", Context.MODE_PRIVATE)
        val original = prefs.all
        val originalBook = Storage(context).read()
        val hadPermission = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!hadPermission) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val payment = Payment(name = "Trial test",
            terms = listOf(Terms("2026-11-13", "9.99", Interval.Monthly)), trial = true, cancelBy = "2026-11-11")
        val book = Book(payments = listOf(payment), preferences = Preferences(secondTrialReminder = true))
        var now = ZonedDateTime.parse("2026-11-08T10:00:00+01:00[Europe/Vienna]")
        try {
            prefs.edit().clear().commit()
            val reminders = Reminders(context) { now }
            reminders.refresh(book, deliver = true)
            val events = reminderEvents(book, now)
            assertNotNull(prefs.getString(events.first().key, null))
            assertNull(prefs.getString(events.last().key, null))
            val notification = reminders.notification(events.first(), now)
            assertEquals("Remind me tomorrow", notification.actions.single().title.toString())
            reminders.snooze(book, payment.id, "2026-11-11", true)
            reminders.refresh(book)
            now = now.plusDays(1)
            Reminders(context) { now }.refresh(book, deliver = true)
            assertNull(prefs.getString(events.last().key, null))
            now = now.plusDays(1)
            Reminders(context) { now }.refresh(book, deliver = true)
            assertNotNull(prefs.getString(events.last().key, null))
            val cancelled = book.copy(payments = listOf(payment.copy(cancelled = true)))
            reminders.refresh(cancelled)
            assertEquals("[]", prefs.getString("__snoozes", null))
        } finally {
            context.getSystemService(NotificationManager::class.java).cancel(payment.id, 0)
            val editor = prefs.edit().clear()
            original.forEach { (key, value) -> if (value is String) editor.putString(key, value) }
            editor.commit()
            Reminders(context).refresh(originalBook)
        }
    }
}
