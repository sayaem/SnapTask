package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("snaptask_prefs", Context.MODE_PRIVATE)

    private val _onboardingCompleted = MutableStateFlow(
        prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    )
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    private val _autoDetectionEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_DETECTION, false)
    )
    val autoDetectionEnabled: StateFlow<Boolean> = _autoDetectionEnabled.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_NOTIFICATIONS, true)
    )
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _autoRemindersEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_REMINDERS, true)
    )
    val autoRemindersEnabled: StateFlow<Boolean> = _autoRemindersEnabled.asStateFlow()

    private val _defaultReminderOffset = MutableStateFlow(
        prefs.getInt(KEY_DEFAULT_REMINDER_OFFSET, 60) // 1 hour before default
    )
    val defaultReminderOffset: StateFlow<Int> = _defaultReminderOffset.asStateFlow()

    private val _themeMode = MutableStateFlow(
        prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
        _onboardingCompleted.value = completed
    }

    fun setAutoDetectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_DETECTION, enabled).apply()
        _autoDetectionEnabled.value = enabled
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
        _notificationsEnabled.value = enabled
    }

    fun setAutoRemindersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_REMINDERS, enabled).apply()
        _autoRemindersEnabled.value = enabled
    }

    fun setDefaultReminderOffset(minutes: Int) {
        prefs.edit().putInt(KEY_DEFAULT_REMINDER_OFFSET, minutes).apply()
        _defaultReminderOffset.value = minutes
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeMode.value = mode
    }

    fun getLastScreenshotProcessedTimestamp(): Long {
        return prefs.getLong(KEY_LAST_SCREENSHOT_TIMESTAMP, 0L)
    }

    fun isScreenshotProcessed(identifier: String): Boolean {
        val processedSet = prefs.getStringSet(KEY_PROCESSED_SCREENSHOT_IDS, emptySet()) ?: emptySet()
        return processedSet.contains(identifier)
    }

    fun markScreenshotProcessed(identifier: String, timestampSec: Long) {
        val currentSet = prefs.getStringSet(KEY_PROCESSED_SCREENSHOT_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        // Keep set size bounded to most recent 200 items to avoid growing unboundedly
        if (currentSet.size > 200) {
            val pruned = currentSet.toList().takeLast(100).toMutableSet()
            pruned.add(identifier)
            prefs.edit()
                .putStringSet(KEY_PROCESSED_SCREENSHOT_IDS, pruned)
                .putLong(KEY_LAST_SCREENSHOT_TIMESTAMP, timestampSec)
                .apply()
        } else {
            currentSet.add(identifier)
            prefs.edit()
                .putStringSet(KEY_PROCESSED_SCREENSHOT_IDS, currentSet)
                .putLong(KEY_LAST_SCREENSHOT_TIMESTAMP, timestampSec)
                .apply()
        }
    }

    fun isFingerprintProcessed(fingerprint: String): Boolean {
        val processedSet = prefs.getStringSet(KEY_PROCESSED_FINGERPRINTS, emptySet()) ?: emptySet()
        return processedSet.contains(fingerprint)
    }

    fun markFingerprintProcessed(fingerprint: String) {
        val currentSet = prefs.getStringSet(KEY_PROCESSED_FINGERPRINTS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (currentSet.size > 200) {
            val pruned = currentSet.toList().takeLast(100).toMutableSet()
            pruned.add(fingerprint)
            prefs.edit().putStringSet(KEY_PROCESSED_FINGERPRINTS, pruned).apply()
        } else {
            currentSet.add(fingerprint)
            prefs.edit().putStringSet(KEY_PROCESSED_FINGERPRINTS, currentSet).apply()
        }
    }

    companion object {
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_AUTO_DETECTION = "auto_detection_enabled"
        private const val KEY_AUTO_REMINDERS = "auto_reminders_enabled"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_DEFAULT_REMINDER_OFFSET = "default_reminder_offset"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LAST_SCREENSHOT_TIMESTAMP = "last_screenshot_timestamp"
        private const val KEY_PROCESSED_SCREENSHOT_IDS = "processed_screenshot_ids"
        private const val KEY_PROCESSED_FINGERPRINTS = "processed_event_fingerprints"
    }
}
