package com.example.detector

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.ai.processor.ScreenshotPipeline
import com.example.ai.validator.AutomaticReminderEvaluator
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.entity.ActionItem
import com.example.domain.model.ActionType
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BackgroundScreenshotProcessor {

    private const val TAG = "SnapTaskBackground"

    /**
     * Executes the end-to-end background analysis pipeline for a detected screenshot URI (Sections 3A-3I).
     * Ensures strict deduplication, conservative reminder evaluation, and quiet background behavior.
     */
    suspend fun processScreenshot(
        context: Context,
        imageUri: Uri,
        identifier: String,
        timestampSec: Long = System.currentTimeMillis() / 1000
    ): Long? = withContext(Dispatchers.IO) {
        val preferences = PreferencesManager(context)
        val database = AppDatabase.getInstance(context)
        val dao = database.screenshotDao()

        // 1. Strict deduplication check (Section 3G)
        if (preferences.isScreenshotProcessed(identifier)) {
            Log.d(TAG, "Screenshot $identifier already processed in preferences, ignoring.")
            return@withContext null
        }

        if (dao.countByImageUri(imageUri.toString()) > 0) {
            Log.d(TAG, "Screenshot URI $imageUri already exists in database, marking processed.")
            preferences.markScreenshotProcessed(identifier, timestampSec)
            return@withContext null
        }

        // Mark processed early to avoid race conditions with multiple observers/triggers
        preferences.markScreenshotProcessed(identifier, timestampSec)

        try {
            Log.d(TAG, "Starting background processing for: $imageUri ($identifier)")

            // 2. Execute ML Kit OCR + Classification + Candidate Extraction + Relevance Validation
            val detailed = ScreenshotPipeline.processImageDetailed(context, imageUri)
            val result = detailed.screenshotWithEntities
            val isActionable = detailed.validationResult.isActionable

            // 3. Persist initial screenshot entity to local database
            val screenshotToInsert = result.screenshot.copy(
                processingStatus = if (isActionable) "ACTIONABLE" else "UNACTIONABLE",
                relevanceDecision = if (isActionable) "ACTIONABLE" else "UNACTIONABLE"
            )
            val screenshotId = dao.insertScreenshot(screenshotToInsert)

            // Persist associated entities
            if (result.entities.isNotEmpty()) {
                val entitiesWithId = result.entities.map { it.copy(screenshotId = screenshotId) }
                dao.insertEntities(entitiesWithId)
            }

            // Persist default suggested actions
            if (result.actions.isNotEmpty()) {
                val actionsWithId = result.actions.map { it.copy(screenshotId = screenshotId) }
                dao.insertActions(actionsWithId)
            }

            // 4. Automatic reminder evaluation (Sections 3C, 3D, 3E)
            val autoRemindersEnabled = preferences.autoRemindersEnabled.value
            if (autoRemindersEnabled && isActionable) {
                val decision = AutomaticReminderEvaluator.evaluate(
                    validationResult = detailed.validationResult,
                    ocrDocument = detailed.ocrDocument,
                    contextResult = detailed.contextResult,
                    userDefaultOffsetMinutes = preferences.defaultReminderOffset.value
                )

                if (decision.isEligible) {
                    // Check duplicate event fingerprint across screenshots (Section 3G)
                    val isDuplicateEvent = preferences.isFingerprintProcessed(decision.fingerprint) ||
                            dao.countByFingerprint(decision.fingerprint) > 0

                    if (isDuplicateEvent) {
                        Log.d(TAG, "Duplicate event detected for fingerprint: ${decision.fingerprint}, skipping automatic reminder.")
                    } else {
                        Log.d(TAG, "Automatic reminder eligible: ${decision.title} at ${decision.reminderTimestamp}")

                        // Create dedicated scheduled reminder action
                        val reminderAction = ActionItem(
                            id = 0,
                            screenshotId = screenshotId,
                            type = ActionType.REMINDER.name,
                            status = "SCHEDULED",
                            scheduledTimeMillis = decision.reminderTimestamp,
                            createdAt = System.currentTimeMillis(),
                            details = decision.title
                        )
                        dao.insertActions(listOf(reminderAction))

                        // Retrieve the inserted action to get its ID
                        val insertedWithEntities = dao.getScreenshotByIdSync(screenshotId)
                        val createdAction = insertedWithEntities?.actions?.find {
                            it.type == ActionType.REMINDER.name && it.status == "SCHEDULED" && it.details == decision.title
                        }
                        val actionId = createdAction?.id ?: screenshotId

                        // Update screenshot record with reminder tracking info
                        dao.updateReminderCreated(
                            id = screenshotId,
                            reminderId = actionId,
                            timestamp = System.currentTimeMillis(),
                            fingerprint = decision.fingerprint
                        )
                        preferences.markFingerprintProcessed(decision.fingerprint)

                        // Schedule exact OS alarm
                        NotificationHelper.scheduleSystemAlarm(
                            context = context,
                            actionId = actionId,
                            title = decision.title,
                            triggerAtMillis = decision.reminderTimestamp
                        )

                        // Notify user if notifications enabled (Section 3F)
                        if (preferences.notificationsEnabled.value) {
                            NotificationHelper.showAutomaticReminderCreatedNotification(
                                context = context,
                                notificationId = (screenshotId % 100000).toInt(),
                                screenshotId = screenshotId,
                                actionId = actionId,
                                reminderTitle = decision.title
                            )
                        }
                    }
                } else {
                    Log.d(TAG, "Not eligible for automatic reminder: ${decision.reason}")
                    // Quiet: Do NOT send any notification when nothing actionable or conservative check fails
                }
            } else {
                Log.d(TAG, "Auto reminders disabled or not actionable; skipping reminder creation.")
            }

            return@withContext screenshotId
        } catch (e: Exception) {
            Log.e(TAG, "Background processing failed for $imageUri", e)
            return@withContext null
        }
    }
}
