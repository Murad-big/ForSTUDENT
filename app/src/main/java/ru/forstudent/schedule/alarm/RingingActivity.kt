package ru.forstudent.schedule.alarm

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class RingingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(32, 32, 32, 32) }
        layout.addView(TextView(this).apply { text = "Пора просыпаться\nСегодня учебный день"; textSize = 28f; gravity = Gravity.CENTER })
        layout.addView(Button(this).apply { text = "Выключить"; setOnClickListener { act(RingingService.DISMISS) } })
        layout.addView(Button(this).apply { text = "Отложить на 10 минут"; setOnClickListener { act(RingingService.SNOOZE) } })
        setContentView(layout)
    }

    private fun act(action: String) {
        startService(Intent(this, RingingService::class.java).setAction(action))
        finish()
    }
}
