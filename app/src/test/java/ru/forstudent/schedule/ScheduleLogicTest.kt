package ru.forstudent.schedule

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import ru.forstudent.schedule.alarm.AlarmReconciliation
import ru.forstudent.schedule.data.ScheduleSnapshot
import ru.forstudent.schedule.domain.AlarmPlanner
import ru.forstudent.schedule.domain.DaySchedule
import ru.forstudent.schedule.domain.Lesson
import ru.forstudent.schedule.domain.LessonTimeTable
import ru.forstudent.schedule.domain.ScheduleDataException
import ru.forstudent.schedule.source.IubipScheduleParser
import ru.forstudent.schedule.source.IubipGroupCatalogParser
import ru.forstudent.schedule.widget.LessonPhase
import ru.forstudent.schedule.widget.widgetLessonFocus
import ru.forstudent.schedule.widget.nextWidgetRefreshAt
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import ru.forstudent.schedule.domain.AlarmPlanner.zone

class ScheduleLogicTest {
    private val group = "ГРУППА"
    private val parser = IubipScheduleParser()
    private val date = LocalDate.of(2026, 9, 24)
    private val clock = Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneOffset.UTC)
    private fun fixture() = javaClass.getResource("/schedule-anonymized.json")!!.readText()

    @Test fun thirdSlotRingsAt0940Moscow() {
        val parsed = parser.parse(fixture(), group)
        val alarm = AlarmPlanner.plan(parsed.lessons, parsed.publishedDates, true, clock = clock).first { it.date == date }
        assertEquals("2026-09-24T06:40:00Z", alarm.triggerAt.toString())
        assertEquals("11:40", alarm.firstLesson.start.toString())
    }

    @Test fun publishedEmptyAndUnpublishedAreDifferent() {
        val parsed = parser.parse(fixture(), group)
        val snapshot = ScheduleSnapshot(parsed.lessons, parsed.publishedDates, null)
        assertEquals(DaySchedule.PublishedEmpty, snapshot.day(LocalDate.of(2026, 9, 26)))
        assertEquals(DaySchedule.Unpublished, snapshot.day(LocalDate.of(2026, 10, 5)))
        assertTrue(AlarmPlanner.plan(emptyList(), parsed.publishedDates, true, clock = clock).isEmpty())
    }

    @Test fun multipleLessonsInOneSlotArePreserved() {
        val root = JSONObject(fixture())
        val slot = root.getJSONArray(group).getJSONObject(1).getJSONArray("5").getJSONObject(1)
            .getJSONObject("4").getJSONArray("3")
        slot.put(JSONObject(slot.getJSONObject(0).toString()).put("ID", "3").put("SUBJECT", " Предмет В "))
        val parsed = parser.parse(root.toString(), group)
        assertEquals(listOf("Предмет Б", "Предмет В"), parsed.lessons.filter { it.date == date }.map { it.subject })
    }

    @Test fun acceptsUnpaddedRecordDateObservedOnSite() {
        val parsed = parser.parse(fixture(), group)
        assertEquals("Предмет Г", parsed.lessons.first { it.date == LocalDate.of(2026, 10, 1) }.subject)
    }

    @Test fun firstLessonChangeChangesAlarm() {
        val original = parser.parse(fixture(), group)
        val first = original.lessons.first { it.date == date }
        val changed = original.lessons.filterNot { it == first } + first.copy(slot = 2,
            start = LessonTimeTable.times(2).first, end = LessonTimeTable.times(2).second)
        val old = AlarmPlanner.plan(original.lessons, original.publishedDates, true, clock = clock).first { it.date == date }
        val new = AlarmPlanner.plan(changed, original.publishedDates, true, clock = clock).first { it.date == date }
        assertEquals("2026-09-24T05:00:00Z", new.triggerAt.toString())
        assertNotEquals(old.triggerAt, new.triggerAt)
    }

    @Test fun unknownSlotIsDataError() {
        val root = JSONObject(fixture())
        val day = root.getJSONArray(group).getJSONObject(1).getJSONArray("5").getJSONObject(1).getJSONObject("4")
        day.put("9", day.getJSONArray("3"))
        assertThrows(ScheduleDataException::class.java) { parser.parse(root.toString(), group) }
    }

    @Test fun skippedDateAndElapsedTimeAreNotScheduled() {
        val parsed = parser.parse(fixture(), group)
        val skipped = AlarmPlanner.plan(parsed.lessons, parsed.publishedDates, true,
            skipDate = date, clock = clock)
        assertTrue(skipped.none { it.date == date })
        val lateClock = Clock.fixed(Instant.parse("2026-09-24T07:00:00Z"), ZoneOffset.UTC)
        val late = AlarmPlanner.plan(parsed.lessons, parsed.publishedDates, true, clock = lateClock)
        assertTrue(late.none { it.date == date })
        val oneHour = AlarmPlanner.plan(parsed.lessons, parsed.publishedDates, true,
            lead = Duration.ofHours(1), clock = clock).first { it.date == date }
        assertEquals("2026-09-24T07:40:00Z", oneHour.triggerAt.toString())
    }

    @Test fun rebootAndResyncDoNotDuplicate() {
        val old = mapOf("2026-09-24" to 100L)
        assertTrue(AlarmReconciliation.diff(old, old).schedule.isEmpty())
        assertEquals(old, AlarmReconciliation.diff(old, old, force = true).schedule)
        assertEquals(mapOf("2026-09-24" to 200L), AlarmReconciliation.diff(old, mapOf("2026-09-24" to 200L)).schedule)
    }

    @Test fun groupCatalogKeepsAcademiesAndExactGroupNames() {
        val groups = IubipGroupCatalogParser().parse("""{
            "Право":{"К3Ю3(9),К3Ю4(9)":1,"ЮД201":1},
            "Экономика":{"ЭД401":1}
        }""")
        assertEquals(3, groups.size)
        assertEquals("Право", groups.first { it.name == "К3Ю3(9),К3Ю4(9)" }.academy)
        assertThrows(ScheduleDataException::class.java) {
            IubipGroupCatalogParser().parse("""{"Право":{"":1}}""")
        }
    }

    @Test fun widgetShowsCurrentNextAndLastLesson() {
        val lessons = parser.parse(fixture(), group).lessons.filter { it.date == date }
        assertEquals(LessonPhase.NEXT, widgetLessonFocus(lessons, LocalTime.of(9, 0))?.phase)
        assertEquals(LessonPhase.CURRENT, widgetLessonFocus(lessons, LocalTime.of(12, 0))?.phase)
        assertEquals(LessonPhase.FINISHED, widgetLessonFocus(lessons, LocalTime.of(22, 0))?.phase)
    }

    @Test fun widgetRefreshesAtLessonBoundaryAndMidnight() {
        val parsed = parser.parse(fixture(), group)
        val snapshot = ScheduleSnapshot(parsed.lessons, parsed.publishedDates, null)
        val morning = ZonedDateTime.of(2026, 9, 24, 10, 0, 0, 0, zone)
        assertEquals("2026-09-24T08:40:00Z", nextWidgetRefreshAt(snapshot, morning).toString())
        val evening = ZonedDateTime.of(2026, 9, 24, 22, 0, 0, 0, zone)
        assertEquals("2026-09-24T21:00:00Z", nextWidgetRefreshAt(snapshot, evening).toString())
    }
}
