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
    OTHER("📄", "Other");

    companion object {
        fun fromString(value: String): Category {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: OTHER
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
