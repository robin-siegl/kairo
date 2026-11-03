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

private const val TRIALS = "trials"
private const val RENEWALS = "renewals"
private const val ALARM = "dev.robinsiegl.kairo.REMIND"

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            Reminders(context).refresh(Storage(context).read(), deliver = intent.action == ALARM)
        }.onFailure { Log.e("Kairo", "Could not refresh reminders", it) }
    }
}

class Reminders(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val alarm = context.getSystemService(AlarmManager::class.java)
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
        manager.getNotificationChannel(if (trial) TRIALS else RENEWALS)?.importance !=
        NotificationManager.IMPORTANCE_NONE

    fun refresh(book: Book, deliver: Boolean = false) {
        createChannels()
        val trigger = PendingIntent.getBroadcast(
            context, 0, Intent(context, ReminderReceiver::class.java).setAction(ALARM),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarm.cancel(trigger)
        if (!enabled()) return

        val now = ZonedDateTime.now()
        val today = now.toLocalDate()
        val prefs = book.preferences
        val sent = delivered.all
        val editor = delivered.edit()
        sent.filterValues {
            val date = it as? String
            date == null || LocalDate.parse(date) < today
        }.keys.forEach(editor::remove)
        var next: Long? = null

        for (payment in book.payments) {
            val trial = payment.isTrial(today)
            if (payment.cancelled) manager.cancel(payment.id, 0)
            if (!channelEnabled(trial) || (trial && !prefs.trialReminders) || (!trial && !payment.remind)) continue
            val firstDate = if (trial) payment.trialDeadline(today) else payment.nextCharge(today)?.date
            val target = firstDate?.takeIf { it >= today } ?: continue
            val lead = if (trial) prefs.trialLead else prefs.renewalLead
            val key = "${payment.id}:$trial:$target"
            val event = if (key !in sent) target else {
                if (trial) continue
                payment.nextCharge(target.plusDays(1))?.date ?: continue
            }
            val dateTime = event.minusDays(lead.toLong())
                .atTime(prefs.reminderHour, prefs.reminderMinute).atZone(now.zone)
            if (deliver && dateTime <= now) {
                notify(payment, event, trial)
                editor.putString("${payment.id}:$trial:$event", event.toString())
                if (!trial) {
                    val following = payment.nextCharge(event.plusDays(1))?.date ?: continue
                    val nextTime = following.minusDays(lead.toLong())
                        .atTime(prefs.reminderHour, prefs.reminderMinute).atZone(now.zone)
                    next = minOf(next ?: Long.MAX_VALUE, nextTime.toInstant().toEpochMilli())
                }
            } else {
                next = minOf(next ?: Long.MAX_VALUE, dateTime.toInstant().toEpochMilli())
            }
        }
        editor.apply()
        next?.let {
            alarm.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, maxOf(it, now.toInstant().toEpochMilli() + 1_000), trigger,
            )
        }
    }

    private fun notify(payment: Payment, event: LocalDate, trial: Boolean) {
        val open = PendingIntent.getActivity(
            context, payment.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .setData(Uri.fromParts("kairo", payment.id, null))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("payment", payment.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val message = when {
            trial && payment.cancelBy != null -> "Cancel by ${formatDate(event)}"
            trial -> "First charge on ${formatDate(event)}"
            else -> "Renews on ${formatDate(event)}"
        }
        val notification = Notification.Builder(context, if (trial) TRIALS else RENEWALS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(payment.name)
            .setContentText(message)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(payment.id, 0, notification)
    }
}
