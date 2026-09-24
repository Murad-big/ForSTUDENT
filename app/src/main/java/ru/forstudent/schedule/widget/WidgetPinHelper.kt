package ru.forstudent.schedule.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object WidgetPinHelper {
    fun requestPin(context: Context, compact: Boolean): Boolean {
        val manager = context.getSystemService(AppWidgetManager::class.java)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val receiver = if (compact) CompactTodayWidgetReceiver::class.java else TodayWidgetReceiver::class.java
        return manager.requestPinAppWidget(ComponentName(context, receiver), null, null)
    }
}
