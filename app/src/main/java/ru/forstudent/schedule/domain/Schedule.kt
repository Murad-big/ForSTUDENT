package ru.forstudent.schedule.domain

import java.time.LocalDate
import java.time.LocalTime

data class Lesson(
    val date: LocalDate,
    val slot: Int,
    val start: LocalTime,
    val end: LocalTime,
    val subject: String,
    val type: String,
    val teacher: String,
    val room: String,
    val subgroup: String,
)

data class ParsedSchedule(val lessons: List<Lesson>, val publishedDates: Set<LocalDate>)

sealed interface DaySchedule {
    data class WithLessons(val lessons: List<Lesson>) : DaySchedule
    data object PublishedEmpty : DaySchedule
    data object Unpublished : DaySchedule
}
