package dev.robinsiegl.kairo

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.util.Log
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.serialization.json.Json

private const val TRIALS = "trials"
private const val RENEWALS = "renewals"
private const val ALARM = "dev.robinsiegl.kairo.REMIND"
private const val SNOOZE = "dev.robinsiegl.kairo.SNOOZE"
private const val SNOOZES_KEY = "__snoozes"
private val reminderJson = Json

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            val reminders = Reminders(context)
            val book = Storage(context).read()
            if (intent.action == SNOOZE) {
                reminders.snooze(book, intent.getStringExtra("payment").orEmpty(),
                    intent.getStringExtra("event").orEmpty(), intent.getBooleanExtra("trial", false))
            }
            reminders.refresh(book, deliver = intent.action == ALARM)
        }.onFailure { Log.e("Kairo", "Could not refresh reminders", it) }
    }
}

class Reminders(
    private val context: Context,
    private val currentTime: () -> ZonedDateTime = { ZonedDateTime.now() },
) {
    private val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
    private val alarm = requireNotNull(context.getSystemService(AlarmManager::class.java))
    private val delivered = context.getSharedPreferences("delivered", Context.MODE_PRIVATE)

    fun createChannels() {
        manager.createNotificationChannels(listOf(
            NotificationChannel(TRIALS, "Trial deadlines", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(RENEWALS, "Renewals", NotificationManager.IMPORTANCE_DEFAULT),
        ))
    }

    fun enabled(): Boolean = manager.areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun channelEnabled(trial: Boolean): Boolean = enabled() &&
        manager.getNotificationChannel(if (trial) TRIALS else RENEWALS)?.importance != NotificationManager.IMPORTANCE_NONE

    fun dismiss(paymentId: String) {
        manager.cancel(paymentId, 0)
    }

    private fun snoozes(): List<SnoozedReminder> = runCatching {
        reminderJson.decodeFromString<List<SnoozedReminder>>(delivered.getString(SNOOZES_KEY, "[]") ?: "[]")
    }.getOrDefault(emptyList())

    internal fun snooze(book: Book, paymentId: String, eventDate: String, trial: Boolean) = synchronized(lock) {
        val now = currentTime()
        val event = reminderEvents(book, now).firstOrNull {
            it.payment.id == paymentId && it.event.toString() == eventDate && it.trial == trial
        }
        if (event == null || !channelEnabled(trial) || !event.canSnooze(now.toLocalDate())) return@synchronized
        val record = SnoozedReminder(paymentId, eventDate, trial, now.toLocalDate().plusDays(1).toString())
        val updated = snoozes().filterNot { it.identity == event.identity } + record
        delivered.edit().putString(SNOOZES_KEY, reminderJson.encodeToString(updated)).apply()
        dismiss(paymentId)
    }

    fun refresh(book: Book, deliver: Boolean = false): Unit = synchronized(lock) {
        createChannels()
        val trigger = PendingIntent.getBroadcast(
            context, 0, Intent(context, ReminderReceiver::class.java).setAction(ALARM),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarm.cancel(trigger)
        val now = currentTime()
        val today = now.toLocalDate()
        val allEvents = reminderEvents(book, now)
        val events = allEvents.filter { channelEnabled(it.trial) }
        val snoozed = validSnoozes(snoozes(), allEvents, today)
        val prefs = book.preferences
        val times = snoozed.associate { snooze ->
            snooze.identity to LocalDate.parse(snooze.dueDate)
                .atTime(prefs.reminderHour, prefs.reminderMinute).atZone(now.zone)
        }
        val editor = delivered.edit()
        val sent = delivered.all.filterKeys { it != SNOOZES_KEY }.filter { (_, value) ->
            val date = runCatching { LocalDate.parse(value as? String ?: "") }.getOrNull()
            date != null && date >= today
        }.keys.toMutableSet()
        delivered.all.keys.filter { it != SNOOZES_KEY && it !in sent }.forEach(editor::remove)
        val unsent = events.filter { it.key !in sent }
        val ready = unsent.filter { it.at <= now && (times[it.identity]?.let { time -> time <= now } != false) }
        val snoozedDue = events.filter { times[it.identity]?.let { time -> time <= now } == true }
            .groupBy { it.identity }.values.map { group -> group.minBy { it.at } }
        val notifications = (ready + snoozedDue).distinctBy { it.key }.groupBy { it.payment.id }
        val consumed = mutableSetOf<String>()
        if (deliver && enabled()) notifications.values.forEach { due ->
            val nearest = due.filter { it.event > today }.minByOrNull { it.event } ?: due.minBy { it.event }
            notify(nearest, now)
            due.forEach {
                editor.putString(it.key, it.event.toString())
                sent.add(it.key)
                consumed.add(it.identity)
            }
        }
        val retained = snoozed.filterNot { it.identity in consumed }
        editor.putString(SNOOZES_KEY, reminderJson.encodeToString(retained)).apply()
        if (!enabled()) return@synchronized
        val activeIdentities = events.map { it.identity }.toSet()
        val snoozeTimes = retained.filter { it.identity in activeIdentities }.mapNotNull { times[it.identity] }
        val next = events.filter { it.key !in sent }.map { event ->
            maxOf(event.at, times[event.identity] ?: event.at)
        }.plus(snoozeTimes).minOrNull()
        next?.let {
            alarm.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                maxOf(it.toInstant().toEpochMilli(), now.toInstant().toEpochMilli() + 1_000),
                trigger,
            )
        }
        Unit
    }

    internal fun notification(event: ReminderEvent, now: ZonedDateTime): Notification {
        val payment = event.payment
        val open = PendingIntent.getActivity(
            context, payment.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .setData(Uri.fromParts("kairo", payment.id, null))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("payment", payment.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val message = when {
            event.trial && payment.cancelBy != null -> "Cancel by ${formatDate(event.event)}"
            event.trial -> "First charge on ${formatDate(event.event)}"
            else -> "Renews on ${formatDate(event.event)}"
        }
        val builder = Notification.Builder(context, if (event.trial) TRIALS else RENEWALS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(payment.name)
            .setContentText(message)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .setAutoCancel(true)
        if (event.canSnooze(now.toLocalDate())) {
            val intent = Intent(context, ReminderReceiver::class.java).setAction(SNOOZE)
                .setData(Uri.parse("kairo://snooze").buildUpon().appendPath(payment.id)
                    .appendPath(event.event.toString()).appendPath(event.trial.toString()).build())
                .putExtra("payment", payment.id).putExtra("event", event.event.toString())
                .putExtra("trial", event.trial)
            val action = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(Notification.Action.Builder(null, "Remind me tomorrow", action).build())
        }
        return builder.build()
    }

    private fun notify(event: ReminderEvent, now: ZonedDateTime) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        manager.notify(event.payment.id, 0, notification(event, now))
    }

    fun test(trial: Boolean): Boolean {
        createChannels()
        if (!channelEnabled(trial)) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val open = PendingIntent.getActivity(context, if (trial) 1 else 2,
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("kairo://test/${if (trial) "trial" else "renewal"}")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(context, if (trial) TRIALS else RENEWALS)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Kairo test reminder")
            .setContentText(if (trial) "Trial notifications are ready." else "Renewal notifications are ready.")
            .setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open)
            .setAutoCancel(true).setTimeoutAfter(60_000).build()
        manager.notify("test", if (trial) 1 else 2, notification)
        return true
    }

    companion object {
        private val lock = Any()
    }
}
