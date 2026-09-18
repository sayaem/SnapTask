package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UndoReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val actionId = intent.getLongExtra(EXTRA_ACTION_ID, -1L)
        val screenshotId = intent.getLongExtra(EXTRA_SCREENSHOT_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        // 1. Dismiss notification immediately
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }

        // 2. Cancel alarm if scheduled
        if (actionId != -1L) {
            NotificationHelper.cancelSystemAlarm(context, actionId)
        }

        // 3. Remove action and update screenshot status in Room database
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                if (actionId != -1L) {
                    db.screenshotDao().deleteActionById(actionId)
                }
                if (screenshotId != -1L) {
                    db.screenshotDao().updateProcessingStatus(screenshotId, "ACTIONABLE")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }

        // 4. Feedback to user
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "Reminder removed", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_UNDO_REMINDER = "com.example.notifications.ACTION_UNDO_REMINDER"
        const val EXTRA_ACTION_ID = "extra_action_id"
        const val EXTRA_SCREENSHOT_ID = "extra_screenshot_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
