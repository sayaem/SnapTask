package com.example.ai.classifier

import com.example.domain.model.OcrDocument
import com.example.domain.model.OcrLine
import com.example.domain.model.ScreenshotContext

data class ContextClassificationResult(
    val context: ScreenshotContext,
    val confidence: Float,
    val hasStatusBar: Boolean,
    val hasNavigationBar: Boolean,
    val isActionableCandidate: Boolean,
    val evidence: List<String> = emptyList()
)

object ContextClassifier {

    private val TIME_STATUS_REGEX = "^\\s*(\\d{1,2}[:.]\\d{2})\\s*(?:[AaPp][Mm])?\\s*$".toRegex()
    private val BATTERY_STATUS_REGEX = "^\\s*(\\d{1,3})%?\\s*$".toRegex()
    private val STATUS_BAR_INDICATORS = setOf(
        "5G", "4G", "LTE", "3G", "VoLTE", "WiFi", "Wi-Fi", "VOLTE", "AM", "PM"
    )

    private val TRAVEL_INDICATORS = listOf(
        "flight", "airline", "boarding pass", "departure", "arrival", "gate", "terminal",
        "e-ticket", "itinerary", "pnr", "seat", "baggage", "boarding time", "booking reference",
        "train", "platform", "coach"
    )

    private val AIRLINE_NAMES = listOf(
        "biman", "emirates", "qatar", "singapore airlines", "delta", "united", "american airlines",
        "british airways", "lufthansa", "cathay", "air india", "indigo", "etihad", "klm", "air france",
        "turkish airlines", "saudia", "flydubai", "gulf air"
    )

    private val RECEIPT_INDICATORS = listOf(
        "receipt", "invoice", "subtotal", "tax", "total", "amount due", "order #", "order number",
        "cashier", "items sold", "qty", "payment method", "balance due", "bill to", "tax invoice"
    )

    private val PAYMENT_INDICATORS = listOf(
        "payment successful", "payment received", "money sent", "transfer successful", "transferred",
        "transaction id", "transaction successful", "txn id", "reference id", "reference number",
        "paid to", "sent to", "received from", "transfer complete"
    )

    private val APPOINTMENT_INDICATORS = listOf(
        "appointment", "doctor", "dentist", "clinic", "hospital", "consultation", "scheduled for",
        "reservation confirmed", "meeting scheduled", "patient", "dr.", "physician", "checkup"
    )

    private val STUDY_INDICATORS = listOf(
        "assignment", "submission deadline", "exam", "syllabus", "lecture", "due date", "homework",
        "course code", "quiz", "submission", "midterm", "final exam"
    )

    private val SHOPPING_INDICATORS = listOf(
        "add to cart", "buy now", "in stock", "out of stock", "free delivery", "order summary",
        "item description", "customer reviews", "ratings & reviews"
    )

    private val SETTINGS_INDICATORS = listOf(
        "network & internet", "connected devices", "apps & notifications", "battery", "display",
        "sound & vibration", "storage", "privacy", "location", "security", "accounts", "accessibility",
        "system", "about phone", "search settings"
    )

    fun classifyContext(ocrDocument: OcrDocument): ContextClassificationResult {
        val fullText = ocrDocument.fullText.trim()
        if (fullText.isBlank()) {
            return ContextClassificationResult(
                context = ScreenshotContext.UNACTIONABLE,
                confidence = 1.0f,
                hasStatusBar = false,
                hasNavigationBar = false,
                isActionableCandidate = false,
                evidence = listOf("Empty text")
            )
        }

        val lines = ocrDocument.lines.ifEmpty {
            OcrDocument.fromRawText(fullText).lines
        }

        val evidence = mutableListOf<String>()

        // 1. Detect System UI components (Status Bar & Navigation Bar)
        val statusBarLines = lines.filter { it.bounds?.isStatusBar == true }
        val hasStatusClock = statusBarLines.any { line ->
            TIME_STATUS_REGEX.matches(line.text.trim())
        }
        val hasStatusBattery = statusBarLines.any { line ->
            val t = line.text.trim()
            BATTERY_STATUS_REGEX.matches(t) && (t.endsWith("%") || t.toIntOrNull() in 1..100)
        }
        val hasStatusNetwork = statusBarLines.any { line ->
            STATUS_BAR_INDICATORS.any { line.text.contains(it, ignoreCase = true) }
        }
        val hasStatusBar = hasStatusClock || (hasStatusBattery && hasStatusNetwork)
        if (hasStatusBar) evidence.add("Detected Status Bar (time/battery/network at top)")

        val navBarLines = lines.filter { it.bounds?.isNavigationBar == true }
        val hasNavigationBar = navBarLines.isNotEmpty()

        val lowerText = fullText.lowercase()

        // 2. Test Settings Screen
        val settingsMatches = SETTINGS_INDICATORS.count { lowerText.contains(it) }
        if (settingsMatches >= 3 || (lowerText.startsWith("settings") && settingsMatches >= 2)) {
            evidence.add("Matched $settingsMatches Android Settings categories")
            return ContextClassificationResult(
                context = ScreenshotContext.SETTINGS,
                confidence = 0.95f,
                hasStatusBar = hasStatusBar,
                hasNavigationBar = hasNavigationBar,
                isActionableCandidate = false,
                evidence = evidence
            )
        }

        // 3. Test Travel / Ticket
        val flightCodeRegex = "\\b([A-Z]{2}|[A-Z0-9]{2})\\s?\\d{3,4}\\b".toRegex()
        val hasFlightCode = flightCodeRegex.containsMatchIn(ocrDocument.fullText)
        val travelMatches = TRAVEL_INDICATORS.count { lowerText.contains(it) }
        val hasAirline = AIRLINE_NAMES.any { lowerText.contains(it) }
        val hasRouteSymbol = fullText.contains("→") || fullText.contains("->") || fullText.contains("—")

        if (hasFlightCode && (travelMatches >= 1 || hasAirline)) {
            evidence.add("Found flight code and travel terminology")
            return ContextClassificationResult(
                context = ScreenshotContext.TRAVEL_TICKET,
                confidence = 0.95f,
                hasStatusBar = hasStatusBar,
                hasNavigationBar = hasNavigationBar,
                isActionableCandidate = true,
                evidence = evidence
            )
        }
        if (travelMatches >= 3 || (travelMatches >= 2 && hasRouteSymbol)) {
            evidence.add("Found strong travel keywords and itinerary indicators")
            return ContextClassificationResult(
                context = ScreenshotContext.TRAVEL_TICKET,
                confidence = 0.90f,
                hasStatusBar = hasStatusBar,
                hasNavigationBar = hasNavigationBar,
                isActionableCandidate = true,
                evidence = evidence
            )
        }

        // 4. Test Payment Confirmation
        val paymentMatches = PAYMENT_INDICATORS.count { lowerText.contains(it) }
        if (paymentMatches >= 1 && (lowerText.contains("transaction") || lowerText.contains("paid") || lowerText.contains("transferred") || lowerText.contains("sent"))) {
            evidence.add("Found payment transaction confirmation verbs and identifiers")
            return ContextClassificationResult(
                context = ScreenshotContext.PAYMENT,
                confidence = 0.95f,
                hasStatusBar = hasStatusBar,
                hasNavigationBar = hasNavigationBar,
                isActionableCandidate = true,
                evidence = evidence
            )
        }

        // 5. Test Receipt / Invoice
        val receiptMatches = RECEIPT_INDICATORS.count { lowerText.contains(it) }
        val hasPriceSymbol = fullText.contains("$") || fullText.contains("€") || fullText.contains("£") || fullText.contains("USD") || fullText.contains("৳")
        val hasItemizedPrice = "\\b\\d+[.,]\\d{2}\\b".toRegex().containsMatchIn(fullText)

        if ((receiptMatches >= 2 && hasPriceSymbol) || (receiptMatches >= 1 && hasPriceSymbol && hasItemizedPrice && lowerText.contains("total"))) {
            evidence.add("Found receipt financial indicators and total calculation")
            return ContextClassificationResult(
                context = ScreenshotContext.RECEIPT_INVOICE,
                confidence = 0.92f,
                hasStatusBar = hasStatusBar,
                hasNavigationBar = hasNavigationBar,
                isActionableCandidate = true,
                evidence = evidence
            )
        }

        // 6. Test Appointment / Booking
        val appointmentMatches = APPOINTMENT_INDICATORS.count { lowerText.contains(it) }
        if (appointmentMatches >= 2 || (appointmentMatches >= 1 && (lowerText.contains("appointment") || lowerText.contains("reservation")))) {
            evidence.add("Found appointment/doctor/booking terminology")
            return ContextClassificationResult(
                context = ScreenshotContext.FORM_APPOINTMENT,
                confidence = 0.92f,
                hasStatusBar = hasStatusBar,
                hasNavigationBar = hasNavigationBar,
                isActionableCandidate = true,
                evidence = evidence
            )
        }

        // 7. Test Study / Exam / Assignment
        val studyMatches = STUDY_INDICATORS.count { lowerText.contains(it) }
        if (studyMatches >= 2 || (studyMatches >= 1 && lowerText.contains("due date"))) {
            evidence.add("Found educational assignment / exam keywords")
            return ContextClassificationResult(
                context = ScreenshotContext.STUDY_DOCUMENT,
                confidence = 0.90f,
                hasStatusBar = hasStatusBar,
                hasNavigationBar = hasNavigationBar,
                isActionableCandidate = true,
                evidence = evidence
            )
        }

        // 8. Test Product / Shopping
        val shoppingMatches = SHOPPING_INDICATORS.count { lowerText.contains(it) }
        if (shoppingMatches >= 1 && hasPriceSymbol) {
            evidence.add("Found e-commerce shopping indicators and price")
            return ContextClassificationResult(
                context = ScreenshotContext.PRODUCT_SHOPPING,
                confidence = 0.88f,
                hasStatusBar = hasStatusBar,
                hasNavigationBar = hasNavigationBar,
                isActionableCandidate = true,
                evidence = evidence
            )
        }

        // 9. Test Home Screen / Launcher UI
        // Characteristics of a launcher:
        // - Often has status bar
        // - Majority of lines are short: 1 to 3 words
        // - No complete sentences or conversational punctuation
        // - App label patterns or launcher search
        val nonStatusLines = lines.filter { it.bounds?.isStatusBar != true && it.bounds?.isNavigationBar != true }
        val lineWordCounts = nonStatusLines.map { line ->
            line.text.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size
        }.filter { it > 0 }

        val isGridOfShortLabels = lineWordCounts.isNotEmpty() && lineWordCounts.count { it <= 2 }.toFloat() / lineWordCounts.size >= 0.70f
        val hasSearchWidget = lowerText.contains("search apps") || (lowerText.contains("google") && lineWordCounts.count { it <= 2 } >= 4)
        val hasNoPunctuationOrSentences = !fullText.contains(".") && !fullText.contains("?") && !fullText.contains("!") && !fullText.contains(":")

        if ((hasStatusBar || hasSearchWidget) && isGridOfShortLabels && lineWordCounts.size >= 3) {
            evidence.add("Detected app launcher grid: high concentration of isolated short app labels without body text")
            return ContextClassificationResult(
                context = ScreenshotContext.HOME_SCREEN,
                confidence = 0.95f,
                hasStatusBar = hasStatusBar,
                hasNavigationBar = hasNavigationBar,
                isActionableCandidate = false,
                evidence = evidence
            )
        }

        // 10. Test Chat / Conversational Message
        val chatSignals = listOf("please send", "can you", "let me know", "meet me", "don't forget", "remember to", "see you tomorrow", "type a message")
        if (chatSignals.any { lowerText.contains(it) }) {
            evidence.add("Detected conversational instruction / task phrasing")
            return ContextClassificationResult(
                context = ScreenshotContext.CHAT_MESSAGE,
                confidence = 0.85f,
                hasStatusBar = hasStatusBar,
                hasNavigationBar = hasNavigationBar,
                isActionableCandidate = true,
                evidence = evidence
            )
        }

        // 11. Test Webpage with metadata timestamps
        if (lowerText.contains("http://") || lowerText.contains("https://") || lowerText.contains(".com") || lowerText.contains("updated ") || lowerText.contains("published ")) {
            evidence.add("Detected web browser / article structure")
            return ContextClassificationResult(
                context = ScreenshotContext.WEBPAGE,
                confidence = 0.75f,
                hasStatusBar = hasStatusBar,
                hasNavigationBar = hasNavigationBar,
                isActionableCandidate = false,
                evidence = evidence
            )
        }

        // Default / Other: conservatively treated as non-actionable unless proven otherwise by candidate extraction
        evidence.add("Generic or unclassified screenshot context")
        return ContextClassificationResult(
            context = ScreenshotContext.OTHER,
            confidence = 0.50f,
            hasStatusBar = hasStatusBar,
            hasNavigationBar = hasNavigationBar,
            isActionableCandidate = false,
            evidence = evidence
        )
    }
}
