package ru.forstudent.schedule.source

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import ru.forstudent.schedule.domain.Lesson
import ru.forstudent.schedule.domain.LessonTimeTable
import ru.forstudent.schedule.domain.ParsedSchedule
import ru.forstudent.schedule.domain.ScheduleDataException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/** Strictly translates the site's private JSON into the app's independent domain model. */
class IubipScheduleParser {
    private val periodPattern = Regex("^(\\d{2}\\.\\d{2}\\.\\d{4})\\s*-\\s*(\\d{2}\\.\\d{2}\\.\\d{4})$")
    private val weekDate = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)
    private val recordDate = DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT)

    fun parse(json: String, group: String): ParsedSchedule = try {
        val root = JSONObject(json)
        val response = root.getJSONArray(group)
        if (response.length() != 2 || response.getString(0).isBlank()) bad("Неверный объект группы")
        val weeks = response.getJSONObject(1)
        if (weeks.length() == 0) bad("Нет опубликованных недель")
        val lessons = mutableListOf<Lesson>()
        val coverage = mutableSetOf<LocalDate>()
        val weekKeys = weeks.keys()
        while (weekKeys.hasNext()) {
            val week = weeks.getJSONArray(weekKeys.next())
            if (week.length() != 2) bad("Неверный объект недели")
            val meta = week.getJSONObject(0)
            val match = periodPattern.matchEntire(meta.getString("per")) ?: bad("Неверный диапазон недели")
            val start = LocalDate.parse(match.groupValues[1], weekDate)
            val end = LocalDate.parse(match.groupValues[2], weekDate)
            if (end != start.plusDays(6)) bad("Неделя должна содержать семь дней")
            if (meta.getJSONArray("days").length() != 7) bad("Неверный список дней")
            (0L..6L).forEach { coverage += start.plusDays(it) }
            val days = week.getJSONObject(1)
            val dayKeys = days.keys()
            while (dayKeys.hasNext()) {
                val dayNumber = dayKeys.next().toIntOrNull() ?: bad("Неверный день недели")
                if (dayNumber !in 1..7) bad("Неверный день недели")
                val expectedDate = start.plusDays((dayNumber - 1).toLong())
                val slots = days.getJSONObject(dayNumber.toString())
                val slotKeys = slots.keys()
                while (slotKeys.hasNext()) {
                    val key = slotKeys.next()
                    val slot = key.toIntOrNull() ?: bad("Неверный номер пары")
                    val (startTime, endTime) = LessonTimeTable.times(slot)
                    val records = slots.getJSONArray(key)
                    for (index in 0 until records.length()) {
                        val item = records.getJSONObject(index)
                        val deleted = item.getInt("deleted")
                        if (deleted !in 0..1) bad("Неверный признак удаления")
                        if (deleted == 1) continue
                        val recordSlot = item.getString("LES").trim().toIntOrNull() ?: bad("Неверный LES")
                        if (recordSlot != slot) bad("LES не совпадает с ключом слота")
                        val date = LocalDate.parse(item.getString("DATE").trim(), recordDate)
                        if (date != expectedDate) bad("DATE не совпадает с днём недели")
                        val subject = clean(item.getString("SUBJECT"))
                        if (subject.isBlank()) bad("Пустой предмет")
                        item.getString("ID") // required even when the app does not store server IDs
                        lessons += Lesson(date, slot, startTime, endTime, subject,
                            clean(item.getString("SUBJ_TYPE")), clean(item.getString("NAME")),
                            clean(item.getString("AUD")), clean(item.getString("SUBG")))
                    }
                }
            }
        }
        ParsedSchedule(lessons.sortedWith(compareBy({ it.date }, { it.slot }, { it.subgroup })), coverage)
    } catch (error: JSONException) {
        throw ScheduleDataException("Неверный JSON расписания: ${error.message}")
    } catch (error: DateTimeParseException) {
        throw ScheduleDataException("Неверная дата расписания: ${error.message}")
    }

    private fun clean(text: String) = text.trim().replace(Regex("\\s+"), " ")
    private fun bad(message: String): Nothing = throw ScheduleDataException(message)
}
