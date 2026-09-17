package com.example.data.repository

import com.example.data.local.dao.CategoryCount
import com.example.data.local.dao.ScreenshotDao
import com.example.data.local.entity.ActionItem
import com.example.data.local.entity.ExtractedEntityItem
import com.example.data.local.entity.ScreenshotEntity
import com.example.data.local.entity.ScreenshotWithEntities
import kotlinx.coroutines.flow.Flow

class ScreenshotRepository(
    private val dao: ScreenshotDao
) {
    val allScreenshots: Flow<List<ScreenshotWithEntities>> = dao.getAllScreenshots()
    val inboxScreenshots: Flow<List<ScreenshotWithEntities>> = dao.getInboxScreenshots()
    val attentionCount: Flow<Int> = dao.getAttentionCount()
    val attentionCountsByCategory: Flow<List<CategoryCount>> = dao.getAttentionCountsByCategory()

    fun getScreenshotById(id: Long): Flow<ScreenshotWithEntities?> = dao.getScreenshotById(id)

    suspend fun getScreenshotByIdSync(id: Long): ScreenshotWithEntities? = dao.getScreenshotByIdSync(id)

    fun search(query: String): Flow<List<ScreenshotWithEntities>> = dao.searchScreenshots(query)

    suspend fun insertProcessedScreenshot(item: ScreenshotWithEntities): Long {
        val screenshotId = dao.insertScreenshot(item.screenshot)
        val entitiesWithId = item.entities.map { it.copy(screenshotId = screenshotId) }
        val actionsWithId = item.actions.map { it.copy(screenshotId = screenshotId) }
        dao.insertEntities(entitiesWithId)
        dao.insertActions(actionsWithId)
        return screenshotId
    }

    suspend fun updateEntity(entity: ExtractedEntityItem) {
        dao.updateEntity(entity)
    }

    suspend fun updateScreenshot(screenshot: ScreenshotEntity) {
        dao.updateScreenshot(screenshot)
    }

    suspend fun markActionCreated(screenshotId: Long, actionType: String, scheduledTime: Long? = null) {
        val screenshotWithEntities = dao.getScreenshotByIdSync(screenshotId)
        if (screenshotWithEntities != null) {
            val matchingAction = screenshotWithEntities.actions.find { it.type == actionType }
            if (matchingAction != null) {
                dao.updateAction(
                    matchingAction.copy(
                        status = "CREATED",
                        scheduledTimeMillis = scheduledTime
                    )
                )
            } else {
                dao.insertActions(
                    listOf(
                        ActionItem(
                            screenshotId = screenshotId,
                            type = actionType,
                            status = "CREATED",
                            scheduledTimeMillis = scheduledTime
                        )
                    )
                )
            }
            dao.markProcessed(screenshotId, "ACTION_CREATED")
        }
    }

    suspend fun markReviewed(screenshotId: Long) {
        dao.markProcessed(screenshotId, "SAVED")
    }

    suspend fun deleteScreenshot(id: Long) {
        dao.deleteEntireScreenshot(id)
    }

    suspend fun clearAll() {
        dao.clearAllData()
    }

    suspend fun getTotalScreenshotsCount(): Int = dao.getTotalScreenshotsCount()

    suspend fun getTotalActionsCount(): Int = dao.getTotalActionsCompletedCount()

    // Check whether an image URI has already been processed and saved
    suspend fun isImageUriProcessed(imageUri: String): Boolean {
        return dao.countByImageUri(imageUri) > 0
    }
}
