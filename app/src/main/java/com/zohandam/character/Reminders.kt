package com.zohandam.character

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
import java.time.LocalDate
import java.time.ZonedDateTime

object Reminders {
    const val CHANNEL = "weekly_trait"

    fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Trait reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Your current weekly trait at your chosen daily times"
                enableVibration(false)
            }
        )
    }

    fun permitted(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun pending(context: Context, minutes: Int) = PendingIntent.getBroadcast(
        context, minutes, Intent(context, ReminderReceiver::class.java).putExtra("minutes", minutes),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(context: Context) {
        channel(context)
        val store = Store(context)
        val manager = context.getSystemService(AlarmManager::class.java)
        // Cancel previous entries as well as removed times, without creating new PendingIntents.
        val scheduled = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)
        scheduled.getStringSet("times", emptySet())!!.forEach { item ->
            item.toIntOrNull()?.let { manager.cancel(pending(context, it)) }
        }
        val times = if (store.enabled && permitted(context)) store.times else emptyList()
        times.forEach { scheduleNext(context, it) }
        scheduled.edit().putStringSet("times", times.map { it.toString() }.toSet()).apply()
    }

    fun scheduleNext(context: Context, minutes: Int) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val trigger = Schedule.nextReminder(minutes, ZonedDateTime.now()).toInstant().toEpochMilli()
        val operation = pending(context, minutes)
        if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
            } catch (_: SecurityException) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
            }
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
        }
    }

    fun show(context: Context) {
        channel(context)
        if (!permitted(context)) return
        val store = Store(context)
        val traits = store.traits()
        val trait = traits[Schedule.traitIndex(store.anchor, LocalDate.now(), traits.size)]
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("This week: ${trait.name}")
            .setContentText(trait.definition)
            .setStyle(Notification.BigTextStyle().bigText(trait.definition))
            .setContentIntent(open).setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(1, notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = Store(context)
        val minutes = intent.getIntExtra("minutes", -1)
        if (store.enabled && minutes in store.times && Reminders.permitted(context)) {
            Reminders.show(context)
            // Leave other pending times intact, even if Android delivers this alarm late.
            Reminders.scheduleNext(context, minutes)
        }
    }
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                Reminders.schedule(context)
        }
    }
}
