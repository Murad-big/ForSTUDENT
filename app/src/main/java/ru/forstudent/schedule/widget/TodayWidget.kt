package ru.forstudent.schedule.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.forstudent.schedule.data.ScheduleRepository
import ru.forstudent.schedule.domain.DaySchedule
import ru.forstudent.schedule.domain.AlarmPlanner
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class TodayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = withContext(Dispatchers.IO) { ScheduleRepository(context).snapshot() }
        val today = LocalDate.now(AlarmPlanner.zone)
        val day = snapshot.day(today)
        val title = today.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale.forLanguageTag("ru")))
        provideContent {
            val compact = LocalSize.current.height < 110.dp
            Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
                Text("Сегодня · $title")
                when (day) {
                    DaySchedule.Unpublished -> Text("Расписание ещё не опубликовано")
                    DaySchedule.PublishedEmpty -> Text("Сегодня пар нет")
                    is DaySchedule.WithLessons -> {
                        val upcoming = day.lessons.firstOrNull { it.end > LocalTime.now(AlarmPlanner.zone) }
                        if (upcoming != null) Text("Ближайшая: ${upcoming.start} ${upcoming.subject}")
                        day.lessons.take(if (compact) 2 else 6).forEach { lesson ->
                            Row { Text("${lesson.start}  ${lesson.subject}  ${lesson.room}") }
                        }
                        if (compact && day.lessons.size > 2) Text("Ещё ${day.lessons.size - 2} пар")
                    }
                }
            }
        }
    }

    companion object {
        suspend fun updateAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(TodayWidget::class.java).forEach { TodayWidget().update(context, it) }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
