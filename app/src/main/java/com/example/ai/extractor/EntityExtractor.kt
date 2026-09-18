package com.example.ai.extractor

import com.example.domain.model.BoundingBox
import com.example.domain.model.EntityType
import com.example.domain.model.OcrDocument
import com.example.domain.model.OcrLine
import java.util.regex.Pattern

data class CandidateEntity(
    val type: EntityType,
    val label: String,
    val value: String,
    val sourceLine: String = "",
    val bounds: BoundingBox? = null,
    val rawConfidence: Float = 0.9f
)

object EntityExtractor {

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

    private val timePattern = Pattern.compile(
        "\\b(\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:AM|PM|am|pm)?)\\b"
    )

    private val pricePattern = Pattern.compile(
        "([\\$€£৳₹¥]\\s*\\d+(?:[.,]\\d{2})?|\\b\\d+(?:[.,]\\d{2})?\\s*(?:USD|EUR|BDT|GBP)\\b)",
        Pattern.CASE_INSENSITIVE
    )

    private val urlPattern = Pattern.compile(
        "\\b(https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:/[^\\s]*)?|www\\.[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:/[^\\s]*)?)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val emailPattern = Pattern.compile(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"
    )

    private val phonePattern = Pattern.compile(
        "\\b(?:\\+?\\d{1,3}[- .]?)?\\(?\\d{3}\\)?[- .]?\\d{3}[- .]?\\d{4}\\b"
    )

    private val flightPattern = Pattern.compile(
        "\\b([A-Z]{2}\\s?\\d{2,4}|[A-Z0-9]{2}\\s?\\d{3,4})\\b"
    )

    private val routePattern = Pattern.compile(
        "([A-Za-z\\s]{3,25})\\s*(?:→|->|to|—)\\s*([A-Za-z\\s]{3,25})",
        Pattern.CASE_INSENSITIVE
    )

    private val orderPattern = Pattern.compile(
        "(?:Order|Booking|Reference|Invoice|Order ID|Transaction ID|Txn ID)[:#\\s]+([A-Za-z0-9-_]{4,25})\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val commonAirlines = listOf(
        "Biman Bangladesh Airlines", "Biman", "Emirates", "Qatar Airways", "Qatar",
        "Singapore Airlines", "Delta", "United Airlines", "United", "American Airlines",
        "British Airways", "Lufthansa", "Air India", "IndiGo", "Etihad"
    )

    private val commonProducts = listOf(
        "AirPods Pro", "AirPods", "Sony WH-1000XM6", "Sony WH-1000XM5",
        "iPhone 16 Pro", "iPhone 16", "MacBook Pro", "MacBook Air",
        "ASUS ROG", "ASUS Zenbook", "Lenovo ThinkPad", "iPad Pro",
        "Apple Watch", "Galaxy S24", "Pixel 9 Pro", "Keyboard", "Headphones"
    )

    fun extractCandidates(ocrDocument: OcrDocument): List<CandidateEntity> {
        val candidates = mutableListOf<CandidateEntity>()
        val lines = ocrDocument.lines.ifEmpty {
            OcrDocument.fromRawText(ocrDocument.fullText).lines
        }

        for (line in lines) {
            val lineText = line.text.trim()
            if (lineText.isBlank()) continue

            // 1. Flight extraction
            val flightMatcher = flightPattern.matcher(lineText)
            while (flightMatcher.find()) {
                val candidate = flightMatcher.group(1)?.replace(" ", "") ?: ""
                if (candidate.length in 4..7 && candidate.any { it.isDigit() } && candidate.any { it.isLetter() }) {
                    candidates.add(
                        CandidateEntity(
                            type = EntityType.FLIGHT,
                            label = "Flight",
                            value = candidate,
                            sourceLine = lineText,
                            bounds = line.bounds,
                            rawConfidence = 0.95f
                        )
                    )
                }
            }

            // 2. Route extraction
            val routeMatcher = routePattern.matcher(lineText)
            while (routeMatcher.find()) {
                val origin = routeMatcher.group(1)?.trim() ?: ""
                val dest = routeMatcher.group(2)?.trim() ?: ""
                candidates.add(
                    CandidateEntity(
                        type = EntityType.LOCATION,
                        label = "Route",
                        value = "$origin → $dest",
                        sourceLine = lineText,
                        bounds = line.bounds,
                        rawConfidence = 0.90f
                    )
                )
            }

            // 3. Date extraction
            val d1 = datePattern1.matcher(lineText)
            if (d1.find()) {
                candidates.add(
                    CandidateEntity(
                        type = EntityType.DATE,
                        label = "Date",
                        value = d1.group(0)?.trim() ?: "",
                        sourceLine = lineText,
                        bounds = line.bounds,
                        rawConfidence = 0.95f
                    )
                )
            } else {
                val d2 = datePattern2.matcher(lineText)
                if (d2.find()) {
                    candidates.add(
                        CandidateEntity(
                            type = EntityType.DATE,
                            label = "Date",
                            value = d2.group(0)?.trim() ?: "",
                            sourceLine = lineText,
                            bounds = line.bounds,
                            rawConfidence = 0.90f
                        )
                    )
                } else {
                    val dRel = relativeDatePattern.matcher(lineText)
                    if (dRel.find()) {
                        val relStr = dRel.group(0)?.trim()?.replaceFirstChar { it.uppercase() } ?: ""
                        candidates.add(
                            CandidateEntity(
                                type = EntityType.DATE,
                                label = "Date",
                                value = relStr,
                                sourceLine = lineText,
                                bounds = line.bounds,
                                rawConfidence = 0.90f
                            )
                        )
                    } else {
                        val dNum = datePatternNumeric.matcher(lineText)
                        if (dNum.find()) {
                            candidates.add(
                                CandidateEntity(
                                    type = EntityType.DATE,
                                    label = "Date",
                                    value = dNum.group(0)?.trim() ?: "",
                                    sourceLine = lineText,
                                    bounds = line.bounds,
                                    rawConfidence = 0.80f
                                )
                            )
                        }
                    }
                }
            }

            // 4. Time extraction
            val tMatcher = timePattern.matcher(lineText)
            while (tMatcher.find()) {
                candidates.add(
                    CandidateEntity(
                        type = EntityType.TIME,
                        label = "Time",
                        value = tMatcher.group(1)?.trim() ?: "",
                        sourceLine = lineText,
                        bounds = line.bounds,
                        rawConfidence = 0.92f
                    )
                )
            }

            // 5. Price extraction
            val pMatcher = pricePattern.matcher(lineText)
            while (pMatcher.find()) {
                candidates.add(
                    CandidateEntity(
                        type = EntityType.PRICE,
                        label = "Price",
                        value = pMatcher.group(0)?.trim() ?: "",
                        sourceLine = lineText,
                        bounds = line.bounds,
                        rawConfidence = 0.95f
                    )
                )
            }

            // 6. URL extraction
            val uMatcher = urlPattern.matcher(lineText)
            while (uMatcher.find()) {
                candidates.add(
                    CandidateEntity(
                        type = EntityType.URL,
                        label = "Link",
                        value = uMatcher.group(0)?.trim() ?: "",
                        sourceLine = lineText,
                        bounds = line.bounds,
                        rawConfidence = 0.98f
                    )
                )
            }

            // 7. Phone extraction
            val phoneMatcher = phonePattern.matcher(lineText)
            while (phoneMatcher.find()) {
                candidates.add(
                    CandidateEntity(
                        type = EntityType.PHONE,
                        label = "Phone",
                        value = phoneMatcher.group(0)?.trim() ?: "",
                        sourceLine = lineText,
                        bounds = line.bounds,
                        rawConfidence = 0.90f
                    )
                )
            }

            // 8. Email extraction
            val emailMatcher = emailPattern.matcher(lineText)
            while (emailMatcher.find()) {
                candidates.add(
                    CandidateEntity(
                        type = EntityType.EMAIL,
                        label = "Email",
                        value = emailMatcher.group(0)?.trim() ?: "",
                        sourceLine = lineText,
                        bounds = line.bounds,
                        rawConfidence = 0.95f
                    )
                )
            }

            // 9. Order / Transaction ID
            val oMatcher = orderPattern.matcher(lineText)
            if (oMatcher.find()) {
                val idVal = oMatcher.group(1)?.trim() ?: ""
                val label = if (lineText.contains("transaction", ignoreCase = true) || lineText.contains("txn", ignoreCase = true)) "Transaction ID" else "Order ID"
                candidates.add(
                    CandidateEntity(
                        type = EntityType.ORDER_ID,
                        label = label,
                        value = idVal,
                        sourceLine = lineText,
                        bounds = line.bounds,
                        rawConfidence = 0.90f
                    )
                )
            }

            // 10. Address / Location candidate heuristics
            val addressKeywords = listOf("Street", "St.", "Road", "Rd.", "Avenue", "Ave.", "Boulevard", "Blvd", "Lane", "Way", "Clinic", "Hospital", "Airport", "Terminal")
            if (addressKeywords.any { lineText.contains(it, ignoreCase = true) } && lineText.length in 5..60) {
                candidates.add(
                    CandidateEntity(
                        type = EntityType.LOCATION,
                        label = "Location",
                        value = lineText,
                        sourceLine = lineText,
                        bounds = line.bounds,
                        rawConfidence = 0.85f
                    )
                )
            }
        }

        // 11. Document-level Product matches
        for (prod in commonProducts) {
            val matchingLine = lines.find { it.text.contains(prod, ignoreCase = true) }
            if (matchingLine != null) {
                candidates.add(
                    CandidateEntity(
                        type = EntityType.PRODUCT,
                        label = "Product",
                        value = prod,
                        sourceLine = matchingLine.text,
                        bounds = matchingLine.bounds,
                        rawConfidence = 0.95f
                    )
                )
                break
            }
        }

        // 12. Document-level Airline matches
        for (airline in commonAirlines) {
            val matchingLine = lines.find { it.text.contains(airline, ignoreCase = true) }
            if (matchingLine != null) {
                candidates.add(
                    0,
                    CandidateEntity(
                        type = EntityType.NOTE,
                        label = "Airline",
                        value = airline,
                        sourceLine = matchingLine.text,
                        bounds = matchingLine.bounds,
                        rawConfidence = 0.98f
                    )
                )
                break
            }
        }

        return candidates
    }

    // Retained for backward compatibility
    fun extractAll(rawText: String): List<com.example.ai.extractor.ExtractedRawEntity> {
        val ocrDoc = OcrDocument.fromRawText(rawText)
        val candidates = extractCandidates(ocrDoc)
        return candidates.map {
            ExtractedRawEntity(
                type = it.type,
                label = it.label,
                value = it.value,
                confidence = it.rawConfidence,
                isAmbiguous = false
            )
        }
    }
}

data class ExtractedRawEntity(
    val type: EntityType,
    val label: String,
    val value: String,
    val confidence: Float = 0.9f,
    val isAmbiguous: Boolean = false
)
