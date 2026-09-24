package ru.forstudent.schedule

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.forstudent.schedule.data.ScheduleDatabase
import ru.forstudent.schedule.data.ScheduleRepository
import ru.forstudent.schedule.data.ScheduleSettings
import ru.forstudent.schedule.data.SyncOutcome
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RepositoryFailureTest {
    @Test fun networkFailurePreservesLastGoodSchedule() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferencesName = "test_settings_repository_failure"
        context.getSharedPreferences(preferencesName, 0).edit().clear().commit()
        val settings = ScheduleSettings(context, preferencesName).apply { group = "ГРУППА" }
        val fixture = instrumentation.context.assets.open("schedule-anonymized.json").bufferedReader().use { it.readText() }
        val db = Room.inMemoryDatabaseBuilder(context, ScheduleDatabase::class.java).build()
        try {
            val good = ScheduleRepository(context, db, { fixture }, { 1_000_000L }, settings)
            assertEquals(SyncOutcome.Updated, good.sync(force = true))
            val previous = good.snapshot()
            val failed = ScheduleRepository(context, db, { throw IOException("offline") }, { 1_100_000L }, settings)
            assertTrue(failed.sync(force = true) is SyncOutcome.Failed)
            assertEquals(previous, failed.snapshot())
        } finally {
            db.close()
            context.getSharedPreferences(preferencesName, 0).edit().clear().commit()
        }
    }
}
