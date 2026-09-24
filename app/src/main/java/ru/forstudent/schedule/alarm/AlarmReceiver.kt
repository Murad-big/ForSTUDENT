package ru.forstudent.schedule.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val date = intent.getStringExtra("date") ?: return
        ContextCompat.startForegroundService(context,
            Intent(context, RingingService::class.java).putExtra("date", date))
    }
}
