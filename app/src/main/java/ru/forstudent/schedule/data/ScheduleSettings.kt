package ru.forstudent.schedule.data

import android.content.Context
import java.time.LocalDate

class ScheduleSettings(context: Context, preferencesName: String = "settings") {
    private val prefs = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    var group: String
        get() = prefs.getString("group", DEFAULT_GROUP) ?: DEFAULT_GROUP
        set(value) { prefs.edit().putString("group", value.trim()).apply() }

    var alarmsEnabled: Boolean
        get() = prefs.getBoolean("alarms_enabled", false)
        set(value) { prefs.edit().putBoolean("alarms_enabled", value).apply() }

    var leadMinutes: Int
        get() = prefs.getInt("lead_minutes", 120)
        set(value) { prefs.edit().putInt("lead_minutes", value.coerceIn(1, 720)).apply() }

    var skipDate: LocalDate?
        get() = prefs.getString("skip_date", null)?.let(LocalDate::parse)
        set(value) { prefs.edit().putString("skip_date", value?.toString()).apply() }

    var lastAttemptMillis: Long
        get() = prefs.getLong("last_attempt", 0)
        set(value) { prefs.edit().putLong("last_attempt", value).apply() }

    var groupCatalogJson: String?
        get() = prefs.getString("group_catalog", null)
        set(value) { prefs.edit().putString("group_catalog", value).apply() }

    var groupCatalogSuccessMillis: Long
        get() = prefs.getLong("group_catalog_success", 0)
        set(value) { prefs.edit().putLong("group_catalog_success", value).apply() }

    var groupCatalogAttemptMillis: Long
        get() = prefs.getLong("group_catalog_attempt", 0)
        set(value) { prefs.edit().putLong("group_catalog_attempt", value).apply() }

    companion object { const val DEFAULT_GROUP = "К3Ю3(9),К3Ю4(9)" }
}
