package com.example.ai.extractor

import com.example.domain.model.EntityType
import java.util.regex.Pattern

data class ExtractedRawEntity(
    val type: EntityType,
    val label: String,
    val value: String,
    val confidence: Float = 0.9f,
    val isAmbiguous: Boolean = false
)

object EntityExtractor {

    // Regex for Dates: e.g. "October 14", "Oct 14", "September 25", "Sep 25, 2026", "25 September 2026", "14/10/2026", "2026-10-14", "tomorrow"
    private val monthNames = "January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec"
    private val datePattern1 = Pattern.compile(
        "\\b($monthNames)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?\\b",
        Pattern.CASE_INSENSITIVE
    )
    private val datePattern2 = Pattern.compile(
        "\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+($monthNames)(?:,?\\s+(\\d{4}))?\\b",
        Pattern.CASE_INSENSITIVE
    )
    private val datePatternNumeric = Pattern.compile(
        "\\b(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\b"
    )
    private val relativeDatePattern = Pattern.compile(
        "\\b(today|tomorrow|tonight|next\\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday|week))\\b",
        Pattern.CASE_INSENSITIVE
    )

    // Regex for Times: e.g. "3:30 PM", "2:15 AM", "02:15", "15:45", "7:00pm", "11:00 AM"
    private val timePattern = Pattern.compile(
        "\\b(\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:AM|PM|am|pm)?)\\b"
    )

    // Regex for Prices: e.g. "$249", "$399.99", "€150", "£49", "৳2500"
    private val pricePattern = Pattern.compile(
        "([\\$€£৳₹¥]\\s*\\d+(?:[.,]\\d{2})?|\\b\\d+(?:[.,]\\d{2})?\\s*(?:USD|EUR|BDT|GBP)\\b)",
        Pattern.CASE_INSENSITIVE
    )

    // Regex for URLs
    private val urlPattern = Pattern.compile(
        "\\b(https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:/[^\\s]*)?|www\\.[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:/[^\\s]*)?)\\b",
        Pattern.CASE_INSENSITIVE
    )

    // Regex for Emails
    private val emailPattern = Pattern.compile(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"
    )

    // Regex for Phone numbers
    private val phonePattern = Pattern.compile(
        "\\b(?:\\+?\\d{1,3}[- .]?)?\\(?\\d{3}\\)?[- .]?\\d{3}[- .]?\\d{4}\\b"
    )

    // Flight number regex: e.g. BG147, EK202, SQ318, AA1234, UA850
    private val flightPattern = Pattern.compile(
        "\\b([A-Z]{2}\\s?\\d{2,4}|[A-Z0-9]{2}\\s?\\d{3,4})\\b"
    )

    // Route regex: e.g. "Dhaka → Dubai", "Dhaka - Dubai", "Dhaka to Dubai", "SFO -> JFK"
    private val routePattern = Pattern.compile(
        "([A-Za-z\\s]+)\\s*(?:→|->|to|—)\\s*([A-Za-z\\s]+)",
        Pattern.CASE_INSENSITIVE
    )

    // Order ID regex
    private val orderPattern = Pattern.compile(
        "(?:Order|Booking|Reference|Invoice|Order ID)[:#\\s]+([A-Za-z0-9-_]{4,20})\\b",
        Pattern.CASE_INSENSITIVE
    )

    fun extractAll(rawText: String): List<ExtractedRawEntity> {
        val entities = mutableListOf<ExtractedRawEntity>()
        val text = rawText.trim()
        if (text.isEmpty()) return entities

        // 1. Flight extraction
        if (text.contains("flight", ignoreCase = true) || text.contains("airline", ignoreCase = true) || text.contains("boarding", ignoreCase = true)) {
            val matcher = flightPattern.matcher(text)
            while (matcher.find()) {
                val candidate = matcher.group(1) ?: ""
                val clean = candidate.replace(" ", "")
                // Avoid matching common false positives like "AM", "PM"
                if (clean.length in 4..7 && clean.any { it.isDigit() } && clean.any { it.isLetter() }) {
                    entities.add(
                        ExtractedRawEntity(
                            type = EntityType.FLIGHT,
                            label = "Flight",
                            value = clean,
                            confidence = 0.95f
                        )
                    )
                    break
                }
            }
        }

        // 2. Route extraction
        if (text.contains("→") || text.contains("->") || text.contains(" to ", ignoreCase = true)) {
            val routeMatcher = routePattern.matcher(text)
            while (routeMatcher.find()) {
                val origin = routeMatcher.group(1)?.trim() ?: ""
                val dest = routeMatcher.group(2)?.trim() ?: ""
                if (origin.length in 3..30 && dest.length in 3..30 && !origin.equals(dest, ignoreCase = true)) {
                    entities.add(
                        ExtractedRawEntity(
                            type = EntityType.LOCATION,
                            label = "Route",
                            value = "$origin → $dest",
                            confidence = 0.92f
                        )
                    )
                    break
                }
            }
        }

        // 3. Date extraction
        var dateFound = false
        val matcher1 = datePattern1.matcher(text)
        if (matcher1.find()) {
            val dateVal = matcher1.group(0)?.trim() ?: ""
            val isContextClear = text.contains("appointment", ignoreCase = true) ||
                    text.contains("flight", ignoreCase = true) ||
                    text.contains("deadline", ignoreCase = true) ||
                    text.contains("event", ignoreCase = true) ||
                    text.contains("departure", ignoreCase = true)
            entities.add(
                ExtractedRawEntity(
                    type = EntityType.DATE,
                    label = "Date",
                    value = dateVal,
                    confidence = if (isContextClear) 0.95f else 0.75f,
                    isAmbiguous = !isContextClear
                )
            )
            dateFound = true
        }

        if (!dateFound) {
            val matcher2 = datePattern2.matcher(text)
            if (matcher2.find()) {
                val dateVal = matcher2.group(0)?.trim() ?: ""
                entities.add(
                    ExtractedRawEntity(
                        type = EntityType.DATE,
                        label = "Date",
                        value = dateVal,
                        confidence = 0.9f,
                        isAmbiguous = false
                    )
                )
                dateFound = true
            }
        }

        if (!dateFound) {
            val matcherRel = relativeDatePattern.matcher(text)
            if (matcherRel.find()) {
                val relDate = matcherRel.group(0)?.trim()?.replaceFirstChar { it.uppercase() } ?: ""
                entities.add(
                    ExtractedRawEntity(
                        type = EntityType.DATE,
                        label = "Date",
                        value = relDate,
                        confidence = 0.92f,
                        isAmbiguous = false
                    )
                )
                dateFound = true
            }
        }

        if (!dateFound) {
            val matcherNum = datePatternNumeric.matcher(text)
            if (matcherNum.find()) {
                val numDate = matcherNum.group(0)?.trim() ?: ""
                entities.add(
                    ExtractedRawEntity(
                        type = EntityType.DATE,
                        label = "Date",
                        value = numDate,
                        confidence = 0.8f,
                        isAmbiguous = true // Numeric dates might be expiry, order date, event date
                    )
                )
            }
        }

        // 4. Time extraction
        val timeMatcher = timePattern.matcher(text)
        if (timeMatcher.find()) {
            val timeVal = timeMatcher.group(1)?.trim() ?: ""
            entities.add(
                ExtractedRawEntity(
                    type = EntityType.TIME,
                    label = "Time",
                    value = timeVal,
                    confidence = 0.94f
                )
            )
        }

        // 5. Price extraction
        val priceMatcher = pricePattern.matcher(text)
        if (priceMatcher.find()) {
            val priceVal = priceMatcher.group(0)?.trim() ?: ""
            entities.add(
                ExtractedRawEntity(
                    type = EntityType.PRICE,
                    label = "Price",
                    value = priceVal,
                    confidence = 0.95f
                )
            )
        }

        // 6. Product extraction
        val commonProducts = listOf(
            "AirPods Pro", "AirPods", "Sony WH-1000XM6", "Sony WH-1000XM5",
            "iPhone 16 Pro", "iPhone 16", "MacBook Pro", "MacBook Air",
            "ASUS ROG", "ASUS Zenbook", "Lenovo ThinkPad", "iPad Pro",
            "Apple Watch", "Galaxy S24", "Pixel 9 Pro"
        )
        for (prod in commonProducts) {
            if (text.contains(prod, ignoreCase = true)) {
                entities.add(
                    ExtractedRawEntity(
                        type = EntityType.PRODUCT,
                        label = "Product",
                        value = prod,
                        confidence = 0.96f
                    )
                )
                break
            }
        }

        // 7. URL extraction
        val urlMatcher = urlPattern.matcher(text)
        if (urlMatcher.find()) {
            val urlVal = urlMatcher.group(0)?.trim() ?: ""
            entities.add(
                ExtractedRawEntity(
                    type = EntityType.URL,
                    label = "Link",
                    value = urlVal,
                    confidence = 0.98f
                )
            )
        }

        // 8. Phone extraction
        val phoneMatcher = phonePattern.matcher(text)
        if (phoneMatcher.find()) {
            val phoneVal = phoneMatcher.group(0)?.trim() ?: ""
            entities.add(
                ExtractedRawEntity(
                    type = EntityType.PHONE,
                    label = "Phone",
                    value = phoneVal,
                    confidence = 0.9f
                )
            )
        }

        // 9. Email extraction
        val emailMatcher = emailPattern.matcher(text)
        if (emailMatcher.find()) {
            val emailVal = emailMatcher.group(0)?.trim() ?: ""
            entities.add(
                ExtractedRawEntity(
                    type = EntityType.EMAIL,
                    label = "Email",
                    value = emailVal,
                    confidence = 0.95f
                )
            )
        }

        // 10. Order ID
        val orderMatcher = orderPattern.matcher(text)
        if (orderMatcher.find()) {
            val orderVal = orderMatcher.group(1)?.trim() ?: ""
            entities.add(
                ExtractedRawEntity(
                    type = EntityType.ORDER_ID,
                    label = "Order ID",
                    value = orderVal,
                    confidence = 0.9f
                )
            )
        }

        // 11. Location / Address heuristics
        if (entities.none { it.type == EntityType.LOCATION }) {
            val locKeywords = listOf("Airport", "Hospital", "Clinic", "Street", "Ave", "Road", "Square", "Terminal", "Hotel")
            for (line in text.lines()) {
                val trimmed = line.trim()
                if (locKeywords.any { trimmed.contains(it, ignoreCase = true) } && trimmed.length in 5..50) {
                    entities.add(
                        ExtractedRawEntity(
                            type = EntityType.LOCATION,
                            label = "Location",
                            value = trimmed,
                            confidence = 0.85f
                        )
                    )
                    break
                }
            }
        }

        // 12. Airline heuristic if flight exists
        if (entities.any { it.type == EntityType.FLIGHT }) {
            val airlines = listOf("Biman Bangladesh Airlines", "Emirates", "Qatar Airways", "Singapore Airlines", "Delta", "United", "American Airlines", "British Airways")
            for (airline in airlines) {
                if (text.contains(airline, ignoreCase = true)) {
                    entities.add(
                        0,
                        ExtractedRawEntity(
                            type = EntityType.NOTE,
                            label = "Airline",
                            value = airline,
                            confidence = 0.98f
                        )
                    )
                    break
                }
            }
        }

        return entities
    }
}
