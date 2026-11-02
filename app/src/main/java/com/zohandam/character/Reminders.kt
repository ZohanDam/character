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
import java.time.Instant
import java.time.Duration

object Reminders {
    const val CHANNEL = "weekly_trait"
    private const val NOTIFICATION_ID = 1

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

    private fun pending(context: Context, minutes: Int, create: Boolean = true): PendingIntent? = PendingIntent.getBroadcast(
        context, minutes, Intent(context, ReminderReceiver::class.java).putExtra("minutes", minutes),
        (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or PendingIntent.FLAG_IMMUTABLE
    )

    fun precise(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun clearNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        context.getSharedPreferences("alarms", Context.MODE_PRIVATE).edit().remove("notificationWeek").apply()
    }

    fun schedule(context: Context, rebuild: Boolean = false) {
        channel(context)
        val store = Store(context)
        val manager = context.getSystemService(AlarmManager::class.java)
        val scheduled = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)
        val now = ZonedDateTime.now()
        val week = Schedule.sunday(now.toLocalDate()).toString()
        val postedWeek = scheduled.getString("notificationWeek", null)
        if (postedWeek != week) clearNotification(context)
        else if (rebuild && postedWeek == week) {
            val notifications = context.getSystemService(NotificationManager::class.java)
            notifications.activeNotifications.firstOrNull { it.id == NOTIFICATION_ID }?.let {
                val remaining = Duration.between(now, Schedule.nextSunday(now)).toMillis().coerceAtLeast(1)
                notifications.notify(NOTIFICATION_ID, Notification.Builder.recoverBuilder(context, it.notification)
                    .setTimeoutAfter(remaining).setOnlyAlertOnce(true).build())
            }
        }
        val times = if (store.enabled && permitted(context)) store.times else emptyList()
        val oldTimes = scheduled.getStringSet("times", emptySet())!!.mapNotNull { it.toIntOrNull() }
        val changes = scheduled.edit()
        oldTimes.filter { it !in times }.forEach { minutes ->
            pending(context, minutes, false)?.let { manager.cancel(it); it.cancel() }
            changes.remove("at_$minutes")
        }
        val exact = precise(context)
        val timingChanged = scheduled.contains("precise") && scheduled.getBoolean("precise", false) != exact
        times.forEach { minutes ->
            val exists = pending(context, minutes, false) != null
            val recorded = if (scheduled.contains("at_$minutes")) scheduled.getLong("at_$minutes", 0) else null
            val trigger = Schedule.reminderTrigger(minutes, recorded, exists, now, rebuild)
            if (rebuild || timingChanged || !exists || recorded != trigger) {
                register(context, minutes, trigger)
                changes.putLong("at_$minutes", trigger)
            }
        }
        changes.putBoolean("precise", exact).putStringSet("times", times.map { it.toString() }.toSet()).apply()
        if (!store.enabled && oldTimes.isNotEmpty()) clearNotification(context)
    }

    fun scheduleNext(context: Context, minutes: Int) {
        val trigger = Schedule.nextReminder(minutes, ZonedDateTime.now()).toInstant().toEpochMilli()
        register(context, minutes, trigger)
        context.getSharedPreferences("alarms", Context.MODE_PRIVATE).edit().putLong("at_$minutes", trigger).apply()
    }

    private fun register(context: Context, minutes: Int, trigger: Long) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val operation = pending(context, minutes)!!
        if (precise(context)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
            } catch (_: SecurityException) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
            }
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
        }
    }

    fun next(context: Context): ZonedDateTime? {
        val store = Store(context)
        if (!store.enabled || !permitted(context)) return null
        val scheduled = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)
        val trigger = store.times.map { scheduled.getLong("at_$it", 0) }.filter { it > 0 }.minOrNull() ?: return null
        return Instant.ofEpochMilli(trigger).atZone(ZonedDateTime.now().zone)
    }

    fun show(context: Context, test: Boolean = false): Boolean {
        channel(context)
        if (!permitted(context)) return false
        val store = Store(context)
        val traits = store.traits()
        val trait = traits[Schedule.traitIndex(store.anchor, LocalDate.now(), traits.size)]
        val now = ZonedDateTime.now()
        val expires = Duration.between(now, Schedule.nextSunday(now)).toMillis().coerceAtLeast(1)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(trait.name)
            .setSubText(if (test) "Test reminder" else "Weekly focus")
            .setContentText(trait.definition)
            .setStyle(Notification.BigTextStyle().bigText(trait.definition))
            .setContentIntent(open).setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setTimeoutAfter(expires)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        context.getSharedPreferences("alarms", Context.MODE_PRIVATE).edit()
            .putString("notificationWeek", Schedule.sunday(now.toLocalDate()).toString()).apply()
        return true
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
            Intent.ACTION_MY_PACKAGE_REPLACED ->
                Reminders.schedule(context, rebuild = true)
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> Reminders.schedule(context)
        }
    }
}
