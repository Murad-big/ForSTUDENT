package ru.forstudent.schedule.widget

import ru.forstudent.schedule.domain.Lesson
import java.time.LocalTime

enum class LessonPhase { CURRENT, NEXT, FINISHED }

data class WidgetLessonFocus(val phase: LessonPhase, val lesson: Lesson)

fun widgetLessonFocus(lessons: List<Lesson>, now: LocalTime): WidgetLessonFocus? {
    val ordered = lessons.sortedWith(compareBy({ it.start }, { it.slot }, { it.subject }))
    ordered.firstOrNull { it.start <= now && it.end > now }?.let {
        return WidgetLessonFocus(LessonPhase.CURRENT, it)
    }
    ordered.firstOrNull { it.start > now }?.let {
        return WidgetLessonFocus(LessonPhase.NEXT, it)
    }
    return ordered.lastOrNull()?.let { WidgetLessonFocus(LessonPhase.FINISHED, it) }
}
