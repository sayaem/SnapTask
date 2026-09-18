package com.example.domain.model

enum class Category(val icon: String, val displayName: String) {
    EVENT("📅", "Event"),
    TRAVEL("✈️", "Travel"),
    PRODUCT("🛒", "Product"),
    RECEIPT("🧾", "Receipt"),
    MESSAGE("💬", "Message"),
    STUDY("📚", "Study"),
    LOCATION("📍", "Location"),
    PAYMENT("💳", "Payment"),
    OTHER("📄", "Other"),
    UNACTIONABLE("✨", "Nothing Actionable");

    val isActionable: Boolean
        get() = this != UNACTIONABLE && this != OTHER

    companion object {
        fun fromString(value: String): Category {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: OTHER
        }
    }
}

enum class ScreenshotContext(val displayName: String, val isGenerallyActionable: Boolean) {
    HOME_SCREEN("Home Screen", false),
    LOCK_SCREEN("Lock Screen", false),
    SYSTEM_UI("System UI", false),
    SETTINGS("Settings", false),
    SOCIAL_MEDIA("Social Media", false),
    CHAT_MESSAGE("Chat / Message", true),
    EMAIL("Email", true),
    WEBPAGE("Webpage", false),
    CALENDAR_EVENT("Calendar / Event", true),
    TRAVEL_TICKET("Travel / Ticket", true),
    RECEIPT_INVOICE("Receipt / Invoice", true),
    PRODUCT_SHOPPING("Product / Shopping", true),
    PAYMENT("Payment Confirmation", true),
    STUDY_DOCUMENT("Study / Document", true),
    MAP_LOCATION("Map / Location", true),
    FORM_APPOINTMENT("Appointment / Booking", true),
    UNACTIONABLE("Unactionable", false),
    OTHER("Other", false)
}

data class BoundingBox(
    val left: Float,   // Normalized 0.0 .. 1.0
    val top: Float,    // Normalized 0.0 .. 1.0
    val right: Float,  // Normalized 0.0 .. 1.0
    val bottom: Float  // Normalized 0.0 .. 1.0
) {
    val centerY: Float get() = (top + bottom) / 2f
    val centerX: Float get() = (left + right) / 2f
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)

    val isStatusBar: Boolean get() = top <= 0.12f && bottom <= 0.15f
    val isNavigationBar: Boolean get() = top >= 0.90f
}

data class OcrElement(
    val text: String,
    val bounds: BoundingBox? = null,
    val confidence: Float = 1.0f
)

data class OcrLine(
    val text: String,
    val bounds: BoundingBox? = null,
    val elements: List<OcrElement> = emptyList(),
    val confidence: Float = 1.0f
)

data class OcrBlock(
    val text: String,
    val bounds: BoundingBox? = null,
    val lines: List<OcrLine> = emptyList(),
    val confidence: Float = 1.0f
)

data class OcrDocument(
    val fullText: String,
    val blocks: List<OcrBlock> = emptyList(),
    val lines: List<OcrLine> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0
) {
    companion object {
        fun fromRawText(text: String): OcrDocument {
            val rawLines = text.lines()
            val totalLines = maxOf(rawLines.size, 1)
            val ocrLines = rawLines.mapIndexed { index, lineStr ->
                val topRatio = index.toFloat() / totalLines.toFloat()
                val bottomRatio = (index + 1).toFloat() / totalLines.toFloat()
                val bounds = BoundingBox(0f, topRatio, 1f, bottomRatio)
                val elements = lineStr.split("\\s+".toRegex())
                    .filter { it.isNotBlank() }
                    .map { OcrElement(it, bounds) }
                OcrLine(
                    text = lineStr,
                    bounds = bounds,
                    elements = elements
                )
            }
            return OcrDocument(
                fullText = text,
                blocks = listOf(OcrBlock(text = text, lines = ocrLines)),
                lines = ocrLines,
                imageWidth = 1080,
                imageHeight = 2400
            )
        }
    }
}

enum class EntityType(val displayName: String) {
    DATE("Date"),
    TIME("Time"),
    LOCATION("Location"),
    PERSON("Person"),
    PRICE("Price"),
    URL("Link"),
    PHONE("Phone"),
    EMAIL("Email"),
    PRODUCT("Product"),
    FLIGHT("Flight"),
    ORDER_ID("Order ID"),
    DEADLINE("Deadline"),
    NOTE("Note")
}

enum class ActionType(val displayName: String) {
    REMINDER("Add reminder"),
    CALENDAR("Add to calendar"),
    OPEN_URL("Open link"),
    CALL("Call"),
    NAVIGATE("Navigate"),
    SAVE_PRODUCT("Save product"),
    SAVE_RECEIPT("Save receipt"),
    COPY("Copy details")
}

enum class ScreenshotStatus {
    NEEDS_ATTENTION,
    ACTION_CREATED,
    SAVED,
    DISMISSED
}
