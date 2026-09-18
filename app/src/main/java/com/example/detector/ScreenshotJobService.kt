package com.example.detector

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ScreenshotJobService : JobService() {

    private var jobScope: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        val preferences = PreferencesManager(applicationContext)
        if (!preferences.autoDetectionEnabled.value) {
            Log.d(TAG, "Auto detection disabled in preferences, completing job.")
            scheduleJob(applicationContext)
            return false
        }

        jobScope = CoroutineScope(Dispatchers.IO).launch {
            try {
                processTriggeredUris(params)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing job URIs", e)
            } finally {
                // Re-register job trigger for the next screenshot
                scheduleJob(applicationContext)
                jobFinished(params, false)
            }
        }

        return true // Work is ongoing asynchronously
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        jobScope?.cancel()
        return true // Reschedule if cancelled unexpectedly
    }

    private suspend fun processTriggeredUris(params: JobParameters?) {
        val triggeredUris = params?.triggeredContentUris
        if (!triggeredUris.isNullOrEmpty()) {
            for (uri in triggeredUris) {
                inspectAndProcessUri(uri)
            }
        } else {
            // If no specific URIs passed by OS, query the single most recent image
            queryLatestScreenshot()
        }
    }

    private suspend fun inspectAndProcessUri(uri: Uri) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED
        )

        try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val pathCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                    val dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)

                    val id = cursor.getLong(idCol)
                    val name = if (nameCol != -1) cursor.getString(nameCol) ?: "" else ""
                    val path = if (pathCol != -1) cursor.getString(pathCol) ?: "" else ""
                    val dateSec = if (dateCol != -1) cursor.getLong(dateCol) else System.currentTimeMillis() / 1000

                    if (isScreenshotFile(name, path)) {
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        val identifier = "ss_${id}_${name}_$dateSec"
                        BackgroundScreenshotProcessor.processScreenshot(
                            context = applicationContext,
                            imageUri = contentUri,
                            identifier = identifier,
                            timestampSec = dateSec
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query URI: $uri", e)
        }
    }

    private suspend fun queryLatestScreenshot() {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED
        )

        try {
            val cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val pathCol = it.getColumnIndex(MediaStore.Images.Media.DATA)
                    val dateCol = it.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)

                    val id = it.getLong(idCol)
                    val name = if (nameCol != -1) it.getString(nameCol) ?: "" else ""
                    val path = if (pathCol != -1) it.getString(pathCol) ?: "" else ""
                    val dateSec = if (dateCol != -1) it.getLong(dateCol) else System.currentTimeMillis() / 1000

                    val nowSec = System.currentTimeMillis() / 1000
                    if (nowSec - dateSec < 60 && isScreenshotFile(name, path)) {
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        val identifier = "ss_${id}_${name}_$dateSec"
                        BackgroundScreenshotProcessor.processScreenshot(
                            context = applicationContext,
                            imageUri = contentUri,
                            identifier = identifier,
                            timestampSec = dateSec
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query latest screenshot", e)
        }
    }

    private fun isScreenshotFile(displayName: String, path: String): Boolean {
        val lowerName = displayName.lowercase()
        val lowerPath = path.lowercase()

        val isNamedScreenshot = lowerName.contains("screenshot") ||
                lowerName.contains("screen_") ||
                lowerName.contains("capture") ||
                lowerName.startsWith("screenshot_") ||
                lowerName.startsWith("screenshot-")

        val isInScreenshotDir = lowerPath.contains("/screenshots") ||
                lowerPath.contains("/screenshot") ||
                lowerPath.contains("/screen_capture")

        val isCameraPhoto = lowerPath.contains("/dcim/camera") ||
                lowerName.startsWith("img_") ||
                lowerName.startsWith("pxl_") ||
                lowerName.startsWith("dsc_")

        return (isNamedScreenshot || isInScreenshotDir) && !isCameraPhoto
    }

    companion object {
        private const val TAG = "ScreenshotJobService"
        const val JOB_ID = 91024

        fun scheduleJob(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val componentName = ComponentName(context, ScreenshotJobService::class.java)

            val builder = JobInfo.Builder(JOB_ID, componentName)
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                    )
                )
                .setTriggerContentUpdateDelay(400L)
                .setTriggerContentMaxDelay(1200L)

            try {
                scheduler.schedule(builder.build())
                Log.d(TAG, "Scheduled ContentObserver Job trigger for MediaStore screenshots.")
            } catch (e: Exception) {
                Log.e(TAG, "Could not schedule JobScheduler trigger", e)
            }
        }

        fun cancelJob(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            scheduler.cancel(JOB_ID)
            Log.d(TAG, "Cancelled ScreenshotJobService.")
        }
    }
}
