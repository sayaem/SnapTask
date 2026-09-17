package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.processor.PipelineProgress
import com.example.ai.processor.PipelineStep
import com.example.ai.processor.ScreenshotPipeline
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.dao.CategoryCount
import com.example.data.local.entity.ExtractedEntityItem
import com.example.data.local.entity.ScreenshotWithEntities
import com.example.data.repository.ScreenshotRepository
import com.example.domain.model.EntityType
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProcessingUiState(
    val isProcessing: Boolean = false,
    val imageUri: Uri? = null,
    val currentStep: PipelineStep = PipelineStep.READING_TEXT,
    val completedScreenshotId: Long? = null
)

class SnapTaskViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = ScreenshotRepository(db.screenshotDao())
    val preferences = PreferencesManager(application)

    init {
        NotificationHelper.initChannels(application)
        viewModelScope.launch {
            repository.seedSamplePersonasIfEmpty()
        }
    }

    val inboxScreenshots: StateFlow<List<ScreenshotWithEntities>> = repository.inboxScreenshots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allScreenshots: StateFlow<List<ScreenshotWithEntities>> = repository.allScreenshots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val attentionCount: StateFlow<Int> = repository.attentionCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val attentionCategoryCounts: StateFlow<List<CategoryCount>> = repository.attentionCountsByCategory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchQuery = MutableStateFlow("")
    val selectedCategoryFilter = MutableStateFlow("ALL")

    val filteredSavedScreenshots: StateFlow<List<ScreenshotWithEntities>> = combine(
        repository.allScreenshots,
        searchQuery,
        selectedCategoryFilter
    ) { screenshots, query, category ->
        var list = screenshots
        if (category != "ALL") {
            list = list.filter { it.screenshot.category.equals(category, ignoreCase = true) }
        }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.screenshot.title.lowercase().contains(q) ||
                        it.screenshot.rawText.lowercase().contains(q) ||
                        it.entities.any { ent -> ent.value.lowercase().contains(q) }
            }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _processingState = MutableStateFlow(ProcessingUiState())
    val processingState: StateFlow<ProcessingUiState> = _processingState.asStateFlow()

    fun processScreenshot(
        uri: Uri,
        knownText: String? = null,
        onComplete: (Long) -> Unit
    ) {
        viewModelScope.launch {
            _processingState.value = ProcessingUiState(
                isProcessing = true,
                imageUri = uri,
                currentStep = PipelineStep.READING_TEXT
            )

            val processed = ScreenshotPipeline.processImage(
                context = getApplication(),
                imageUri = uri,
                knownText = knownText
            ) { progress: PipelineProgress ->
                _processingState.value = _processingState.value.copy(
                    currentStep = progress.currentStep
                )
            }

            val savedId = repository.insertProcessedScreenshot(processed)
            _processingState.value = ProcessingUiState(
                isProcessing = false,
                imageUri = null,
                completedScreenshotId = savedId
            )
            onComplete(savedId)
        }
    }

    fun dismissProcessing() {
        _processingState.value = ProcessingUiState()
    }

    fun getScreenshotById(id: Long): StateFlow<ScreenshotWithEntities?> {
        return repository.getScreenshotById(id)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    fun updateExtractedEntity(entity: ExtractedEntityItem) {
        viewModelScope.launch {
            repository.updateEntity(entity)
        }
    }

    fun createReminder(
        screenshotWithEntities: ScreenshotWithEntities,
        offsetMinutes: Int,
        context: Context
    ) {
        viewModelScope.launch {
            val screenshot = screenshotWithEntities.screenshot
            val dateVal = screenshotWithEntities.entities.find { it.type == EntityType.DATE.name }?.value ?: "Upcoming"
            val timeVal = screenshotWithEntities.entities.find { it.type == EntityType.TIME.name }?.value ?: ""

            repository.markActionCreated(
                screenshotId = screenshot.id,
                actionType = "REMINDER",
                scheduledTime = System.currentTimeMillis() + 60000L // Simulated schedule trigger
            )

            // Show immediate system notification as requested in PRD Section 25
            val notifTitle = "${screenshot.title}"
            val notifMessage = if (timeVal.isNotBlank()) "$dateVal • $timeVal" else dateVal
            NotificationHelper.showReminderNotification(
                context = context,
                notificationId = screenshot.id.toInt(),
                title = notifTitle,
                message = notifMessage
            )

            Toast.makeText(context, "✓ Reminder created for $dateVal", Toast.LENGTH_SHORT).show()
        }
    }

    fun addToCalendar(
        screenshotWithEntities: ScreenshotWithEntities,
        context: Context
    ) {
        viewModelScope.launch {
            val screenshot = screenshotWithEntities.screenshot
            val locationVal = screenshotWithEntities.entities.find { it.type == EntityType.LOCATION.name }?.value ?: ""

            repository.markActionCreated(
                screenshotId = screenshot.id,
                actionType = "CALENDAR"
            )

            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, screenshot.title)
                putExtra(CalendarContract.Events.DESCRIPTION, screenshot.rawText)
                if (locationVal.isNotBlank()) {
                    putExtra(CalendarContract.Events.EVENT_LOCATION, locationVal)
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Calendar app not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun markAction(screenshotId: Long, actionType: String) {
        viewModelScope.launch {
            repository.markActionCreated(screenshotId, actionType)
        }
    }

    fun markReviewed(screenshotId: Long) {
        viewModelScope.launch {
            repository.markReviewed(screenshotId)
        }
    }

    fun deleteScreenshot(id: Long) {
        viewModelScope.launch {
            repository.deleteScreenshot(id)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun seedSamples() {
        viewModelScope.launch {
            repository.seedSamples()
        }
    }

    suspend fun getStats(): Pair<Int, Int> {
        val total = repository.getTotalScreenshotsCount()
        val actions = repository.getTotalActionsCount()
        return Pair(total, actions)
    }
}
