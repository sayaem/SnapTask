package com.example.ai.classifier

import com.example.ai.extractor.ExtractedRawEntity
import com.example.domain.model.ActionType
import com.example.domain.model.Category
import com.example.domain.model.EntityType

data class ClassificationResult(
    val category: Category,
    val title: String,
    val confidence: Float,
    val primaryAction: ActionType,
    val secondaryActions: List<ActionType>
)

object ScreenshotClassifier {

    fun classify(rawText: String, entities: List<ExtractedRawEntity>): ClassificationResult {
        val lowerText = rawText.lowercase()

        val hasDate = entities.any { it.type == EntityType.DATE }
        val hasTime = entities.any { it.type == EntityType.TIME }
        val hasPrice = entities.any { it.type == EntityType.PRICE }
        val hasFlight = entities.any { it.type == EntityType.FLIGHT }
        val hasUrl = entities.any { it.type == EntityType.URL }
        val hasProduct = entities.any { it.type == EntityType.PRODUCT }

        // 1. Travel Check
        if (hasFlight || lowerText.contains("flight") || lowerText.contains("boarding pass") ||
            lowerText.contains("airline") || lowerText.contains("gate ") || lowerText.contains("terminal ") ||
            lowerText.contains("itinerary") || lowerText.contains("hotel booking")
        ) {
            val flight = entities.find { it.type == EntityType.FLIGHT }?.value
            val title = if (flight != null) "Flight $flight detected" else "Flight detected"
            return ClassificationResult(
                category = Category.TRAVEL,
                title = title,
                confidence = 0.95f,
                primaryAction = ActionType.REMINDER,
                secondaryActions = listOf(ActionType.CALENDAR, ActionType.COPY, ActionType.OPEN_URL)
            )
        }

        // 2. Study Check
        if (lowerText.contains("assignment") || lowerText.contains("submission deadline") ||
            lowerText.contains("exam ") || lowerText.contains("lecture") || lowerText.contains("syllabus") ||
            lowerText.contains("homework") || lowerText.contains("course code") || lowerText.contains("final exam")
        ) {
            val title = if (lowerText.contains("exam")) "Exam date detected" else "Study deadline detected"
            return ClassificationResult(
                category = Category.STUDY,
                title = title,
                confidence = 0.93f,
                primaryAction = ActionType.REMINDER,
                secondaryActions = listOf(ActionType.CALENDAR, ActionType.COPY)
            )
        }

        // 3. Message / Task Check
        if (lowerText.contains("can you") || lowerText.contains("send me") || lowerText.contains("let's meet") ||
            lowerText.contains("please remember") || lowerText.contains("reminder:") || lowerText.contains("don't forget") ||
            lowerText.contains("tomorrow at") || lowerText.contains("ping me")
        ) {
            return ClassificationResult(
                category = Category.MESSAGE,
                title = "Task detected",
                confidence = 0.91f,
                primaryAction = ActionType.REMINDER,
                secondaryActions = listOf(ActionType.CALENDAR, ActionType.COPY)
            )
        }

        // 4. Receipt Check
        if (lowerText.contains("receipt") || lowerText.contains("invoice") || lowerText.contains("subtotal") ||
            lowerText.contains("total:") || lowerText.contains("tax:") || lowerText.contains("order confirmation") ||
            (hasPrice && (lowerText.contains("purchase") || lowerText.contains("paid") || lowerText.contains("store") || lowerText.contains("warranty")))) {
            val merchant = entities.find { it.type == EntityType.PRODUCT }?.value
            val title = if (merchant != null) "$merchant receipt detected" else "Receipt detected"
            return ClassificationResult(
                category = Category.RECEIPT,
                title = title,
                confidence = 0.94f,
                primaryAction = ActionType.SAVE_RECEIPT,
                secondaryActions = listOf(ActionType.REMINDER, ActionType.COPY)
            )
        }

        // 5. Product Check
        if (hasProduct || (hasPrice && (lowerText.contains("add to cart") || lowerText.contains("buy now") ||
                    lowerText.contains("in stock") || lowerText.contains("free shipping") || lowerText.contains("off retail")))) {
            val prod = entities.find { it.type == EntityType.PRODUCT }?.value ?: "Product"
            return ClassificationResult(
                category = Category.PRODUCT,
                title = "$prod detected",
                confidence = 0.92f,
                primaryAction = ActionType.SAVE_PRODUCT,
                secondaryActions = if (hasUrl) listOf(ActionType.OPEN_URL, ActionType.REMINDER, ActionType.COPY) else listOf(ActionType.REMINDER, ActionType.COPY)
            )
        }

        // 6. Payment Check
        if (lowerText.contains("payment successful") || lowerText.contains("transaction id") ||
            lowerText.contains("transferred") || lowerText.contains("payment received") ||
            lowerText.contains("money sent")
        ) {
            return ClassificationResult(
                category = Category.PAYMENT,
                title = "Payment confirmed",
                confidence = 0.92f,
                primaryAction = ActionType.COPY,
                secondaryActions = listOf(ActionType.REMINDER)
            )
        }

        // 7. Event / Appointment Check
        if (lowerText.contains("appointment") || lowerText.contains("meeting") || lowerText.contains("doctor") ||
            lowerText.contains("dentist") || lowerText.contains("reservation") || (hasDate && hasTime)
        ) {
            val title = when {
                lowerText.contains("appointment") -> "Appointment detected"
                lowerText.contains("meeting") -> "Meeting detected"
                else -> "Event detected"
            }
            return ClassificationResult(
                category = Category.EVENT,
                title = title,
                confidence = 0.95f,
                primaryAction = ActionType.REMINDER,
                secondaryActions = listOf(ActionType.CALENDAR, ActionType.COPY)
            )
        }

        // 8. Location / Address Check
        if (entities.any { it.type == EntityType.LOCATION } || lowerText.contains("address:") || lowerText.contains("directions to")) {
            return ClassificationResult(
                category = Category.LOCATION,
                title = "Address detected",
                confidence = 0.88f,
                primaryAction = ActionType.NAVIGATE,
                secondaryActions = listOf(ActionType.COPY, ActionType.REMINDER)
            )
        }

        // 9. URL / Link Check
        if (hasUrl) {
            return ClassificationResult(
                category = Category.OTHER,
                title = "Link detected",
                confidence = 0.85f,
                primaryAction = ActionType.OPEN_URL,
                secondaryActions = listOf(ActionType.COPY, ActionType.REMINDER)
            )
        }

        // 10. General / Other fallback
        val fallbackTitle = if (rawText.isNotBlank()) {
            val firstLine = rawText.lines().firstOrNull { it.isNotBlank() }?.take(28) ?: "Information"
            "$firstLine detected"
        } else {
            "Screenshot detected"
        }

        return ClassificationResult(
            category = Category.OTHER,
            title = fallbackTitle,
            confidence = 0.70f,
            primaryAction = if (hasDate) ActionType.REMINDER else ActionType.COPY,
            secondaryActions = listOf(ActionType.COPY)
        )
    }
}
