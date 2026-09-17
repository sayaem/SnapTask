package com.example.ai.processor

import android.content.Context
import android.net.Uri
import com.example.ai.classifier.ScreenshotClassifier
import com.example.ai.extractor.EntityExtractor
import com.example.ai.ocr.TextRecognizerHelper
import com.example.data.local.entity.ActionItem
import com.example.data.local.entity.ExtractedEntityItem
import com.example.data.local.entity.ScreenshotEntity
import com.example.data.local.entity.ScreenshotWithEntities
import kotlinx.coroutines.delay

enum class PipelineStep {
    READING_TEXT,
    FINDING_DATES,
    UNDERSTANDING_CONTENT,
    FINDING_ACTIONS,
    COMPLETED
}

data class PipelineProgress(
    val currentStep: PipelineStep,
    val isDone: Boolean = false,
    val result: ScreenshotWithEntities? = null
)

object ScreenshotPipeline {

    suspend fun processImage(
        context: Context,
        imageUri: Uri,
        knownText: String? = null,
        onProgress: (PipelineProgress) -> Unit = {}
    ): ScreenshotWithEntities {
        // Step 1: Reading text
        onProgress(PipelineProgress(PipelineStep.READING_TEXT))
        val rawText = if (!knownText.isNullOrBlank()) {
            delay(150) // Subtle natural feedback
            knownText
        } else {
            val recognized = TextRecognizerHelper.recognizeText(context, imageUri)
            if (recognized.isBlank()) {
                // Return fallback if nothing read
                ""
            } else {
                recognized
            }
        }

        // Step 2: Finding dates & entities
        onProgress(PipelineProgress(PipelineStep.FINDING_DATES))
        delay(120)
        val extractedRaw = EntityExtractor.extractAll(rawText)

        // Step 3: Understanding content & classification
        onProgress(PipelineProgress(PipelineStep.UNDERSTANDING_CONTENT))
        delay(120)
        val classification = ScreenshotClassifier.classify(rawText, extractedRaw)

        // Step 4: Finding actions
        onProgress(PipelineProgress(PipelineStep.FINDING_ACTIONS))
        delay(100)

        val screenshotEntity = ScreenshotEntity(
            id = 0,
            imageUri = imageUri.toString(),
            createdAt = System.currentTimeMillis(),
            processedAt = System.currentTimeMillis(),
            category = classification.category.name,
            title = classification.title,
            rawText = rawText,
            confidence = classification.confidence,
            status = "NEEDS_ATTENTION",
            isSaved = true,
            needsAttention = true
        )

        val entityItems = extractedRaw.map { raw ->
            ExtractedEntityItem(
                id = 0,
                screenshotId = 0,
                type = raw.type.name,
                label = raw.label,
                value = raw.value,
                confidence = raw.confidence,
                isAmbiguous = raw.isAmbiguous
            )
        }

        val allActions = mutableListOf<ActionItem>()
        allActions.add(
            ActionItem(
                id = 0,
                screenshotId = 0,
                type = classification.primaryAction.name,
                status = "PENDING",
                details = classification.title
            )
        )
        for (sec in classification.secondaryActions) {
            allActions.add(
                ActionItem(
                    id = 0,
                    screenshotId = 0,
                    type = sec.name,
                    status = "PENDING",
                    details = ""
                )
            )
        }

        val finalResult = ScreenshotWithEntities(
            screenshot = screenshotEntity,
            entities = entityItems,
            actions = allActions
        )

        onProgress(PipelineProgress(PipelineStep.COMPLETED, isDone = true, result = finalResult))
        return finalResult
    }
}
