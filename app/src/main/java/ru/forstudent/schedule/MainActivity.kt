package ru.forstudent.schedule

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import ru.forstudent.schedule.alarm.AlarmScheduler
import ru.forstudent.schedule.data.ScheduleRepository
import ru.forstudent.schedule.data.ScheduleSettings
import ru.forstudent.schedule.data.ScheduleSnapshot
import ru.forstudent.schedule.data.SyncOutcome
import ru.forstudent.schedule.domain.AlarmPlanner
import ru.forstudent.schedule.domain.DaySchedule
import ru.forstudent.schedule.domain.Lesson
import ru.forstudent.schedule.sync.SyncWorker
import ru.forstudent.schedule.widget.TodayWidget
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val repository by lazy { ScheduleRepository(this) }
    private val settings by lazy { ScheduleSettings(this) }
    private val scheduler by lazy { AlarmScheduler(this) }
    private var permissionsRevision by mutableIntStateOf(0)
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissionsRevision++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        SyncWorker.schedule(this)
        SyncWorker.enqueueInitial(this)
        setContent { ScheduleTheme { App() } }
    }

    override fun onResume() {
        super.onResume()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        permissionsRevision++
        lifecycleScope.launch { scheduler.reconcile(repository.snapshot(), force = true) }
    }

    @Composable
    private fun App() {
        val scope = rememberCoroutineScope()
        var snapshot by remember { mutableStateOf<ScheduleSnapshot?>(null) }
        var message by remember { mutableStateOf<String?>(null) }
        var tab by remember { mutableIntStateOf(0) }
        var settingVersion by remember { mutableIntStateOf(0) }
        val permissionsVersion = permissionsRevision
        val tabs = listOf("Сегодня", "Неделя", "Будильник", "Настройки")

        LaunchedEffect(Unit) { repository.observe().collect { snapshot = it } }

        fun refresh() {
            scope.launch {
                message = when (val outcome = repository.sync(force = true)) {
                    SyncOutcome.Updated -> {
                        snapshot = repository.snapshot()
                        scheduler.reconcile(snapshot!!)
                        TodayWidget.updateAll(this@MainActivity)
                        "Расписание обновлено"
                    }
                    SyncOutcome.Throttled -> "Повторное обновление доступно через минуту"
                    is SyncOutcome.Failed -> "Ошибка загрузки: ${outcome.message}. Сохранено прежнее расписание"
                }
            }
        }

        val tabIcons = listOf(Icons.Default.Today, Icons.Default.DateRange, Icons.Default.Alarm, Icons.Default.Settings)
        Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(tabIcons[index], contentDescription = null) },
                        label = { Text(label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ScheduleColors.blue,
                            selectedTextColor = ScheduleColors.blue,
                            indicatorColor = ScheduleColors.paleBlue,
                            unselectedIconColor = ScheduleColors.muted,
                            unselectedTextColor = ScheduleColors.navy,
                        ),
                    )
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(20.dp))
                Text("Расписание ИУБиП", color = ScheduleColors.navy,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { tab = 3 },
                    shape = RoundedCornerShape(18.dp),
                    color = ScheduleColors.paleBlue,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(settings.group, modifier = Modifier.weight(1f), color = ScheduleColors.navy,
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Изменить группу", tint = ScheduleColors.navy)
                    }
                }
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                when (tab) {
                    0 -> TodayPage(snapshot, scheduler, ::refresh, ::openSite)
                    1 -> WeekPage(snapshot)
                    2 -> AlarmPage(snapshot, settingVersion + permissionsVersion, onChanged = {
                        settingVersion++
                        snapshot?.let { scheduler.reconcile(it) }
                    }, requestExact = ::requestExact, requestNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }, requestFullScreen = ::requestFullScreen, openClocks = ::openClocks, chooseSkipDate = {
                        chooseSkipDate { date -> settings.skipDate = date; settingVersion++; snapshot?.let { scheduler.reconcile(it) } }
                    })
                    else -> SettingsPage(settings.group, onSave = { group ->
                        scope.launch {
                            scheduler.cancelAll()
                            repository.changeGroup(group)
                            snapshot = repository.snapshot()
                            TodayWidget.updateAll(this@MainActivity)
                            settingVersion++
                            refresh()
                        }
                    }, ::openSite)
                }
            }
        }
    }

    private fun openSite() {
        val url = "https://iubip.ru/schedule/?group=" + Uri.encode(settings.group)
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun requestExact() {
        if (Build.VERSION.SDK_INT >= 31) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:$packageName")))
    }

    private fun requestFullScreen() {
        if (Build.VERSION.SDK_INT >= 34) startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.parse("package:$packageName")))
    }

    private fun openClocks() {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
    }

    private fun chooseSkipDate(onDate: (LocalDate) -> Unit) {
        val tomorrow = LocalDate.now(AlarmPlanner.zone).plusDays(1)
        DatePickerDialog(this, { _, year, month, day -> onDate(LocalDate.of(year, month + 1, day)) },
            tomorrow.year, tomorrow.monthValue - 1, tomorrow.dayOfMonth).show()
    }
}

@Composable
private fun TodayPage(snapshot: ScheduleSnapshot?, scheduler: AlarmScheduler, refresh: () -> Unit, openSite: () -> Unit) {
    val today = LocalDate.now(AlarmPlanner.zone)
    val tomorrow = today.plusDays(1)
    val ru = Locale.forLanguageTag("ru")
    val next = if (scheduler.exactAllowed()) snapshot?.let { scheduler.planned(it).firstOrNull() } else null
    val tomorrowLessons = (snapshot?.day(tomorrow) as? DaySchedule.WithLessons)?.lessons
    val tomorrowText = tomorrowLessons?.minByOrNull { it.start }?.let { "${it.start} · ${it.subject}" }
        ?: if (snapshot == null || snapshot.day(tomorrow) == DaySchedule.Unpublished) "не опубликовано" else "пар нет"
    val nextText = next?.triggerAt?.atZone(AlarmPlanner.zone)
        ?.format(DateTimeFormatter.ofPattern("d MMMM HH:mm", ru)) ?: "не назначен"
    val syncText = snapshot?.lastSuccessMillis?.let {
        Instant.ofEpochMilli(it).atZone(AlarmPlanner.zone)
            .format(DateTimeFormatter.ofPattern("d MMMM HH:mm", ru))
    } ?: "ещё не было"

    LazyColumn(contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", ru)).replaceFirstChar { it.uppercaseChar() },
                    modifier = Modifier.weight(1f), color = ScheduleColors.navy,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Button(onClick = refresh, shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Обновить")
                }
            }
        }
        item { DayCard("Сегодня", snapshot?.day(today)) }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, ScheduleColors.outline)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SummaryLine(Icons.Default.DateRange, "Первая пара завтра", tomorrowText)
                    SummaryLine(Icons.Default.Alarm, "Следующий будильник", nextText)
                    SummaryLine(Icons.Default.AccessTime, "Последняя загрузка", syncText)
                }
            }
        }
        item {
            TextButton(onClick = openSite, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Открыть расписание на сайте")
            }
        }
    }
}

@Composable
private fun SummaryLine(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = ScheduleColors.blue, modifier = Modifier.size(20.dp))
        Column {
            Text(label, color = ScheduleColors.muted, style = MaterialTheme.typography.bodySmall)
            Text(value, color = ScheduleColors.navy, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WeekPage(snapshot: ScheduleSnapshot?) {
    var weekOffset by remember { mutableIntStateOf(0) }
    val monday = LocalDate.now(AlarmPlanner.zone).with(java.time.DayOfWeek.MONDAY).plusWeeks(weekOffset.toLong())
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { weekOffset-- }) { Text("←") }
                Text("${monday} — ${monday.plusDays(6)}")
                Button(onClick = { weekOffset++ }) { Text("→") }
            }
        }
        items((0L..6L).map { monday.plusDays(it) }) { date ->
            DayCard(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru"))), snapshot?.day(date))
        }
    }
}

@Composable
private fun DayCard(title: String, day: DaySchedule?) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ScheduleColors.paleBlue)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = ScheduleColors.navy,
                fontWeight = FontWeight.Bold)
            when (day) {
                null -> Text("Загрузка…", color = ScheduleColors.muted)
                DaySchedule.Unpublished -> Text("Расписание ещё не опубликовано", color = ScheduleColors.muted)
                DaySchedule.PublishedEmpty -> Text(if (title == "Сегодня") "Сегодня пар нет" else "Пар нет", color = ScheduleColors.muted)
                is DaySchedule.WithLessons -> Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        day.lessons.sortedWith(compareBy({ it.start }, { it.slot }, { it.subject })).forEach { LessonRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonRow(lesson: Lesson) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${lesson.start}–${lesson.end}", modifier = Modifier.width(86.dp),
            color = ScheduleColors.navy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Column(Modifier.weight(1f)) {
            Text(lesson.subject, color = ScheduleColors.navy, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            val details = listOf(lesson.type, lesson.teacher,
                lesson.subgroup.takeIf { it.isNotBlank() && it != "0" }?.let { "подгруппа $it" }.orEmpty())
                .filter { it.isNotBlank() }.joinToString(" · ")
            if (details.isNotEmpty()) Text(details, color = ScheduleColors.muted,
                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (lesson.room.isNotBlank()) {
            Surface(shape = RoundedCornerShape(10.dp), color = ScheduleColors.badge) {
                Text("ауд. ${lesson.room}", modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                    color = ScheduleColors.navy, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AlarmPage(snapshot: ScheduleSnapshot?, version: Int, onChanged: () -> Unit,
                      requestExact: () -> Unit, requestNotifications: () -> Unit,
                      requestFullScreen: () -> Unit, openClocks: () -> Unit,
                      chooseSkipDate: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { ScheduleSettings(context) }
    val scheduler = remember { AlarmScheduler(context) }
    var leadText by remember(version) { mutableStateOf(settings.leadMinutes.toString()) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Автоматические будильники")
            Switch(checked = settings.alarmsEnabled, onCheckedChange = { settings.alarmsEnabled = it; onChanged() })
        }
        OutlinedTextField(value = leadText, onValueChange = { leadText = it }, label = { Text("За сколько минут до пары") })
        Button(onClick = { leadText.toIntOrNull()?.takeIf { it in 1..720 }?.let { settings.leadMinutes = it; onChanged() } }) { Text("Сохранить интервал") }
        Text("Пропустить одну дату: ${settings.skipDate ?: "не выбрано"}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = chooseSkipDate) { Text("Выбрать дату") }
            Button(onClick = { settings.skipDate = null; onChanged() }) { Text("Сбросить") }
        }
        if (!scheduler.exactAllowed()) {
            Text("Точные будильники запрещены системой. Автоматический звонок не гарантирован.", color = MaterialTheme.colorScheme.error)
            Button(onClick = requestExact) { Text("Разрешить точные будильники") }
            Button(onClick = openClocks) { Text("Открыть системные часы") }
        }
        if (!scheduler.notificationsAllowed()) {
            Text("Уведомления отключены: экран звонка может не появиться.", color = MaterialTheme.colorScheme.error)
            Button(onClick = requestNotifications) { Text("Разрешить уведомления") }
        }
        if (!scheduler.fullScreenAllowed()) {
            Text("Полноэкранный экран звонка запрещён: доступен звук и уведомление.", color = MaterialTheme.colorScheme.error)
            Button(onClick = requestFullScreen) { Text("Открыть настройку экрана звонка") }
        }
        val next = if (scheduler.exactAllowed()) snapshot?.let { scheduler.planned(it).firstOrNull() } else null
        Text("Следующий звонок: ${next?.triggerAt?.atZone(AlarmPlanner.zone) ?: "не назначен"}")
    }
}

@Composable
private fun SettingsPage(currentGroup: String, onSave: (String) -> Unit, openSite: () -> Unit) {
    var group by remember(currentGroup) { mutableStateOf(currentGroup) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = group, onValueChange = { group = it }, label = { Text("Группа") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { if (group.isNotBlank()) onSave(group) }) { Text("Сохранить группу") }
        Button(onClick = openSite) { Text("Открыть расписание на сайте") }
        Text("Часовой пояс будильников: Europe/Moscow")
    }
}
