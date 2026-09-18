package com.example.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity

object NotificationHelper {

    const val CHANNEL_REMINDERS = "snaptask_reminders"
    const val CHANNEL_DETECTIONS = "snaptask_detections"
    const val EXTRA_OPEN_SCREENSHOT_ID = "extra_open_screenshot_id"

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders created from screenshots"
                enableVibration(true)
            }

            val detectionChannel = NotificationChannel(
                CHANNEL_DETECTIONS,
                "Screenshot Detections",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when new screenshots are analyzed"
            }

            notificationManager.createNotificationChannel(reminderChannel)
            notificationManager.createNotificationChannel(detectionChannel)
        }
    }

    /**
     * Concise, useful notification displayed when an automatic reminder is created (Section 3F).
     * Includes [Open] and [Undo] actions.
     */
    fun showAutomaticReminderCreatedNotification(
        context: Context,
        notificationId: Int,
        screenshotId: Long,
        actionId: Long,
        reminderTitle: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        // Open action
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_SCREENSHOT_ID, screenshotId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Undo action
        val undoIntent = Intent(context, UndoReminderReceiver::class.java).apply {
            action = UndoReminderReceiver.ACTION_UNDO_REMINDER
            putExtra(UndoReminderReceiver.EXTRA_ACTION_ID, actionId)
            putExtra(UndoReminderReceiver.EXTRA_SCREENSHOT_ID, screenshotId)
            putExtra(UndoReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val undoPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 100000,
            undoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Reminder created")
            .setContentText(reminderTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminderTitle))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_view,
                "Open",
                openPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_revert,
                "Undo",
                undoPendingIntent
            )

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    fun showReminderNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    fun showDetectionNotification(
        context: Context,
        notificationId: Int,
        title: String,
        details: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_DETECTIONS)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("✨ SnapTask found something useful")
            .setContentText("$title: $details")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$details"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    fun scheduleSystemAlarm(
        context: Context,
        actionId: Long,
        title: String,
        triggerAtMillis: Long
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ScreenshotAlarmReceiver::class.java).apply {
            putExtra(ScreenshotAlarmReceiver.EXTRA_TITLE, "SnapTask Reminder")
            putExtra(ScreenshotAlarmReceiver.EXTRA_MESSAGE, title)
            putExtra(ScreenshotAlarmReceiver.EXTRA_NOTIFICATION_ID, (actionId % 100000).toInt())
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            actionId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelSystemAlarm(context: Context, actionId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ScreenshotAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            actionId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
