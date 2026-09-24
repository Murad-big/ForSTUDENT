package ru.forstudent.schedule.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.forstudent.schedule.data.ScheduleRepository
import ru.forstudent.schedule.widget.TodayWidget

class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmScheduler(context).reconcile(ScheduleRepository(context).snapshot(),
                    force = intent.action != Intent.ACTION_DATE_CHANGED)
                TodayWidget.updateAll(context)
            } finally { pending.finish() }
        }
    }
}
