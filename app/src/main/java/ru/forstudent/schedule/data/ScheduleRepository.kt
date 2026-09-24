package ru.forstudent.schedule.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.forstudent.schedule.domain.DaySchedule
import ru.forstudent.schedule.domain.Lesson
import ru.forstudent.schedule.source.IubipScheduleClient
import ru.forstudent.schedule.source.IubipScheduleParser
import java.time.LocalDate

data class ScheduleSnapshot(val lessons: List<Lesson>, val publishedDates: Set<LocalDate>, val lastSuccessMillis: Long?) {
    fun day(date: LocalDate): DaySchedule = when {
        date !in publishedDates -> DaySchedule.Unpublished
        else -> lessons.filter { it.date == date }.let { if (it.isEmpty()) DaySchedule.PublishedEmpty else DaySchedule.WithLessons(it) }
    }
}

sealed interface SyncOutcome {
    data object Updated : SyncOutcome
    data object Throttled : SyncOutcome
    data class Failed(val message: String) : SyncOutcome
}

class ScheduleRepository(
    context: Context,
    private val db: ScheduleDatabase = ScheduleDatabase.get(context),
    private val fetch: (String) -> String = IubipScheduleClient()::fetch,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val settings: ScheduleSettings = ScheduleSettings(context),
) {
    private val dao = db.dao()
    private val parser = IubipScheduleParser()

    suspend fun snapshot(): ScheduleSnapshot = withContext(Dispatchers.IO) {
        ScheduleSnapshot(dao.allLessons().map { it.lesson() }, dao.allPublishedDates().map { LocalDate.parse(it.date) }.toSet(), dao.meta()?.lastSuccessMillis)
    }

    fun observe(): Flow<ScheduleSnapshot> = combine(dao.observeLessons(), dao.observePublishedDates(), dao.observeMeta()) { lessons, dates, meta ->
        ScheduleSnapshot(lessons.map { it.lesson() }, dates.map { LocalDate.parse(it.date) }.toSet(), meta?.lastSuccessMillis)
    }

    suspend fun sync(force: Boolean = false): SyncOutcome = withContext(Dispatchers.IO) {
        val now = nowMillis()
        val minInterval = if (force) 60_000L else 30 * 60_000L
        if (now - settings.lastAttemptMillis < minInterval) return@withContext SyncOutcome.Throttled
        settings.lastAttemptMillis = now
        try {
            val parsed = parser.parse(fetch(settings.group), settings.group)
            val dates = parsed.publishedDates.map { it.toString() }
            db.withTransaction {
                dao.deleteLessons(dates)
                dao.deletePublishedDates(dates)
                dao.insertDates(dates.map(::PublishedDateEntity))
                dao.insertLessons(parsed.lessons.mapIndexed { index, lesson ->
                    LessonEntity("${lesson.date}:${lesson.slot}:$index", lesson.date.toString(), lesson.slot,
                        lesson.start.toString(), lesson.end.toString(), lesson.subject, lesson.type,
                        lesson.teacher, lesson.room, lesson.subgroup)
                })
                dao.insertMeta(SyncMetaEntity(lastSuccessMillis = now))
            }
            SyncOutcome.Updated
        } catch (error: Exception) {
            SyncOutcome.Failed(error.message ?: "Неизвестная ошибка загрузки")
        }
    }

    suspend fun changeGroup(group: String) = withContext(Dispatchers.IO) {
        require(group.isNotBlank())
        db.withTransaction { dao.clearLessons(); dao.clearDates(); dao.clearMeta() }
        settings.group = group
        settings.lastAttemptMillis = 0
    }
}
