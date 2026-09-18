package com.example.ai.processor

import android.content.Context
import android.net.Uri
import com.example.ai.classifier.ContextClassificationResult
import com.example.ai.classifier.ContextClassifier
import com.example.ai.extractor.EntityExtractor
import com.example.ai.ocr.TextRecognizerHelper
import com.example.ai.validator.RelevanceValidationResult
import com.example.ai.validator.RelevanceValidator
import com.example.domain.model.ActionType
import com.example.domain.model.Category
import com.example.domain.model.OcrDocument
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

data class PipelineDetailedResult(
    val screenshotWithEntities: ScreenshotWithEntities,
    val ocrDocument: OcrDocument,
    val contextResult: ContextClassificationResult,
    val validationResult: RelevanceValidationResult
)

object ScreenshotPipeline {

    suspend fun processImage(
        context: Context,
        imageUri: Uri,
        knownText: String? = null,
        onProgress: (PipelineProgress) -> Unit = {}
    ): ScreenshotWithEntities {
        return processImageDetailed(context, imageUri, knownText, onProgress).screenshotWithEntities
    }

    suspend fun processImageDetailed(
        context: Context,
        imageUri: Uri,
        knownText: String? = null,
        onProgress: (PipelineProgress) -> Unit = {}
    ): PipelineDetailedResult {
        // Step 1: OCR with spatial bounding boxes
        onProgress(PipelineProgress(PipelineStep.READING_TEXT))
        val ocrDocument = if (!knownText.isNullOrBlank()) {
            delay(100)
            OcrDocument.fromRawText(knownText)
        } else {
            val doc = TextRecognizerHelper.recognizeDocument(context, imageUri)
            doc
        }

        // Step 2: Screenshot/context classification BEFORE entity extraction
        onProgress(PipelineProgress(PipelineStep.UNDERSTANDING_CONTENT))
        delay(80)
        val contextResult = ContextClassifier.classifyContext(ocrDocument)

        // Step 3: Candidate entity extraction (with spatial bounds)
        onProgress(PipelineProgress(PipelineStep.FINDING_DATES))
        delay(80)
        val candidates = EntityExtractor.extractCandidates(ocrDocument)

        // Step 4: Relevance validation & actionable information detection
        onProgress(PipelineProgress(PipelineStep.FINDING_ACTIONS))
        delay(80)
        val validationResult = RelevanceValidator.validate(
            candidates = candidates,
            contextResult = contextResult,
            ocrDocument = ocrDocument
        )

        // Step 5: Result generation
        val isActionable = validationResult.isActionable
        val finalCategory = validationResult.category

        val screenshotEntity = ScreenshotEntity(
            id = 0,
            imageUri = imageUri.toString(),
            createdAt = System.currentTimeMillis(),
            processedAt = System.currentTimeMillis(),
            category = finalCategory.name,
            title = validationResult.title,
            rawText = ocrDocument.fullText,
            confidence = validationResult.confidence,
            status = if (isActionable) "NEEDS_ATTENTION" else "REVIEWED",
            isSaved = true,
            needsAttention = isActionable,
            processingStatus = if (isActionable) "ACTIONABLE" else "UNACTIONABLE",
            relevanceDecision = if (isActionable) "ACTIONABLE" else "UNACTIONABLE"
        )

        val entityItems = validationResult.validatedEntities.map { ent ->
            ExtractedEntityItem(
                id = 0,
                screenshotId = 0,
                type = ent.type.name,
                label = ent.label,
                value = ent.value,
                confidence = ent.confidence,
                isAmbiguous = ent.isAmbiguous
            )
        }

        val allActions = mutableListOf<ActionItem>()
        if (isActionable) {
            when (finalCategory) {
                Category.TRAVEL, Category.EVENT, Category.STUDY, Category.MESSAGE -> {
                    allActions.add(ActionItem(id = 0, screenshotId = 0, type = ActionType.REMINDER.name, details = validationResult.title))
                    allActions.add(ActionItem(id = 0, screenshotId = 0, type = ActionType.CALENDAR.name, details = validationResult.title))
                }
                Category.RECEIPT -> {
                    allActions.add(ActionItem(id = 0, screenshotId = 0, type = ActionType.SAVE_RECEIPT.name, details = validationResult.title))
                    allActions.add(ActionItem(id = 0, screenshotId = 0, type = ActionType.REMINDER.name, details = validationResult.title))
                }
                Category.PRODUCT -> {
                    allActions.add(ActionItem(id = 0, screenshotId = 0, type = ActionType.SAVE_PRODUCT.name, details = validationResult.title))
                    allActions.add(ActionItem(id = 0, screenshotId = 0, type = ActionType.REMINDER.name, details = validationResult.title))
                }
                Category.PAYMENT, Category.LOCATION -> {
                    allActions.add(ActionItem(id = 0, screenshotId = 0, type = ActionType.COPY.name, details = validationResult.title))
                }
                else -> {
                    allActions.add(ActionItem(id = 0, screenshotId = 0, type = ActionType.COPY.name, details = validationResult.title))
                }
            }
        }

        val finalResult = ScreenshotWithEntities(
            screenshot = screenshotEntity,
            entities = entityItems,
            actions = allActions
        )

        onProgress(PipelineProgress(PipelineStep.COMPLETED, isDone = true, result = finalResult))
        return PipelineDetailedResult(
            screenshotWithEntities = finalResult,
            ocrDocument = ocrDocument,
            contextResult = contextResult,
            validationResult = validationResult
        )
    }
}
