package com.example.detector

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Monitors MediaStore for newly created screenshots and notifies when a real screenshot is captured.
 *
 * Implements strict deduplication, avoids polling, and filters out camera photos.
 */
class ScreenshotDetector(
    private val context: Context,
    private val preferences: PreferencesManager,
    private val isUriAlreadyProcessed: suspend (String) -> Boolean,
    private val onScreenshotDetected: (Uri) -> Unit
) {
    private val tag = "ScreenshotDetector"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var isObserving = false
    private val handler = Handler(Looper.getMainLooper())
    private var debounceJob: Job? = null

    private val contentObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            // Debounce rapid multiple system callbacks when a file is written and indexed
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(400) // Brief pause to ensure image file is completely written by the OS
                queryLatestScreenshot()
            }
        }
    }

    /**
     * Start observing screenshot additions if permissions are granted.
     */
    fun start() {
        if (isObserving) return
        if (!hasStoragePermission()) {
            Log.d(tag, "Storage permission not granted, skipping auto-detection start")
            return
        }

        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver
            )
            isObserving = true
            Log.d(tag, "Screenshot MediaStore observer successfully registered")
        } catch (e: Exception) {
            Log.w(tag, "Failed to register ContentObserver", e)
        }
    }

    /**
     * Stop observing screenshot additions.
     */
    fun stop() {
        if (!isObserving) return
        try {
            context.contentResolver.unregisterContentObserver(contentObserver)
        } catch (e: Exception) {
            Log.w(tag, "Failed to unregister ContentObserver", e)
        }
        isObserving = false
        Log.d(tag, "Screenshot MediaStore observer stopped")
    }

    /**
     * Queries only the single latest added image to check if it qualifies as a new screenshot.
     * Does NOT scan the entire photo library.
     */
    private suspend fun queryLatestScreenshot() {
        if (!hasStoragePermission()) return

        val projection = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Images.Media.RELATIVE_PATH)
        } else {
            projection.add(MediaStore.Images.Media.DATA)
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection.toTypedArray(),
                null,
                null,
                sortOrder
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val idIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val dateAddedIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    val pathIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        it.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                    } else {
                        it.getColumnIndex(MediaStore.Images.Media.DATA)
                    }

                    val id = it.getLong(idIndex)
                    val displayName = it.getString(nameIndex) ?: ""
                    val dateAddedSec = it.getLong(dateAddedIndex)
                    val path = if (pathIndex >= 0) it.getString(pathIndex) ?: "" else ""

                    // 1. Strict screenshot check: ignore regular camera photos and downloads
                    if (!isScreenshotFile(displayName, path)) {
                        return
                    }

                    // 2. Timestamp check: must not be older than last processed time or older than 180s on first run
                    val lastProcessedTime = preferences.getLastScreenshotProcessedTimestamp()
                    val currentSec = System.currentTimeMillis() / 1000
                    if (dateAddedSec <= lastProcessedTime) {
                        return
                    }
                    if (lastProcessedTime == 0L && (currentSec - dateAddedSec) > 180) {
                        // Avoid processing historical screenshots on first install
                        preferences.markScreenshotProcessed(id.toString(), dateAddedSec)
                        return
                    }

                    // 3. Build URI and deduplicate
                    val imageUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val uriStr = imageUri.toString()

                    if (preferences.isScreenshotProcessed(id.toString()) ||
                        preferences.isScreenshotProcessed(uriStr) ||
                        isUriAlreadyProcessed(uriStr)
                    ) {
                        return
                    }

                    // Mark processed immediately to prevent duplicate runs from subsequent callbacks
                    preferences.markScreenshotProcessed(id.toString(), dateAddedSec)
                    preferences.markScreenshotProcessed(uriStr, dateAddedSec)

                    Log.d(tag, "New screenshot detected: $displayName ($imageUri)")
                    onScreenshotDetected(imageUri)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Error checking latest screenshot", e)
        }
    }

    /**
     * Verifies that the file is genuinely a screenshot by checking standard Android path and naming conventions.
     * Prevents processing camera photos (DCIM/Camera), downloads, or WhatsApp images.
     */
    private fun isScreenshotFile(displayName: String, path: String): Boolean {
        val lowerName = displayName.lowercase()
        val lowerPath = path.lowercase()

        val isNameMatch = lowerName.contains("screenshot") ||
                lowerName.contains("screen_shot") ||
                lowerName.contains("screencapture")

        val isPathMatch = lowerPath.contains("screenshots") ||
                lowerPath.contains("screen_shot")

        // Exclude camera directories
        val isCameraPhoto = lowerPath.contains("camera") || lowerPath.contains("dcim/camera")

        return (isNameMatch || isPathMatch) && !isCameraPhoto
    }

    /**
     * Checks if the required read storage/images permission is granted.
     */
    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
