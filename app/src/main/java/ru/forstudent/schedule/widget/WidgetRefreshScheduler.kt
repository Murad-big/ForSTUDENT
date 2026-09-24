package ru.forstudent.schedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.forstudent.schedule.data.ScheduleSnapshot
import ru.forstudent.schedule.domain.AlarmPlanner
import java.time.Instant
import java.time.ZonedDateTime

/** Update the current/next lesson label at its next time boundary, even without opening the app. */
fun nextWidgetRefreshAt(snapshot: ScheduleSnapshot, now: ZonedDateTime): Instant {
    val day = now.withZoneSameInstant(AlarmPlanner.zone).toLocalDate()
    val boundaries = snapshot.lessons.asSequence().filter { it.date == day }
        .flatMap { sequenceOf(it.start, it.end) }
        .map { day.atTime(it).atZone(AlarmPlanner.zone) }
    return (boundaries + sequenceOf(day.plusDays(1).atStartOfDay(AlarmPlanner.zone)))
        .filter { it.isAfter(now) }.minOrNull()!!.toInstant()
}

class WidgetRefreshScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)

    fun scheduleNext(snapshot: ScheduleSnapshot) {
        val trigger = nextWidgetRefreshAt(snapshot, ZonedDateTime.now(AlarmPlanner.zone))
        manager.cancel(pending())
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.toEpochMilli(), pending())
    }

    private fun pending(): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, WidgetRefreshReceiver::class.java).apply { data = Uri.parse("forstudent://widget/refresh") },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { TodayWidget.updateAll(context) } finally { pending.finish() }
        }
    }
}
