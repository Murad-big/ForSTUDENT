package ru.forstudent.schedule.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.forstudent.schedule.domain.GroupOption
import ru.forstudent.schedule.source.IubipGroupCatalogParser
import ru.forstudent.schedule.source.IubipScheduleClient

data class GroupCatalogResult(val groups: List<GroupOption>, val error: String? = null)

class GroupCatalogRepository(
    context: Context,
    private val fetch: () -> String = IubipScheduleClient()::fetchGroups,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val settings: ScheduleSettings = ScheduleSettings(context),
) {
    private val parser = IubipGroupCatalogParser()

    suspend fun load(force: Boolean = false): GroupCatalogResult = withContext(Dispatchers.IO) {
        val cached = runCatching { settings.groupCatalogJson?.let(parser::parse).orEmpty() }.getOrDefault(emptyList())
        val now = nowMillis()
        if (!force && cached.isNotEmpty() && now - settings.groupCatalogSuccessMillis < 24 * 60 * 60_000L) {
            return@withContext GroupCatalogResult(cached)
        }
        if (now - settings.groupCatalogAttemptMillis < 60_000L) {
            return@withContext GroupCatalogResult(cached,
                if (cached.isEmpty()) "Повторная загрузка списка доступна через минуту" else null)
        }
        settings.groupCatalogAttemptMillis = now
        try {
            val json = fetch()
            val groups = parser.parse(json)
            settings.groupCatalogJson = json
            settings.groupCatalogSuccessMillis = now
            GroupCatalogResult(groups)
        } catch (error: Exception) {
            GroupCatalogResult(cached, "Не удалось загрузить список групп: ${error.message}")
        }
    }
}
