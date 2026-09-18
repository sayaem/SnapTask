package com.example.ai.validator

import com.example.ai.classifier.ContextClassificationResult
import com.example.domain.model.Category
import com.example.domain.model.EntityType
import com.example.domain.model.OcrDocument
import com.example.domain.model.ScreenshotContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class AutomaticReminderDecision(
    val isEligible: Boolean,
    val title: String,
    val eventTimestamp: Long = 0L,
    val reminderTimestamp: Long = 0L,
    val leadTimeMinutes: Int = 60,
    val fingerprint: String = "",
    val reason: String = ""
)

/**
 * Conservative evaluator determining whether a processed screenshot represents a high-confidence,
 * actionable future event or deadline suitable for automatic reminder creation (Sections 3C & 3D).
 */
object AutomaticReminderEvaluator {

    private val APPOINTMENT_KEYWORDS = listOf(
        "appointment", "doctor", "dentist", "clinic", "hospital", "consultation",
        "interview", "webinar", "zoom", "meeting", "checkup", "patient"
    )

    private val STUDY_DEADLINE_KEYWORDS = listOf(
        "assignment", "submission", "deadline", "due date", "homework", "exam", "quiz",
        "syllabus", "project submission", "midterm", "final exam"
    )

    private val TRAVEL_KEYWORDS = listOf(
        "flight", "departure", "boarding pass", "e-ticket", "airline", "itinerary",
        "gate", "terminal", "train", "booking reference", "pnr"
    )

    private val MONTH_MAP = mapOf(
        "jan" to 0, "january" to 0,
        "feb" to 1, "february" to 1,
        "mar" to 2, "march" to 2,
        "apr" to 3, "april" to 3,
        "may" to 4,
        "jun" to 5, "june" to 5,
        "jul" to 6, "july" to 6,
        "aug" to 7, "august" to 7,
        "sep" to 8, "sept" to 8, "september" to 8,
        "oct" to 9, "october" to 9,
        "nov" to 10, "november" to 10,
        "dec" to 11, "december" to 11
    )

    private val DAY_OF_WEEK_MAP = mapOf(
        "sunday" to Calendar.SUNDAY,
        "monday" to Calendar.MONDAY,
        "tuesday" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY
    )

    fun evaluate(
        validationResult: RelevanceValidationResult,
        ocrDocument: OcrDocument,
        contextResult: ContextClassificationResult,
        userDefaultOffsetMinutes: Int = 60
    ): AutomaticReminderDecision {
        // 1. General validity & confidence gating
        if (!validationResult.isActionable) {
            return AutomaticReminderDecision(
                isEligible = false,
                title = "",
                reason = "Not actionable"
            )
        }

        if (validationResult.confidence < 0.85f) {
            return AutomaticReminderDecision(
                isEligible = false,
                title = "",
                reason = "Confidence ${validationResult.confidence} below threshold 0.85"
            )
        }

        val fullTextLower = ocrDocument.fullText.lowercase()

        // 2. Reject categorically non-eligible categories
        when (validationResult.category) {
            Category.UNACTIONABLE,
            Category.PRODUCT,
            Category.PAYMENT,
            Category.LOCATION,
            Category.OTHER -> {
                return AutomaticReminderDecision(
                    isEligible = false,
                    title = "",
                    reason = "Category ${validationResult.category.name} ineligible for automatic reminders"
                )
            }
            Category.RECEIPT -> {
                // Receipts are only eligible if an explicit future deadline/due date is present
                val hasFutureDueEvidence = fullTextLower.contains("due date") || fullTextLower.contains("return by")
                if (!hasFutureDueEvidence) {
                    return AutomaticReminderDecision(
                        isEligible = false,
                        title = "",
                        reason = "Receipts without future deadlines are ineligible"
                    )
                }
            }
            else -> { /* Travel, Event, Study, Message proceed */ }
        }

        // 3. Strict semantic evidence validation
        val dateEntity = validationResult.validatedEntities.find { it.type == EntityType.DATE }
        val timeEntity = validationResult.validatedEntities.find { it.type == EntityType.TIME }
        val flightEntity = validationResult.validatedEntities.find { it.type == EntityType.FLIGHT }

        var hasStrongEvidence = false
        var reminderCategoryType = ""

        when (validationResult.category) {
            Category.TRAVEL -> {
                val hasFlight = flightEntity != null
                val hasTravelWords = TRAVEL_KEYWORDS.any { fullTextLower.contains(it) }
                if (hasFlight || hasTravelWords) {
                    hasStrongEvidence = true
                    reminderCategoryType = "travel"
                }
            }
            Category.STUDY -> {
                val hasStudyWords = STUDY_DEADLINE_KEYWORDS.any { fullTextLower.contains(it) }
                if (hasStudyWords && (dateEntity != null || timeEntity != null)) {
                    hasStrongEvidence = true
                    reminderCategoryType = "study"
                }
            }
            Category.EVENT -> {
                val hasAppointmentWords = APPOINTMENT_KEYWORDS.any { fullTextLower.contains(it) }
                if (hasAppointmentWords && (dateEntity != null || timeEntity != null)) {
                    hasStrongEvidence = true
                    reminderCategoryType = "event"
                }
            }
            Category.MESSAGE -> {
                val hasMeetingOrDeadline = fullTextLower.contains("meeting") ||
                        fullTextLower.contains("appointment") ||
                        fullTextLower.contains("due") ||
                        fullTextLower.contains("deadline")
                if (hasMeetingOrDeadline && (dateEntity != null || timeEntity != null)) {
                    hasStrongEvidence = true
                    reminderCategoryType = "message"
                }
            }
            else -> {}
        }

        if (!hasStrongEvidence) {
            return AutomaticReminderDecision(
                isEligible = false,
                title = "",
                reason = "Insufficient contextual evidence for a future actionable event/deadline"
            )
        }

        // 4. Must have either a valid date or a scheduled time
        if (dateEntity == null && timeEntity == null) {
            return AutomaticReminderDecision(
                isEligible = false,
                title = "",
                reason = "No valid date or time associated with the action"
            )
        }

        // 5. Parse event timestamp
        val eventCal = parseEventDateTime(dateEntity?.value, timeEntity?.value, reminderCategoryType)
        val now = System.currentTimeMillis()

        // If event is already in the past, do not create reminder
        if (eventCal.timeInMillis <= now) {
            return AutomaticReminderDecision(
                isEligible = false,
                title = "",
                reason = "Detected event time is in the past"
            )
        }

        // 6. Intelligent reminder lead time calculation (Section 3D)
        val leadTimeMinutes: Int = when (reminderCategoryType) {
            "travel" -> {
                // For flights/travel: 3 hours (180 mins) before departure
                val diffHours = (eventCal.timeInMillis - now) / (1000 * 60 * 60)
                if (diffHours >= 24) 180 else 120
            }
            "study" -> {
                // For exams/assignment deadlines: 24 hours (1440 mins) before, or 6 hours if sooner
                val diffHours = (eventCal.timeInMillis - now) / (1000 * 60 * 60)
                if (diffHours >= 36) 1440 else 360
            }
            "event", "message" -> {
                // For doctor/dentist/meetings: 2 hours (120 mins) or 1 hour (60 mins)
                val diffHours = (eventCal.timeInMillis - now) / (1000 * 60 * 60)
                if (diffHours >= 12) 120 else userDefaultOffsetMinutes.coerceAtLeast(30)
            }
            else -> userDefaultOffsetMinutes
        }

        var reminderTimestamp = eventCal.timeInMillis - (leadTimeMinutes * 60 * 1000L)
        // If reminder time has already passed, schedule immediately with slight buffer
        if (reminderTimestamp <= now) {
            reminderTimestamp = now + (5 * 60 * 1000L) // 5 minutes from now
        }

        // 7. Formulate concise, useful notification title (Section 3F)
        val displayTitle = formatReminderTitle(validationResult, dateEntity?.value, timeEntity?.value, flightEntity?.value)

        // 8. Generate event fingerprint for duplicate protection (Section 3G)
        val fingerprint = generateFingerprint(reminderCategoryType, displayTitle, eventCal.timeInMillis)

        return AutomaticReminderDecision(
            isEligible = true,
            title = displayTitle,
            eventTimestamp = eventCal.timeInMillis,
            reminderTimestamp = reminderTimestamp,
            leadTimeMinutes = leadTimeMinutes,
            fingerprint = fingerprint,
            reason = "High confidence $reminderCategoryType detected"
        )
    }

    private fun formatReminderTitle(
        validationResult: RelevanceValidationResult,
        dateStr: String?,
        timeStr: String?,
        flightCode: String?
    ): String {
        val dtSummary = when {
            dateStr != null && timeStr != null -> "$dateStr, $timeStr"
            dateStr != null -> dateStr
            timeStr != null -> timeStr
            else -> ""
        }

        return when {
            flightCode != null -> {
                if (dtSummary.isNotBlank()) "Flight $flightCode — $dtSummary" else "Flight $flightCode"
            }
            validationResult.title.contains("Appointment", ignoreCase = true) ||
                    validationResult.title.contains("Doctor", ignoreCase = true) ||
                    validationResult.title.contains("Dentist", ignoreCase = true) -> {
                val cleanTitle = when {
                    validationResult.title.contains("Doctor", ignoreCase = true) -> "Doctor appointment"
                    validationResult.title.contains("Dentist", ignoreCase = true) -> "Dentist appointment"
                    else -> "Appointment"
                }
                if (dtSummary.isNotBlank()) "$cleanTitle — $dtSummary" else cleanTitle
            }
            validationResult.title.contains("Assignment", ignoreCase = true) ||
                    validationResult.title.contains("Deadline", ignoreCase = true) ||
                    validationResult.title.contains("Exam", ignoreCase = true) -> {
                val cleanTitle = when {
                    validationResult.title.contains("Exam", ignoreCase = true) -> "Exam"
                    else -> "Assignment deadline"
                }
                if (dtSummary.isNotBlank()) "$cleanTitle — $dtSummary" else cleanTitle
            }
            validationResult.title.contains("Meeting", ignoreCase = true) -> {
                if (dtSummary.isNotBlank()) "${validationResult.title} — $dtSummary" else validationResult.title
            }
            dtSummary.isNotBlank() -> "${validationResult.title} — $dtSummary"
            else -> validationResult.title
        }
    }

    private fun parseEventDateTime(dateStr: String?, timeStr: String?, categoryType: String): Calendar {
        val cal = Calendar.getInstance()
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        // Default times based on category if time is not explicit
        var hour = if (categoryType == "study") 23 else 10
        var minute = if (categoryType == "study") 59 else 0

        // Parse time if available: e.g. "3:30 PM", "2:15 AM", "11:59 PM", "10:00 AM"
        if (!timeStr.isNullOrBlank()) {
            val timeMatcher = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*(AM|PM|am|pm)?").matcher(timeStr)
            if (timeMatcher.find()) {
                val rawHour = timeMatcher.group(1)?.toIntOrNull() ?: 10
                val rawMin = timeMatcher.group(2)?.toIntOrNull() ?: 0
                val amPm = timeMatcher.group(3)?.uppercase(Locale.US)

                hour = when (amPm) {
                    "PM" -> if (rawHour < 12) rawHour + 12 else rawHour
                    "AM" -> if (rawHour == 12) 0 else rawHour
                    else -> rawHour
                }
                minute = rawMin
            }
        }

        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)

        // Parse date if available: e.g. "2026-11-18", "September 28", "29 Sep", "Monday", "Tomorrow"
        if (!dateStr.isNullOrBlank()) {
            val lower = dateStr.lowercase().trim()
            val isoMatcher = Pattern.compile("(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})").matcher(lower)
            val slashMatcher = Pattern.compile("(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})").matcher(lower)

            if (isoMatcher.find()) {
                val year = isoMatcher.group(1)?.toIntOrNull() ?: cal.get(Calendar.YEAR)
                val month = ((isoMatcher.group(2)?.toIntOrNull() ?: 1) - 1).coerceIn(0, 11)
                val day = (isoMatcher.group(3)?.toIntOrNull() ?: 1).coerceIn(1, 31)
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, day)
            } else if (slashMatcher.find()) {
                val first = slashMatcher.group(1)?.toIntOrNull() ?: 1
                val second = slashMatcher.group(2)?.toIntOrNull() ?: 1
                val year = slashMatcher.group(3)?.toIntOrNull() ?: cal.get(Calendar.YEAR)
                val month = (if (first > 12) second - 1 else first - 1).coerceIn(0, 11)
                val day = (if (first > 12) first else second).coerceIn(1, 31)
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, day)
            } else if (lower == "tomorrow") {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            } else if (lower.startsWith("next ") || DAY_OF_WEEK_MAP.keys.any { lower.contains(it) }) {
                val targetDayName = DAY_OF_WEEK_MAP.keys.find { lower.contains(it) }
                if (targetDayName != null) {
                    val targetDay = DAY_OF_WEEK_MAP[targetDayName] ?: Calendar.MONDAY
                    val currentDay = cal.get(Calendar.DAY_OF_WEEK)
                    var daysToAdd = (targetDay - currentDay + 7) % 7
                    if (daysToAdd == 0) daysToAdd = 7
                    cal.add(Calendar.DAY_OF_YEAR, daysToAdd)
                }
            } else {
                // Try month + day: "September 28", "Sep 25", "28 September"
                val monthEntry = MONTH_MAP.entries.find { lower.contains(it.key) }
                val dayMatcher = Pattern.compile("\\b(\\d{1,2})\\b").matcher(lower)
                val day = if (dayMatcher.find()) dayMatcher.group(1)?.toIntOrNull() else null

                if (monthEntry != null && day != null && day in 1..31) {
                    cal.set(Calendar.MONTH, monthEntry.value)
                    cal.set(Calendar.DAY_OF_MONTH, day)

                    // If parsed date in current year is already past, assume next year
                    val nowCal = Calendar.getInstance()
                    if (cal.before(nowCal)) {
                        cal.set(Calendar.YEAR, nowCal.get(Calendar.YEAR) + 1)
                    }
                }
            }
        } else {
            // No explicit date: if time already passed today, assume tomorrow
            if (cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        return cal
    }

    private fun generateFingerprint(category: String, title: String, eventTimestamp: Long): String {
        // Approximate time to within 2-hour window so duplicate screenshots of the same event match
        val timeBucket = eventTimestamp / (1000 * 60 * 60 * 2)
        val cleanTitle = title.lowercase().replace(Regex("[^a-z0-9]"), "")
        return "${category}_${cleanTitle.take(24)}_$timeBucket"
    }
}
