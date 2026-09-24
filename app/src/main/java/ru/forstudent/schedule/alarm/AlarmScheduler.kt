package ru.forstudent.schedule.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import ru.forstudent.schedule.data.ScheduleSettings
import ru.forstudent.schedule.data.ScheduleSnapshot
import ru.forstudent.schedule.domain.AlarmPlanner
import ru.forstudent.schedule.domain.PlannedAlarm
import java.time.Duration
import java.time.LocalDate

class AlarmScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)
    private val settings = ScheduleSettings(context)
    private val prefs = context.getSharedPreferences("scheduled_alarms", Context.MODE_PRIVATE)

    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()

    fun notificationsAllowed(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun fullScreenAllowed(): Boolean = Build.VERSION.SDK_INT < 34 ||
        context.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()

    fun planned(snapshot: ScheduleSnapshot): List<PlannedAlarm> = AlarmPlanner.plan(
        snapshot.lessons, snapshot.publishedDates, settings.alarmsEnabled,
        Duration.ofMinutes(settings.leadMinutes.toLong()), settings.skipDate,
    )

    /** Stable per-date PendingIntents mean repeated syncs never add duplicates. */
    fun reconcile(snapshot: ScheduleSnapshot, force: Boolean = false): List<PlannedAlarm> {
        val desired = planned(snapshot)
        val old = saved()
        if (!exactAllowed()) return desired
        val wanted = desired.associate { it.date.toString() to it.triggerAt.toEpochMilli() }
        val changes = AlarmReconciliation.diff(old, wanted, force)
        changes.cancel.forEach { manager.cancel(pending(it)) }
        changes.schedule.forEach { (date, millis) ->
            manager.cancel(pending(date))
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending(date))
        }
        prefs.edit().putStringSet("items", wanted.map { "${it.key}=${it.value}" }.toSet()).apply()
        return desired
    }

    fun cancelAll() {
        saved().keys.forEach { manager.cancel(pending(it)) }
        prefs.edit().remove("items").apply()
    }

    fun snooze(date: LocalDate) {
        if (!exactAllowed()) return
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 10 * 60_000L, pending(date.toString(), snooze = true))
    }

    private fun saved(): Map<String, Long> = prefs.getStringSet("items", emptySet()).orEmpty().mapNotNull {
        val split = it.split('=')
        if (split.size == 2) split[1].toLongOrNull()?.let { millis -> split[0] to millis } else null
    }.toMap()

    private fun pending(date: String, snooze: Boolean = false): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            data = Uri.parse("forstudent://alarm/${if (snooze) "snooze/" else ""}$date")
            putExtra("date", date)
        }
        return PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

data class AlarmChanges(val cancel: Set<String>, val schedule: Map<String, Long>)

object AlarmReconciliation {
    fun diff(old: Map<String, Long>, wanted: Map<String, Long>, force: Boolean = false): AlarmChanges =
        AlarmChanges(old.keys - wanted.keys, wanted.filter { (date, time) -> force || old[date] != time })
}
