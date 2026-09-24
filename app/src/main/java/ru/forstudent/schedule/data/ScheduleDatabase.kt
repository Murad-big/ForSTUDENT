package ru.forstudent.schedule.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import ru.forstudent.schedule.domain.Lesson
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey val key: String,
    val date: String,
    val slot: Int,
    val start: String,
    val end: String,
    val subject: String,
    val type: String,
    val teacher: String,
    val room: String,
    val subgroup: String,
) {
    fun lesson() = Lesson(LocalDate.parse(date), slot, LocalTime.parse(start), LocalTime.parse(end), subject, type, teacher, room, subgroup)
}

@Entity(tableName = "published_dates")
data class PublishedDateEntity(@PrimaryKey val date: String)

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(@PrimaryKey val id: Int = 1, val lastSuccessMillis: Long)

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM lessons ORDER BY date, slot, subgroup, key")
    suspend fun allLessons(): List<LessonEntity>
    @Query("SELECT * FROM lessons ORDER BY date, slot, subgroup, key")
    fun observeLessons(): Flow<List<LessonEntity>>

    @Query("SELECT * FROM published_dates")
    suspend fun allPublishedDates(): List<PublishedDateEntity>
    @Query("SELECT * FROM published_dates")
    fun observePublishedDates(): Flow<List<PublishedDateEntity>>

    @Query("SELECT * FROM sync_meta WHERE id = 1")
    suspend fun meta(): SyncMetaEntity?
    @Query("SELECT * FROM sync_meta WHERE id = 1")
    fun observeMeta(): Flow<SyncMetaEntity?>

    @Query("DELETE FROM lessons WHERE date IN (:dates)")
    suspend fun deleteLessons(dates: List<String>)

    @Query("DELETE FROM published_dates WHERE date IN (:dates)")
    suspend fun deletePublishedDates(dates: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(lessons: List<LessonEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDates(dates: List<PublishedDateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeta(meta: SyncMetaEntity)

    @Query("DELETE FROM lessons") suspend fun clearLessons()
    @Query("DELETE FROM published_dates") suspend fun clearDates()
    @Query("DELETE FROM sync_meta") suspend fun clearMeta()
}

@Database(entities = [LessonEntity::class, PublishedDateEntity::class, SyncMetaEntity::class], version = 1, exportSchema = false)
abstract class ScheduleDatabase : RoomDatabase() {
    abstract fun dao(): ScheduleDao

    companion object {
        @Volatile private var instance: ScheduleDatabase? = null
        fun get(context: Context): ScheduleDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ScheduleDatabase::class.java, "schedule.db")
                .build().also { instance = it }
        }
    }
}
