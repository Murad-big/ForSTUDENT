package ru.forstudent.schedule

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal object ScheduleColors {
    val navy = Color(0xFF14264A)
    val blue = Color(0xFF0878B5)
    val paleBlue = Color(0xFFEDF6FF)
    val paleCyan = Color(0xFFECFAFC)
    val badge = Color(0xFFEAF1FA)
    val muted = Color(0xFF637188)
    val outline = Color(0xFFDCE8F3)
}

private val scheduleColorScheme = lightColorScheme(
    primary = ScheduleColors.blue,
    onPrimary = Color.White,
    background = Color.White,
    onBackground = ScheduleColors.navy,
    surface = Color.White,
    onSurface = ScheduleColors.navy,
    surfaceVariant = ScheduleColors.paleBlue,
    onSurfaceVariant = ScheduleColors.muted,
    outlineVariant = ScheduleColors.outline,
)

@Composable
internal fun ScheduleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheduleColorScheme, content = content)
}
