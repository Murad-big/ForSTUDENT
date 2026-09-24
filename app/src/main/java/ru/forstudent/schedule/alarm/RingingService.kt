package ru.forstudent.schedule.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import ru.forstudent.schedule.R
import java.time.LocalDate

class RingingService : Service() {
    private var player: MediaPlayer? = null
    private var date: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            DISMISS -> { stopSelf(); return START_NOT_STICKY }
            SNOOZE -> {
                (date ?: intent?.getStringExtra("date"))?.let { AlarmScheduler(this).snooze(LocalDate.parse(it)) }
                stopSelf()
                return START_NOT_STICKY
            }
        }
        date = intent?.getStringExtra("date") ?: run { stopSelf(); return START_NOT_STICKY }
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Звонки перед парами", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Звуковой будильник перед первой парой"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
        })
        val screen = PendingIntent.getActivity(this, 1,
            Intent(this, RingingActivity::class.java).putExtra("date", date).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Пора просыпаться")
            .setContentText("Сегодня учебный день — первая пара скоро")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_alarm), "Выключить", serviceAction(DISMISS, 2)).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_alarm), "Отложить на 10 минут", serviceAction(SNOOZE, 3)).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIFICATION_ID, notification)
        startSound()
        return START_NOT_STICKY
    }

    private fun serviceAction(action: String, request: Int): PendingIntent = PendingIntent.getService(this, request,
        Intent(this, RingingService::class.java).setAction(action).putExtra("date", date),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun startSound() {
        if (player != null) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (uri == null) return
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(this@RingingService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) { player?.release(); player = null }
    }

    override fun onDestroy() {
        player?.stop()
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val DISMISS = "ru.forstudent.schedule.DISMISS"
        const val SNOOZE = "ru.forstudent.schedule.SNOOZE"
        private const val CHANNEL = "alarms"
        private const val NOTIFICATION_ID = 74
    }
}
