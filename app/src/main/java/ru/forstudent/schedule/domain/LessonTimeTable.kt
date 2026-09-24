package ru.forstudent.schedule.domain

import java.time.LocalTime

class ScheduleDataException(message: String) : IllegalArgumentException(message)

object LessonTimeTable {
    private val slots = mapOf(
        1 to ("08:20" to "09:50"), 2 to ("10:00" to "11:30"),
        3 to ("11:40" to "13:10"), 4 to ("13:30" to "15:00"),
        5 to ("15:10" to "16:40"), 6 to ("17:00" to "18:30"),
        7 to ("18:40" to "20:10"), 8 to ("20:20" to "21:50"),
    ).mapValues { (_, pair) -> LocalTime.parse(pair.first) to LocalTime.parse(pair.second) }

    fun times(slot: Int): Pair<LocalTime, LocalTime> =
        slots[slot] ?: throw ScheduleDataException("Неизвестный номер пары: $slot")
}
