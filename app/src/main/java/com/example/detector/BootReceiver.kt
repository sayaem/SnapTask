package com.example.detector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.PreferencesManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val preferences = PreferencesManager(context)
            if (preferences.autoDetectionEnabled.value) {
                ScreenshotJobService.scheduleJob(context)
            }
        }
    }
}
