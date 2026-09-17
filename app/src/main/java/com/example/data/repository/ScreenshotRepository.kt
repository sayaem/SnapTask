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

    // Preload sample screenshots matching PRD personas
    suspend fun seedSamplePersonasIfEmpty() {
        if (dao.getTotalScreenshotsCount() == 0) {
            seedSamples()
        }
    }

    suspend fun seedSamples() {
        // Persona D - Traveler: Flight BG147
        val flightScreenshot = ScreenshotEntity(
            imageUri = "sample://flight_bg147",
            createdAt = System.currentTimeMillis() - 1000 * 60 * 30, // 30m ago
            category = "TRAVEL",
            title = "Flight BG147 detected",
            rawText = "Biman Bangladesh Airlines\nFlight BG147\nDhaka (DAC) → Dubai (DXB)\nDeparture: September 25 • 2:15 AM\nTerminal 2, Gate 14\nConfirmed Booking Ref: #BG-98721",
            confidence = 0.96f,
            status = "NEEDS_ATTENTION",
            needsAttention = true
        )
        val flightId = dao.insertScreenshot(flightScreenshot)
        dao.insertEntities(
            listOf(
                ExtractedEntityItem(screenshotId = flightId, type = "NOTE", label = "Airline", value = "Biman Bangladesh Airlines"),
                ExtractedEntityItem(screenshotId = flightId, type = "FLIGHT", label = "Flight", value = "BG147"),
                ExtractedEntityItem(screenshotId = flightId, type = "LOCATION", label = "Route", value = "Dhaka → Dubai"),
                ExtractedEntityItem(screenshotId = flightId, type = "DATE", label = "Departure Date", value = "September 25"),
                ExtractedEntityItem(screenshotId = flightId, type = "TIME", label = "Departure Time", value = "2:15 AM"),
                ExtractedEntityItem(screenshotId = flightId, type = "LOCATION", label = "Gate", value = "Terminal 2, Gate 14")
            )
        )
        dao.insertActions(
            listOf(
                ActionItem(screenshotId = flightId, type = "REMINDER", status = "PENDING", details = "Flight BG147 Dhaka → Dubai"),
                ActionItem(screenshotId = flightId, type = "CALENDAR", status = "PENDING"),
                ActionItem(screenshotId = flightId, type = "COPY", status = "PENDING")
            )
        )

        // Persona B - Professional: Doctor Appointment
        val apptScreenshot = ScreenshotEntity(
            imageUri = "sample://appointment",
            createdAt = System.currentTimeMillis() - 1000 * 60 * 120, // 2h ago
            category = "EVENT",
            title = "Doctor appointment detected",
            rawText = "Appointment Confirmation\nPatient: Alex Johnson\nDoctor: Dr. Sarah Smith, Cardiology\nDate: October 14 at 3:30 PM\nLocation: City Health Clinic, 450 Lexington Ave\nPhone: (555) 234-5678",
            confidence = 0.95f,
            status = "NEEDS_ATTENTION",
            needsAttention = true
        )
        val apptId = dao.insertScreenshot(apptScreenshot)
        dao.insertEntities(
            listOf(
                ExtractedEntityItem(screenshotId = apptId, type = "NOTE", label = "Provider", value = "Dr. Sarah Smith, Cardiology"),
                ExtractedEntityItem(screenshotId = apptId, type = "DATE", label = "Date", value = "October 14"),
                ExtractedEntityItem(screenshotId = apptId, type = "TIME", label = "Time", value = "3:30 PM"),
                ExtractedEntityItem(screenshotId = apptId, type = "LOCATION", label = "Location", value = "City Health Clinic, 450 Lexington Ave"),
                ExtractedEntityItem(screenshotId = apptId, type = "PHONE", label = "Phone", value = "(555) 234-5678")
            )
        )
        dao.insertActions(
            listOf(
                ActionItem(screenshotId = apptId, type = "REMINDER", status = "PENDING", details = "Doctor appointment Oct 14"),
                ActionItem(screenshotId = apptId, type = "CALENDAR", status = "PENDING"),
                ActionItem(screenshotId = apptId, type = "CALL", status = "PENDING")
            )
        )

        // Persona C - Shopper: AirPods Pro Receipt
        val receiptScreenshot = ScreenshotEntity(
            imageUri = "sample://receipt_airpods",
            createdAt = System.currentTimeMillis() - 1000 * 60 * 360, // 6h ago
            category = "RECEIPT",
            title = "AirPods Pro receipt detected",
            rawText = "Apple Store Fifth Avenue\nOrder #W89124018\nItem: AirPods Pro (2nd Gen)\nTotal: $249.00 USD\nPurchase Date: September 12\nWarranty: 1 Year Limited Warranty\napple.com/support",
            confidence = 0.94f,
            status = "NEEDS_ATTENTION",
            needsAttention = true
        )
        val receiptId = dao.insertScreenshot(receiptScreenshot)
        dao.insertEntities(
            listOf(
                ExtractedEntityItem(screenshotId = receiptId, type = "PRODUCT", label = "Item", value = "AirPods Pro"),
                ExtractedEntityItem(screenshotId = receiptId, type = "PRICE", label = "Total", value = "$249.00"),
                ExtractedEntityItem(screenshotId = receiptId, type = "DATE", label = "Purchase Date", value = "September 12"),
                ExtractedEntityItem(screenshotId = receiptId, type = "ORDER_ID", label = "Order ID", value = "#W89124018"),
                ExtractedEntityItem(screenshotId = receiptId, type = "URL", label = "Link", value = "apple.com/support")
            )
        )
        dao.insertActions(
            listOf(
                ActionItem(screenshotId = receiptId, type = "SAVE_RECEIPT", status = "PENDING"),
                ActionItem(screenshotId = receiptId, type = "REMINDER", status = "PENDING", details = "Warranty check"),
                ActionItem(screenshotId = receiptId, type = "OPEN_URL", status = "PENDING")
            )
        )

        // Persona A - Student: Assignment Submission Deadline
        val studyScreenshot = ScreenshotEntity(
            imageUri = "sample://study_deadline",
            createdAt = System.currentTimeMillis() - 1000 * 60 * 600, // 10h ago
            category = "STUDY",
            title = "Study deadline detected",
            rawText = "CS 410: Software Engineering\nAssignment 3 - Architecture Design & Diagram\nAssignment submission deadline: September 29 • 11:59 PM\nPlease upload PDF submission to portal.",
            confidence = 0.94f,
            status = "NEEDS_ATTENTION",
            needsAttention = true
        )
        val studyId = dao.insertScreenshot(studyScreenshot)
        dao.insertEntities(
            listOf(
                ExtractedEntityItem(screenshotId = studyId, type = "NOTE", label = "Course", value = "CS 410: Software Engineering"),
                ExtractedEntityItem(screenshotId = studyId, type = "DEADLINE", label = "Task", value = "Assignment 3 - Architecture Design"),
                ExtractedEntityItem(screenshotId = studyId, type = "DATE", label = "Deadline Date", value = "September 29"),
                ExtractedEntityItem(screenshotId = studyId, type = "TIME", label = "Deadline Time", value = "11:59 PM")
            )
        )
        dao.insertActions(
            listOf(
                ActionItem(screenshotId = studyId, type = "REMINDER", status = "PENDING", details = "CS 410 Assignment 3 Due"),
                ActionItem(screenshotId = studyId, type = "CALENDAR", status = "PENDING")
            )
        )

        // Product Screenshot: Sony WH-1000XM6
        val prodScreenshot = ScreenshotEntity(
            imageUri = "sample://sony_headphones",
            createdAt = System.currentTimeMillis() - 1000 * 60 * 1200,
            category = "PRODUCT",
            title = "Sony WH-1000XM6 detected",
            rawText = "Sony WH-1000XM6 Wireless Noise Canceling Headphones\nPrice: $399.00\nSave 15% today\nIn stock. Free Prime delivery.\namazon.com/dp/B09XSONY",
            confidence = 0.92f,
            status = "SAVED",
            needsAttention = false
        )
        val prodId = dao.insertScreenshot(prodScreenshot)
        dao.insertEntities(
            listOf(
                ExtractedEntityItem(screenshotId = prodId, type = "PRODUCT", label = "Product", value = "Sony WH-1000XM6"),
                ExtractedEntityItem(screenshotId = prodId, type = "PRICE", label = "Price", value = "$399.00"),
                ExtractedEntityItem(screenshotId = prodId, type = "URL", label = "Store Link", value = "amazon.com/dp/B09XSONY")
            )
        )
        dao.insertActions(
            listOf(
                ActionItem(screenshotId = prodId, type = "SAVE_PRODUCT", status = "COMPLETED"),
                ActionItem(screenshotId = prodId, type = "OPEN_URL", status = "PENDING")
            )
        )
    }
}
