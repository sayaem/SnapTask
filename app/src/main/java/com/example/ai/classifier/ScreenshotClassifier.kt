package com.example.ai.classifier

import com.example.ai.extractor.EntityExtractor
import com.example.ai.extractor.ExtractedRawEntity
import com.example.ai.validator.RelevanceValidator
import com.example.domain.model.ActionType
import com.example.domain.model.Category
import com.example.domain.model.OcrDocument

data class ClassificationResult(
    val category: Category,
    val title: String,
    val confidence: Float,
    val primaryAction: ActionType,
    val secondaryActions: List<ActionType>
)

object ScreenshotClassifier {

    fun classify(rawText: String, entities: List<ExtractedRawEntity> = emptyList()): ClassificationResult {
        val ocrDoc = OcrDocument.fromRawText(rawText)
        return classifyDocument(ocrDoc)
    }

    fun classifyDocument(ocrDocument: OcrDocument): ClassificationResult {
        val contextResult = ContextClassifier.classifyContext(ocrDocument)
        val candidates = EntityExtractor.extractCandidates(ocrDocument)
        val validation = RelevanceValidator.validate(candidates, contextResult, ocrDocument)

        val primaryAction = when (validation.category) {
            Category.TRAVEL -> ActionType.REMINDER
            Category.EVENT, Category.STUDY, Category.MESSAGE -> ActionType.REMINDER
            Category.RECEIPT -> ActionType.SAVE_RECEIPT
            Category.PRODUCT -> ActionType.SAVE_PRODUCT
            Category.LOCATION -> ActionType.NAVIGATE
            Category.PAYMENT -> ActionType.COPY
            else -> ActionType.COPY
        }

        val secondaryActions = when (validation.category) {
            Category.TRAVEL -> listOf(ActionType.CALENDAR, ActionType.COPY)
            Category.EVENT -> listOf(ActionType.CALENDAR, ActionType.COPY)
            Category.RECEIPT -> listOf(ActionType.REMINDER, ActionType.COPY)
            Category.PRODUCT -> listOf(ActionType.REMINDER, ActionType.COPY)
            else -> listOf(ActionType.COPY)
        }

        return ClassificationResult(
            category = validation.category,
            title = validation.title,
            confidence = validation.confidence,
            primaryAction = primaryAction,
            secondaryActions = secondaryActions
        )
    }
}
