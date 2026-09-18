package com.example.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "screenshots",
    indices = [Index(value = ["createdAt"]), Index(value = ["category"])]
)
data class ScreenshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val imageUri: String,
    val createdAt: Long = System.currentTimeMillis(),
    val processedAt: Long = System.currentTimeMillis(),
    val category: String,
    val title: String,
    val rawText: String,
    val confidence: Float = 0.95f,
    val status: String = "NEEDS_ATTENTION",
    val isSaved: Boolean = true,
    val needsAttention: Boolean = true,
    // Background processing & automatic reminder lifecycle tracking (Section 3H)
    val processingStatus: String = "ACTIONABLE", // DETECTED, PROCESSING, ACTIONABLE, UNACTIONABLE, REMINDER_CREATED, FAILED
    val relevanceDecision: String = "ACTIONABLE",
    val reminderId: Long? = null,
    val reminderCreatedAt: Long? = null,
    val processingError: String? = null,
    val eventFingerprint: String? = null
)

@Entity(
    tableName = "extracted_entities",
    indices = [Index(value = ["screenshotId"])]
)
data class ExtractedEntityItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val screenshotId: Long,
    val type: String,
    val label: String,
    val value: String,
    val confidence: Float = 0.9f,
    val isAmbiguous: Boolean = false,
    val resolvedType: String? = null
)

@Entity(
    tableName = "actions",
    indices = [Index(value = ["screenshotId"])]
)
data class ActionItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val screenshotId: Long,
    val type: String,
    val status: String = "PENDING",
    val scheduledTimeMillis: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val details: String = ""
)

data class ScreenshotWithEntities(
    @Embedded
    val screenshot: ScreenshotEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "screenshotId"
    )
    val entities: List<ExtractedEntityItem>,
    @Relation(
        parentColumn = "id",
        entityColumn = "screenshotId"
    )
    val actions: List<ActionItem>
)
