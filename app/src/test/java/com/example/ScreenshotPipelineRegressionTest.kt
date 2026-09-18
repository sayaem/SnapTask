package com.example

import com.example.ai.classifier.ContextClassifier
import com.example.ai.classifier.ScreenshotClassifier
import com.example.ai.extractor.EntityExtractor
import com.example.ai.validator.RelevanceValidator
import com.example.domain.model.BoundingBox
import com.example.domain.model.Category
import com.example.domain.model.EntityType
import com.example.domain.model.OcrBlock
import com.example.domain.model.OcrDocument
import com.example.domain.model.OcrElement
import com.example.domain.model.OcrLine
import com.example.domain.model.ScreenshotContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScreenshotPipelineRegressionTest {

    private fun buildDocument(linesWithBounds: List<Pair<String, BoundingBox>>): OcrDocument {
        val ocrLines = linesWithBounds.map { (text, bounds) ->
            OcrLine(
                text = text,
                bounds = bounds,
                elements = text.split(" ").map { word ->
                    OcrElement(text = word, bounds = bounds)
                }
            )
        }
        val ocrBlocks = listOf(
            OcrBlock(
                text = linesWithBounds.joinToString("\n") { it.first },
                bounds = BoundingBox(0f, 0f, 1f, 1f),
                lines = ocrLines
            )
        )
        return OcrDocument(
            fullText = linesWithBounds.joinToString("\n") { it.first },
            blocks = ocrBlocks,
            lines = ocrLines
        )
    }

    /**
     * Scenario 1: Android home-screen screenshot
     * Expected: UNACTIONABLE or No Action
     * Forbidden: Event detected, Route detected, Address detected
     */
    @Test
    fun `scenario 1 - android home-screen screenshot is classified as UNACTIONABLE`() {
        val homeDoc = buildDocument(
            listOf(
                "6:33" to BoundingBox(0.04f, 0.02f, 0.12f, 0.05f),
                "5G 98%" to BoundingBox(0.85f, 0.02f, 0.96f, 0.05f),
                "Google Search" to BoundingBox(0.1f, 0.18f, 0.9f, 0.24f),
                "Calendar" to BoundingBox(0.1f, 0.35f, 0.25f, 0.42f),
                "Photos" to BoundingBox(0.35f, 0.35f, 0.50f, 0.42f),
                "Settings" to BoundingBox(0.6f, 0.35f, 0.75f, 0.42f),
                "Camera" to BoundingBox(0.85f, 0.35f, 0.95f, 0.42f),
                "Phone" to BoundingBox(0.1f, 0.85f, 0.25f, 0.92f),
                "Messages" to BoundingBox(0.35f, 0.85f, 0.50f, 0.92f),
                "Chrome" to BoundingBox(0.6f, 0.85f, 0.75f, 0.92f),
                "Play Store" to BoundingBox(0.85f, 0.85f, 0.95f, 0.92f)
            )
        )

        val contextResult = ContextClassifier.classifyContext(homeDoc)
        assertEquals(ScreenshotContext.HOME_SCREEN, contextResult.context)

        val candidates = EntityExtractor.extractCandidates(homeDoc)
        val validation = RelevanceValidator.validate(candidates, contextResult, homeDoc)

        assertEquals(Category.UNACTIONABLE, validation.category)
        assertFalse("Home screen should not be actionable", validation.isActionable)
        assertNotEquals(Category.EVENT, validation.category)
        assertNotEquals(Category.LOCATION, validation.category)
        assertNotEquals(Category.TRAVEL, validation.category)
        assertTrue(validation.validatedEntities.isEmpty())
    }

    /**
     * Scenario 2: Status bar clock
     * Expected: Ignored, not extracted as appointment time
     */
    @Test
    fun `scenario 2 - status bar clock is rejected and not treated as appointment time`() {
        val docWithStatusBarTime = buildDocument(
            listOf(
                "6:33" to BoundingBox(0.04f, 0.015f, 0.12f, 0.045f), // In status bar
                "Meeting notes about product roadmap" to BoundingBox(0.05f, 0.30f, 0.95f, 0.35f),
                "Discuss quarterly targets and team priorities" to BoundingBox(0.05f, 0.38f, 0.95f, 0.45f)
            )
        )

        val contextResult = ContextClassifier.classifyContext(docWithStatusBarTime)
        val candidates = EntityExtractor.extractCandidates(docWithStatusBarTime)
        val validation = RelevanceValidator.validate(candidates, contextResult, docWithStatusBarTime)

        val hasStatusBarTime = validation.validatedEntities.any { it.type == EntityType.TIME && it.value == "6:33" }
        assertFalse("Status bar clock must be rejected as an appointment time", hasStatusBarTime)
    }

    /**
     * Scenario 3: Status bar battery percentage
     * Expected: Ignored, not extracted as price or number
     */
    @Test
    fun `scenario 3 - status bar battery percentage is ignored and not extracted as price`() {
        val docWithBattery = buildDocument(
            listOf(
                "85%" to BoundingBox(0.88f, 0.02f, 0.96f, 0.05f),
                "Article about space exploration" to BoundingBox(0.05f, 0.25f, 0.95f, 0.32f),
                "NASA announced new telescope observations today" to BoundingBox(0.05f, 0.35f, 0.95f, 0.42f)
            )
        )

        val contextResult = ContextClassifier.classifyContext(docWithBattery)
        val candidates = EntityExtractor.extractCandidates(docWithBattery)
        val validation = RelevanceValidator.validate(candidates, contextResult, docWithBattery)

        val hasBatteryPrice = validation.validatedEntities.any { it.type == EntityType.PRICE }
        assertFalse("Battery percentage must not be extracted as price", hasBatteryPrice)
    }

    /**
     * Scenario 4: Flight booking confirmation
     * Expected: Valid flight extracted, airline, dates, route
     */
    @Test
    fun `scenario 4 - flight booking confirmation extracts flight, airline, and route`() {
        val flightDoc = buildDocument(
            listOf(
                "6:33" to BoundingBox(0.04f, 0.02f, 0.12f, 0.05f), // Status bar
                "Biman Bangladesh Airlines" to BoundingBox(0.05f, 0.15f, 0.70f, 0.20f),
                "Flight BG147" to BoundingBox(0.05f, 0.22f, 0.40f, 0.27f),
                "Dhaka → Dubai" to BoundingBox(0.05f, 0.30f, 0.50f, 0.35f),
                "Departure: October 20 • 2:15 AM" to BoundingBox(0.05f, 0.38f, 0.80f, 0.43f),
                "Terminal 3 Gate 12" to BoundingBox(0.05f, 0.45f, 0.60f, 0.50f)
            )
        )

        val contextResult = ContextClassifier.classifyContext(flightDoc)
        assertEquals(ScreenshotContext.TRAVEL_TICKET, contextResult.context)

        val candidates = EntityExtractor.extractCandidates(flightDoc)
        val validation = RelevanceValidator.validate(candidates, contextResult, flightDoc)

        assertEquals(Category.TRAVEL, validation.category)
        assertTrue(validation.isActionable)
        assertTrue("Flight number must be extracted", validation.validatedEntities.any { it.type == EntityType.FLIGHT && it.value == "BG147" })
        assertTrue("Departure date must be extracted", validation.validatedEntities.any { it.type == EntityType.DATE })
        assertTrue("Flight time must be extracted", validation.validatedEntities.any { it.type == EntityType.TIME && it.value == "2:15 AM" })
    }

    /**
     * Scenario 5: Doctor appointment SMS
     * Expected: Valid appointment date and time extracted
     */
    @Test
    fun `scenario 5 - doctor appointment SMS extracts appointment date and time`() {
        val appointmentDoc = buildDocument(
            listOf(
                "Messages" to BoundingBox(0.05f, 0.08f, 0.30f, 0.12f),
                "Your appointment with Dr. Sarah Smith is scheduled for October 14 at 3:30 PM." to BoundingBox(0.05f, 0.25f, 0.95f, 0.35f),
                "Please arrive 10 minutes early at City Dental Clinic." to BoundingBox(0.05f, 0.38f, 0.95f, 0.45f)
            )
        )

        val contextResult = ContextClassifier.classifyContext(appointmentDoc)
        val candidates = EntityExtractor.extractCandidates(appointmentDoc)
        val validation = RelevanceValidator.validate(candidates, contextResult, appointmentDoc)

        assertEquals(Category.EVENT, validation.category)
        assertTrue(validation.isActionable)
        assertTrue("Date must be extracted", validation.validatedEntities.any { it.type == EntityType.DATE && it.value.contains("October 14") })
        assertTrue("Time must be extracted", validation.validatedEntities.any { it.type == EntityType.TIME && it.value.contains("3:30 PM") })
    }

    /**
     * Scenario 6: Receipt screenshot
     * Expected: Valid store, items, total extracted
     */
    @Test
    fun `scenario 6 - receipt screenshot extracts total price and merchant information`() {
        val receiptDoc = buildDocument(
            listOf(
                "Target Store #1042" to BoundingBox(0.1f, 0.15f, 0.60f, 0.20f),
                "Subtotal: $24.99" to BoundingBox(0.1f, 0.40f, 0.50f, 0.45f),
                "Tax: $2.01" to BoundingBox(0.1f, 0.46f, 0.40f, 0.50f),
                "Total: $27.00" to BoundingBox(0.1f, 0.52f, 0.50f, 0.57f),
                "Payment Method: Visa •••• 4242" to BoundingBox(0.1f, 0.60f, 0.80f, 0.65f),
                "Receipt #893412" to BoundingBox(0.1f, 0.68f, 0.50f, 0.73f)
            )
        )

        val contextResult = ContextClassifier.classifyContext(receiptDoc)
        assertEquals(ScreenshotContext.RECEIPT_INVOICE, contextResult.context)

        val candidates = EntityExtractor.extractCandidates(receiptDoc)
        val validation = RelevanceValidator.validate(candidates, contextResult, receiptDoc)

        assertEquals(Category.RECEIPT, validation.category)
        assertTrue(validation.isActionable)
        assertTrue("Total price must be extracted", validation.validatedEntities.any { it.type == EntityType.PRICE && it.value.contains("27.00") })
    }

    /**
     * Scenario 7: News article screenshot containing a publication date and time
     * Expected: Publication timestamp ignored as calendar event
     */
    @Test
    fun `scenario 7 - news article publication timestamp is ignored as a calendar event`() {
        val newsDoc = buildDocument(
            listOf(
                "Tech News Today" to BoundingBox(0.05f, 0.08f, 0.50f, 0.12f),
                "Breakthrough in Artificial Intelligence Announced" to BoundingBox(0.05f, 0.18f, 0.95f, 0.28f),
                "Updated October 14 at 6:33 PM • 4 min read" to BoundingBox(0.05f, 0.30f, 0.80f, 0.35f),
                "Researchers released an open model capable of reasoning on complex tasks." to BoundingBox(0.05f, 0.38f, 0.95f, 0.50f)
            )
        )

        val contextResult = ContextClassifier.classifyContext(newsDoc)
        assertEquals(ScreenshotContext.WEBPAGE, contextResult.context)

        val candidates = EntityExtractor.extractCandidates(newsDoc)
        val validation = RelevanceValidator.validate(candidates, contextResult, newsDoc)

        assertNotEquals(
            "Article publication timestamp must not be classified as a calendar event",
            Category.EVENT,
            validation.category
        )
        val hasEventTime = validation.validatedEntities.any { it.type == EntityType.TIME }
        assertFalse("News publication time should be rejected", hasEventTime)
    }

    /**
     * Scenario 8: Settings screen screenshot
     * Expected: UNACTIONABLE
     */
    @Test
    fun `scenario 8 - settings screen screenshot is classified as UNACTIONABLE`() {
        val settingsDoc = buildDocument(
            listOf(
                "Settings" to BoundingBox(0.05f, 0.08f, 0.35f, 0.13f),
                "Network & internet" to BoundingBox(0.05f, 0.18f, 0.60f, 0.23f),
                "Connected devices" to BoundingBox(0.05f, 0.26f, 0.60f, 0.31f),
                "Apps & notifications" to BoundingBox(0.05f, 0.34f, 0.65f, 0.39f),
                "Battery" to BoundingBox(0.05f, 0.42f, 0.30f, 0.47f),
                "Display" to BoundingBox(0.05f, 0.50f, 0.30f, 0.55f),
                "Sound" to BoundingBox(0.05f, 0.58f, 0.30f, 0.63f),
                "Storage" to BoundingBox(0.05f, 0.66f, 0.30f, 0.71f),
                "Privacy" to BoundingBox(0.05f, 0.74f, 0.30f, 0.79f)
            )
        )

        val contextResult = ContextClassifier.classifyContext(settingsDoc)
        assertEquals(ScreenshotContext.SETTINGS, contextResult.context)

        val candidates = EntityExtractor.extractCandidates(settingsDoc)
        val validation = RelevanceValidator.validate(candidates, contextResult, settingsDoc)

        assertEquals(Category.UNACTIONABLE, validation.category)
        assertFalse("Settings screen must not be actionable", validation.isActionable)
        assertTrue(validation.validatedEntities.isEmpty())
    }
}
