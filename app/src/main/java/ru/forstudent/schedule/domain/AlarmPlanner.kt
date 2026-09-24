package ru.forstudent.schedule.domain

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class PlannedAlarm(val date: LocalDate, val triggerAt: Instant, val firstLesson: Lesson)

object AlarmPlanner {
    val zone: ZoneId = ZoneId.of("Europe/Moscow")

    fun plan(
        lessons: List<Lesson>,
        publishedDates: Set<LocalDate>,
        enabled: Boolean,
        lead: Duration = Duration.ofHours(2),
        skipDate: LocalDate? = null,
        clock: Clock = Clock.systemUTC(),
    ): List<PlannedAlarm> {
        if (!enabled) return emptyList()
        require(!lead.isNegative && !lead.isZero && lead <= Duration.ofHours(12))
        return lessons.filter { it.date in publishedDates && it.date != skipDate }
            .groupBy { it.date }
            .mapNotNull { (date, dayLessons) ->
                val first = dayLessons.minWith(compareBy<Lesson> { it.start }.thenBy { it.slot })
                val whenToRing = date.atTime(first.start).atZone(zone).toInstant().minus(lead)
                if (whenToRing <= clock.instant()) null else PlannedAlarm(date, whenToRing, first)
            }.sortedBy { it.triggerAt }
    }
}
