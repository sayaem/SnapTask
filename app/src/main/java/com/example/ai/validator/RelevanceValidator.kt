package com.example.ai.validator

import android.util.Log
import com.example.ai.classifier.ContextClassificationResult
import com.example.ai.extractor.CandidateEntity
import com.example.domain.model.Category
import com.example.domain.model.EntityType
import com.example.domain.model.OcrDocument
import com.example.domain.model.ScreenshotContext

data class ValidatedEntity(
    val type: EntityType,
    val label: String,
    val value: String,
    val confidence: Float,
    val isAmbiguous: Boolean = false
)

data class RelevanceValidationResult(
    val category: Category,
    val title: String,
    val isActionable: Boolean,
    val confidence: Float,
    val validatedEntities: List<ValidatedEntity>,
    val debugLogs: List<String>
)

object RelevanceValidator {

    private const val TAG = "SnapTaskPipeline"

    private val GENERIC_UI_WORDS = setOf(
        "save", "share", "back", "calendar", "delete", "edit", "settings", "search", "home",
        "more", "done", "cancel", "close", "ok", "dismiss", "menu", "select", "next", "previous",
        "submit", "filter", "clear", "login", "sign in", "sign up", "welcome", "add", "tap",
        "click", "swipe", "switch", "go", "view", "new", "app", "apps", "snaptask"
    )

    private val EVENT_KEYWORDS = listOf(
        "appointment", "meeting", "doctor", "dentist", "clinic", "hospital", "consultation",
        "scheduled", "starts", "begins", "reservation", "interview", "webinar", "zoom",
        "conference", "checkup", "patient", "event details"
    )

    private val TRAVEL_KEYWORDS = listOf(
        "flight", "airline", "boarding pass", "departure", "arrival", "gate", "terminal",
        "e-ticket", "itinerary", "pnr", "seat", "baggage", "train", "platform", "booking reference"
    )

    fun validate(
        candidates: List<CandidateEntity>,
        contextResult: ContextClassificationResult,
        ocrDocument: OcrDocument
    ): RelevanceValidationResult {
        val debugLogs = mutableListOf<String>()
        debugLogs.add("Screenshot classification: ${contextResult.context.name}")
        debugLogs.add("Context confidence: ${contextResult.confidence}")
        debugLogs.add("OCR candidates evaluation:")

        val validated = mutableListOf<ValidatedEntity>()
        val fullTextLower = ocrDocument.fullText.lowercase()

        // If context is unequivocally unactionable (e.g. Home Screen, Settings, Lock Screen, empty)
        val isExplicitlyUnactionableContext = when (contextResult.context) {
            ScreenshotContext.HOME_SCREEN,
            ScreenshotContext.LOCK_SCREEN,
            ScreenshotContext.SYSTEM_UI,
            ScreenshotContext.SETTINGS,
            ScreenshotContext.UNACTIONABLE -> true
            else -> false
        }

        for (cand in candidates) {
            val candValueLower = cand.value.lowercase().trim()
            val sourceLineLower = cand.sourceLine.lowercase()

            // 1. Spatial suppression: Status bar
            if (cand.bounds?.isStatusBar == true || (cand.bounds?.top != null && cand.bounds.top <= 0.12f && cand.bounds.bottom <= 0.15f)) {
                val reason = when (cand.type) {
                    EntityType.TIME -> "status bar"
                    EntityType.PRICE -> "battery indicator"
                    else -> "status bar UI"
                }
                debugLogs.add("${cand.value} → ${cand.type} → rejected → $reason")
                continue
            }

            // 2. Spatial suppression: Navigation bar
            if (cand.bounds?.isNavigationBar == true || (cand.bounds?.top != null && cand.bounds.top >= 0.90f)) {
                debugLogs.add("${cand.value} → ${cand.type} → rejected → navigation bar UI")
                continue
            }

            // 3. Generic UI words suppression
            if (GENERIC_UI_WORDS.contains(candValueLower)) {
                debugLogs.add("${cand.value} → ${cand.type} → rejected → UI element")
                continue
            }

            // 4. Reject all candidates if screenshot is classified as Home Screen / System UI / Settings
            if (isExplicitlyUnactionableContext) {
                val reason = when (contextResult.context) {
                    ScreenshotContext.HOME_SCREEN -> "launcher UI / app label"
                    ScreenshotContext.SETTINGS -> "settings menu item"
                    ScreenshotContext.LOCK_SCREEN -> "lock screen clock/widget"
                    else -> "system UI"
                }
                debugLogs.add("${cand.value} → ${cand.type} → rejected → $reason")
                continue
            }

            // 5. Category-specific semantic evidence validation
            when (cand.type) {
                EntityType.FLIGHT -> {
                    val hasTravelEvidence = contextResult.context == ScreenshotContext.TRAVEL_TICKET ||
                            TRAVEL_KEYWORDS.any { fullTextLower.contains(it) } ||
                            fullTextLower.contains("biman") || fullTextLower.contains("airline")
                    if (hasTravelEvidence) {
                        validated.add(ValidatedEntity(cand.type, cand.label, cand.value, cand.rawConfidence))
                        debugLogs.add("${cand.value} → FLIGHT → accepted")
                    } else {
                        debugLogs.add("${cand.value} → FLIGHT → rejected → lack of travel context")
                    }
                }

                EntityType.LOCATION -> {
                    if (cand.label == "Route") {
                        val parts = cand.value.split("→").map { it.trim().lowercase() }
                        val hasUiWords = parts.any { part -> GENERIC_UI_WORDS.any { uiWord -> part.contains(uiWord) } }
                        val hasTravelEvidence = contextResult.context == ScreenshotContext.TRAVEL_TICKET ||
                                TRAVEL_KEYWORDS.any { fullTextLower.contains(it) }

                        if (hasUiWords) {
                            debugLogs.add("${cand.value} → ROUTE → rejected → contains UI action words")
                        } else if (!hasTravelEvidence) {
                            debugLogs.add("${cand.value} → ROUTE → rejected → route pattern without travel evidence")
                        } else {
                            validated.add(ValidatedEntity(cand.type, cand.label, cand.value, cand.rawConfidence))
                            debugLogs.add("${cand.value} → LOCATION (Route) → accepted")
                        }
                    } else {
                        // General address / location
                        val isAddressContext = contextResult.context == ScreenshotContext.FORM_APPOINTMENT ||
                                contextResult.context == ScreenshotContext.CALENDAR_EVENT ||
                                contextResult.context == ScreenshotContext.MAP_LOCATION ||
                                listOf("street", "road", "ave", "boulevard", "hospital", "clinic", "airport", "terminal")
                                    .any { sourceLineLower.contains(it) }
                        if (isAddressContext && cand.value.length in 5..60) {
                            validated.add(ValidatedEntity(cand.type, cand.label, cand.value, cand.rawConfidence))
                            debugLogs.add("${cand.value} → LOCATION → accepted")
                        } else {
                            debugLogs.add("${cand.value} → LOCATION → rejected → unverified location without address cues")
                        }
                    }
                }

                EntityType.TIME -> {
                    // Check if time is publishing timestamp on webpage
                    if (contextResult.context == ScreenshotContext.WEBPAGE || sourceLineLower.contains("updated") || sourceLineLower.contains("published")) {
                        debugLogs.add("${cand.value} → TIME → rejected → webpage timestamp")
                        continue
                    }

                    // Check semantic evidence for Event / Travel / Task
                    val hasEventContext = contextResult.context == ScreenshotContext.FORM_APPOINTMENT ||
                            contextResult.context == ScreenshotContext.CALENDAR_EVENT ||
                            contextResult.context == ScreenshotContext.TRAVEL_TICKET ||
                            contextResult.context == ScreenshotContext.CHAT_MESSAGE ||
                            EVENT_KEYWORDS.any { fullTextLower.contains(it) } ||
                            sourceLineLower.contains("at ") || sourceLineLower.contains("pm") || sourceLineLower.contains("am")

                    if (hasEventContext) {
                        validated.add(ValidatedEntity(cand.type, cand.label, cand.value, cand.rawConfidence))
                        debugLogs.add("${cand.value} → TIME → accepted")
                    } else {
                        debugLogs.add("${cand.value} → TIME → rejected → standalone time without event or travel context")
                    }
                }

                EntityType.DATE -> {
                    val hasDateContext = contextResult.context == ScreenshotContext.FORM_APPOINTMENT ||
                            contextResult.context == ScreenshotContext.CALENDAR_EVENT ||
                            contextResult.context == ScreenshotContext.TRAVEL_TICKET ||
                            contextResult.context == ScreenshotContext.STUDY_DOCUMENT ||
                            contextResult.context == ScreenshotContext.RECEIPT_INVOICE ||
                            contextResult.context == ScreenshotContext.CHAT_MESSAGE ||
                            EVENT_KEYWORDS.any { fullTextLower.contains(it) } ||
                            fullTextLower.contains("departure") || fullTextLower.contains("due date") ||
                            fullTextLower.contains("purchase date") || fullTextLower.contains("date:")

                    if (hasDateContext) {
                        validated.add(ValidatedEntity(cand.type, cand.label, cand.value, cand.rawConfidence))
                        debugLogs.add("${cand.value} → DATE → accepted")
                    } else {
                        debugLogs.add("${cand.value} → DATE → rejected → standalone date without event or action context")
                    }
                }

                EntityType.PRICE -> {
                    val isShoppingOrReceipt = contextResult.context == ScreenshotContext.RECEIPT_INVOICE ||
                            contextResult.context == ScreenshotContext.PRODUCT_SHOPPING ||
                            contextResult.context == ScreenshotContext.PAYMENT ||
                            fullTextLower.contains("total") || fullTextLower.contains("subtotal") ||
                            fullTextLower.contains("paid") || fullTextLower.contains("price")

                    if (isShoppingOrReceipt) {
                        validated.add(ValidatedEntity(cand.type, cand.label, cand.value, cand.rawConfidence))
                        debugLogs.add("${cand.value} → PRICE → accepted")
                    } else {
                        debugLogs.add("${cand.value} → PRICE → rejected → lone price without shopping or receipt context")
                    }
                }

                EntityType.ORDER_ID -> {
                    val isActionableOrder = contextResult.context == ScreenshotContext.RECEIPT_INVOICE ||
                            contextResult.context == ScreenshotContext.PAYMENT ||
                            contextResult.context == ScreenshotContext.TRAVEL_TICKET ||
                            fullTextLower.contains("order") || fullTextLower.contains("transaction") || fullTextLower.contains("booking")

                    if (isActionableOrder) {
                        validated.add(ValidatedEntity(cand.type, cand.label, cand.value, cand.rawConfidence))
                        debugLogs.add("${cand.value} → ORDER_ID → accepted")
                    } else {
                        debugLogs.add("${cand.value} → ORDER_ID → rejected → identifier without order context")
                    }
                }

                EntityType.PRODUCT -> {
                    if (contextResult.context == ScreenshotContext.RECEIPT_INVOICE ||
                        contextResult.context == ScreenshotContext.PRODUCT_SHOPPING ||
                        fullTextLower.contains("$") || fullTextLower.contains("total") || fullTextLower.contains("order")) {
                        validated.add(ValidatedEntity(cand.type, cand.label, cand.value, cand.rawConfidence))
                        debugLogs.add("${cand.value} → PRODUCT → accepted")
                    } else {
                        debugLogs.add("${cand.value} → PRODUCT → rejected → product name without purchase context")
                    }
                }

                EntityType.NOTE -> {
                    if (cand.label == "Airline" && (contextResult.context == ScreenshotContext.TRAVEL_TICKET || fullTextLower.contains("flight"))) {
                        validated.add(ValidatedEntity(cand.type, cand.label, cand.value, cand.rawConfidence))
                        debugLogs.add("${cand.value} → NOTE (Airline) → accepted")
                    } else {
                        debugLogs.add("${cand.value} → NOTE → rejected")
                    }
                }

                EntityType.URL, EntityType.PHONE, EntityType.EMAIL -> {
                    validated.add(ValidatedEntity(cand.type, cand.label, cand.value, cand.rawConfidence))
                    debugLogs.add("${cand.value} → ${cand.type} → accepted")
                }

                else -> {
                    debugLogs.add("${cand.value} → ${cand.type} → rejected → unrecognized entity type")
                }
            }
        }

        // 6. Handle Conversational Task Extraction for Chat Messages
        if (contextResult.context == ScreenshotContext.CHAT_MESSAGE) {
            val taskSignals = listOf("please send", "can you", "let me know", "remember to", "don't forget", "meet me")
            val taskLine = ocrDocument.lines.find { line ->
                taskSignals.any { line.text.contains(it, ignoreCase = true) }
            }
            if (taskLine != null && validated.none { it.type == EntityType.NOTE }) {
                validated.add(
                    ValidatedEntity(
                        type = EntityType.NOTE,
                        label = "Task",
                        value = taskLine.text.trim(),
                        confidence = 0.90f
                    )
                )
                debugLogs.add("${taskLine.text.trim()} → NOTE (Task) → accepted")
            }
        }

        // 7. Determine Final Category and Outcome
        // If no actionable entities were validated, or if classified as UNACTIONABLE:
        if (validated.isEmpty() || isExplicitlyUnactionableContext) {
            debugLogs.add("Final Outcome: UNACTIONABLE (No validated entities)")
            for (log in debugLogs) {
                Log.d(TAG, log)
            }
            return RelevanceValidationResult(
                category = Category.UNACTIONABLE,
                title = "Nothing actionable found",
                isActionable = false,
                confidence = 0.0f,
                validatedEntities = emptyList(),
                debugLogs = debugLogs
            )
        }

        // Determine category based on validated entities and context
        val finalCategory: Category
        val title: String

        when {
            contextResult.context == ScreenshotContext.TRAVEL_TICKET || validated.any { it.type == EntityType.FLIGHT } -> {
                finalCategory = Category.TRAVEL
                val flight = validated.find { it.type == EntityType.FLIGHT }
                val route = validated.find { it.label == "Route" }
                title = when {
                    flight != null -> "Flight ${flight.value} detected"
                    route != null -> "Trip ${route.value} detected"
                    else -> "Travel details detected"
                }
            }

            contextResult.context == ScreenshotContext.RECEIPT_INVOICE || (validated.any { it.type == EntityType.PRICE } && fullTextLower.contains("total")) -> {
                finalCategory = Category.RECEIPT
                val price = validated.find { it.type == EntityType.PRICE }
                val prod = validated.find { it.type == EntityType.PRODUCT }
                title = when {
                    price != null && prod != null -> "${prod.value} (${price.value})"
                    price != null -> "Receipt ${price.value}"
                    else -> "Receipt detected"
                }
            }

            contextResult.context == ScreenshotContext.PAYMENT || fullTextLower.contains("payment successful") -> {
                finalCategory = Category.PAYMENT
                val price = validated.find { it.type == EntityType.PRICE }
                title = if (price != null) "Payment ${price.value}" else "Payment confirmation"
            }

            contextResult.context == ScreenshotContext.FORM_APPOINTMENT || contextResult.context == ScreenshotContext.CALENDAR_EVENT ||
                    (validated.any { it.type == EntityType.TIME } && EVENT_KEYWORDS.any { fullTextLower.contains(it) }) -> {
                finalCategory = Category.EVENT
                val isDentist = fullTextLower.contains("dentist")
                val isDoctor = fullTextLower.contains("dr.") || fullTextLower.contains("doctor")
                val date = validated.find { it.type == EntityType.DATE }
                val time = validated.find { it.type == EntityType.TIME }

                title = when {
                    isDentist -> "Dentist appointment"
                    isDoctor -> "Doctor appointment"
                    date != null && time != null -> "Event on ${date.value} at ${time.value}"
                    date != null -> "Event on ${date.value}"
                    else -> "Event detected"
                }
            }

            contextResult.context == ScreenshotContext.STUDY_DOCUMENT -> {
                finalCategory = Category.STUDY
                title = "Study deadline detected"
            }

            contextResult.context == ScreenshotContext.PRODUCT_SHOPPING || validated.any { it.type == EntityType.PRODUCT } -> {
                finalCategory = Category.PRODUCT
                val prod = validated.find { it.type == EntityType.PRODUCT }
                title = prod?.value ?: "Product detected"
            }

            contextResult.context == ScreenshotContext.CHAT_MESSAGE || validated.any { it.label == "Task" } -> {
                finalCategory = Category.MESSAGE
                title = "Task from message"
            }

            validated.any { it.type == EntityType.LOCATION } -> {
                finalCategory = Category.LOCATION
                val loc = validated.find { it.type == EntityType.LOCATION }
                title = loc?.value ?: "Location detected"
            }

            else -> {
                finalCategory = Category.OTHER
                title = "Details detected"
            }
        }

        debugLogs.add("Final Outcome: ${finalCategory.name} - \"$title\" (${validated.size} validated entities)")
        for (log in debugLogs) {
            Log.d(TAG, log)
        }

        return RelevanceValidationResult(
            category = finalCategory,
            title = title,
            isActionable = true,
            confidence = 0.92f,
            validatedEntities = validated,
            debugLogs = debugLogs
        )
    }
}
