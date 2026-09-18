package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScreenshotAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Upcoming event"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "You have a scheduled reminder"
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, (System.currentTimeMillis() % 100000).toInt())

        NotificationHelper.showReminderNotification(
            context = context,
            notificationId = notificationId,
            title = title,
            message = message
        )
    }

    companion object {
        const val EXTRA_TITLE = "extra_alarm_title"
        const val EXTRA_MESSAGE = "extra_alarm_message"
        const val EXTRA_NOTIFICATION_ID = "extra_alarm_notif_id"
    }
}
