package ru.forstudent.schedule.source

import org.json.JSONException
import org.json.JSONObject
import ru.forstudent.schedule.domain.GroupOption
import ru.forstudent.schedule.domain.ScheduleDataException

/** The academy -> group-name object is another private contract of the schedule page. */
class IubipGroupCatalogParser {
    fun parse(json: String): List<GroupOption> = try {
        val root = JSONObject(json)
        if (root.length() == 0) throw ScheduleDataException("Список групп пуст")
        val groups = mutableListOf<GroupOption>()
        val academies = root.keys()
        while (academies.hasNext()) {
            val academyKey = academies.next()
            val academy = academyKey.trim()
            if (academy.isEmpty()) throw ScheduleDataException("Пустое название академии")
            val entries = root.getJSONObject(academyKey)
            if (entries.length() == 0) throw ScheduleDataException("В академии нет групп")
            val names = entries.keys()
            while (names.hasNext()) {
                val nameKey = names.next()
                val name = nameKey.trim()
                if (name.isEmpty()) throw ScheduleDataException("Пустое название группы")
                entries.getInt(nameKey) // The site currently stores a numeric marker per group.
                groups += GroupOption(academy, name)
            }
        }
        if (groups.map { it.name }.toSet().size != groups.size) {
            throw ScheduleDataException("В списке повторяется название группы")
        }
        groups.sortedWith(compareBy(GroupOption::academy, GroupOption::name))
    } catch (error: JSONException) {
        throw ScheduleDataException("Неверный список групп: ${error.message}")
    }
}
