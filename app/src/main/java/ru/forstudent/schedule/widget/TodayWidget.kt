package ru.forstudent.schedule.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.forstudent.schedule.MainActivity
import ru.forstudent.schedule.R
import ru.forstudent.schedule.data.ScheduleRepository
import ru.forstudent.schedule.data.ScheduleSettings
import ru.forstudent.schedule.domain.AlarmPlanner
import ru.forstudent.schedule.domain.DaySchedule
import ru.forstudent.schedule.domain.Lesson
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val navy = Color(0xFF14264A)
private val muted = Color(0xFF637188)
private val ru = Locale.forLanguageTag("ru")

open class TodayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    protected open val alwaysCompact = false

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = withContext(Dispatchers.IO) { ScheduleRepository(context).snapshot() }
        WidgetRefreshScheduler(context).scheduleNext(snapshot)
        val group = ScheduleSettings(context).group
        val today = LocalDate.now(AlarmPlanner.zone)
        val day = snapshot.day(today)
        provideContent {
            val size = LocalSize.current
            val compact = alwaysCompact || size.height < 140.dp
            val date = today.format(DateTimeFormatter.ofPattern("d MMMM", ru))
            val lessons = (day as? DaySchedule.WithLessons)?.lessons
                ?.sortedWith(compareBy({ it.start }, { it.slot }, { it.subject })).orEmpty()
            val focus = widgetLessonFocus(lessons, LocalTime.now(AlarmPlanner.zone))
            val widgetBackground = if (compact) R.drawable.widget_compact_background else R.drawable.widget_background

            Column(
                modifier = GlanceModifier.fillMaxSize()
                    .background(ImageProvider(widgetBackground))
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(if (compact) 11.dp else 16.dp),
            ) {
                if (compact) {
                    Text("$group · ${today.format(DateTimeFormatter.ofPattern("d MMMM", ru))}",
                        style = TextStyle(color = ColorProvider(navy), fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Spacer(GlanceModifier.height(3.dp))
                    when (day) {
                        DaySchedule.Unpublished -> WidgetMessage("Расписание ещё не опубликовано")
                        DaySchedule.PublishedEmpty -> WidgetMessage("Сегодня пар нет")
                        is DaySchedule.WithLessons -> {
                            val label = when (focus?.phase) {
                                LessonPhase.CURRENT -> "Сейчас идёт пара"
                                LessonPhase.NEXT -> "Следующая пара"
                                LessonPhase.FINISHED -> "Последняя пара сегодня"
                                null -> "Сегодня пар нет"
                            }
                            Text(label,
                                style = TextStyle(color = ColorProvider(muted), fontSize = 11.sp), maxLines = 1)
                            if (focus != null) {
                                val highlighted = focus.lesson
                                Spacer(GlanceModifier.height(3.dp))
                                Row(GlanceModifier.fillMaxWidth()) {
                                    Text(highlighted.start.toString(), modifier = GlanceModifier.width(52.dp),
                                        style = TextStyle(color = ColorProvider(navy), fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                                    Column(GlanceModifier.defaultWeight()) {
                                        Text(highlighted.subject,
                                            style = TextStyle(color = ColorProvider(navy), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                                        if (size.width < 280.dp && highlighted.room.isNotBlank()) {
                                            Text("ауд. ${highlighted.room}",
                                                style = TextStyle(color = ColorProvider(muted), fontSize = 11.sp), maxLines = 1)
                                        }
                                    }
                                    if (size.width >= 280.dp && highlighted.room.isNotBlank()) WidgetRoom(highlighted.room)
                                }
                            }
                        }
                    }
                } else {
                    Text("Сегодня · $date", style = TextStyle(color = ColorProvider(navy),
                        fontSize = 21.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(group, style = TextStyle(color = ColorProvider(muted), fontSize = 11.sp), maxLines = 1)
                    Spacer(GlanceModifier.height(8.dp))
                    when (day) {
                        DaySchedule.Unpublished -> WidgetMessage("Расписание ещё не опубликовано")
                        DaySchedule.PublishedEmpty -> WidgetMessage("Сегодня пар нет")
                        is DaySchedule.WithLessons -> {
                            val status = when (focus?.phase) {
                                LessonPhase.CURRENT -> "Сейчас: ${focus.lesson.start} ${focus.lesson.subject}"
                                LessonPhase.NEXT -> "Следующая: ${focus.lesson.start} ${focus.lesson.subject}"
                                LessonPhase.FINISHED -> "Сегодня пары закончились"
                                null -> "Сегодня пар нет"
                            }
                            Text(status,
                                style = TextStyle(color = ColorProvider(muted), fontSize = 14.sp), maxLines = 1)
                            Spacer(GlanceModifier.height(12.dp))
                            val maxRows = ((size.height.value - 122f) / 46f).toInt().coerceIn(1, 6)
                            Column(GlanceModifier.fillMaxWidth().background(ImageProvider(R.drawable.widget_row_background)).padding(10.dp)) {
                                lessons.take(maxRows).forEach { lesson -> WidgetLessonRow(lesson, size.width >= 300.dp) }
                                if (lessons.size > maxRows) {
                                    Text("Ещё ${lessons.size - maxRows} пар", modifier = GlanceModifier.padding(top = 5.dp),
                                        style = TextStyle(color = ColorProvider(muted), fontSize = 12.sp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        suspend fun updateAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(TodayWidget::class.java).forEach { TodayWidget().update(context, it) }
            manager.getGlanceIds(CompactTodayWidget::class.java).forEach { CompactTodayWidget().update(context, it) }
        }
    }
}

@Composable
private fun WidgetMessage(message: String) {
    Text(message, modifier = GlanceModifier.padding(top = 6.dp),
        style = TextStyle(color = ColorProvider(muted), fontSize = 14.sp))
}

@Composable
private fun WidgetLessonRow(lesson: Lesson, showRoom: Boolean) {
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text("${lesson.start}–${lesson.end}", modifier = GlanceModifier.width(99.dp),
            style = TextStyle(color = ColorProvider(navy), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Column(GlanceModifier.defaultWeight()) {
            Text(lesson.subject,
                style = TextStyle(color = ColorProvider(navy), fontSize = 13.sp), maxLines = 2)
            if (!showRoom && lesson.room.isNotBlank()) {
                Text("ауд. ${lesson.room}",
                    style = TextStyle(color = ColorProvider(muted), fontSize = 11.sp), maxLines = 1)
            }
        }
        if (showRoom && lesson.room.isNotBlank()) WidgetRoom(lesson.room)
    }
}

@Composable
private fun WidgetRoom(room: String) {
    Text("ауд. $room", modifier = GlanceModifier
        .background(ImageProvider(R.drawable.widget_room_background))
        .padding(horizontal = 6.dp, vertical = 3.dp),
        style = TextStyle(color = ColorProvider(navy), fontSize = 11.sp), maxLines = 1)
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

class CompactTodayWidget : TodayWidget() {
    override val alwaysCompact = true
}

class CompactTodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompactTodayWidget()
}
