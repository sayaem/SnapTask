package com.example

import com.example.ai.classifier.ContextClassificationResult
import com.example.ai.validator.AutomaticReminderEvaluator
import com.example.ai.validator.RelevanceValidationResult
import com.example.ai.validator.ValidatedEntity
import com.example.domain.model.BoundingBox
import com.example.domain.model.Category
import com.example.domain.model.EntityType
import com.example.domain.model.OcrBlock
import com.example.domain.model.OcrDocument
import com.example.domain.model.OcrElement
import com.example.domain.model.OcrLine
import com.example.domain.model.ScreenshotContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutomaticReminderEvaluatorTest {

    private fun createOcrDoc(text: String): OcrDocument {
        val line = OcrLine(
            text = text,
            bounds = BoundingBox(0f, 0f, 1f, 1f),
            elements = text.split(" ").map { OcrElement(it, BoundingBox(0f, 0f, 1f, 1f)) }
        )
        return OcrDocument(
            fullText = text,
            blocks = listOf(OcrBlock(text, BoundingBox(0f, 0f, 1f, 1f), listOf(line))),
            lines = listOf(line)
        )
    }

    @Test
    fun `evaluator rejects low confidence screenshots`() {
        val doc = createOcrDoc("Doctor Appointment on 2026-11-20 at 14:00")
        val contextResult = ContextClassificationResult(
            context = ScreenshotContext.FORM_APPOINTMENT,
            confidence = 0.60f,
            hasStatusBar = false,
            hasNavigationBar = false,
            isActionableCandidate = true
        )
        val validationResult = RelevanceValidationResult(
            category = Category.EVENT,
            title = "Doctor Appointment",
            isActionable = true,
            confidence = 0.70f, // Below 0.85 threshold
            validatedEntities = listOf(
                ValidatedEntity(EntityType.DATE, "Date", "2026-11-20", 0.9f),
                ValidatedEntity(EntityType.TIME, "Time", "14:00", 0.9f)
            ),
            debugLogs = emptyList()
        )

        val decision = AutomaticReminderEvaluator.evaluate(
            validationResult = validationResult,
            ocrDocument = doc,
            contextResult = contextResult
        )

        assertFalse("Evaluator must reject when validation confidence is below 0.85", decision.isEligible)
    }

    @Test
    fun `evaluator rejects ambiguous or non-actionable validation`() {
        val doc = createOcrDoc("Random note without date")
        val contextResult = ContextClassificationResult(
            context = ScreenshotContext.UNACTIONABLE,
            confidence = 0.90f,
            hasStatusBar = false,
            hasNavigationBar = false,
            isActionableCandidate = false
        )
        val validationResult = RelevanceValidationResult(
            category = Category.UNACTIONABLE,
            title = "Random Note",
            isActionable = false,
            confidence = 0.95f,
            validatedEntities = emptyList(),
            debugLogs = emptyList()
        )

        val decision = AutomaticReminderEvaluator.evaluate(
            validationResult = validationResult,
            ocrDocument = doc,
            contextResult = contextResult
        )

        assertFalse("Evaluator must reject non-actionable screenshots", decision.isEligible)
    }

    @Test
    fun `evaluator rejects receipts without explicit due date or return deadline`() {
        val doc = createOcrDoc("Target Store Total $45.20 Paid on 2026-09-12 Cashier 4")
        val contextResult = ContextClassificationResult(
            context = ScreenshotContext.RECEIPT_INVOICE,
            confidence = 0.92f,
            hasStatusBar = false,
            hasNavigationBar = false,
            isActionableCandidate = true
        )
        val validationResult = RelevanceValidationResult(
            category = Category.RECEIPT,
            title = "Target Store",
            isActionable = true,
            confidence = 0.90f,
            validatedEntities = listOf(
                ValidatedEntity(EntityType.DATE, "Date", "2026-09-12", 0.9f),
                ValidatedEntity(EntityType.PRICE, "Price", "$45.20", 0.95f)
            ),
            debugLogs = emptyList()
        )

        val decision = AutomaticReminderEvaluator.evaluate(
            validationResult = validationResult,
            ocrDocument = doc,
            contextResult = contextResult
        )

        assertFalse("Receipts without explicit due date or return by keywords must be rejected", decision.isEligible)
    }

    @Test
    fun `evaluator accepts confident future deadline with explicit due date`() {
        val doc = createOcrDoc("Project Submission Due Date: 2026-12-15 17:00 Final Assignment")
        val contextResult = ContextClassificationResult(
            context = ScreenshotContext.STUDY_DOCUMENT,
            confidence = 0.95f,
            hasStatusBar = false,
            hasNavigationBar = false,
            isActionableCandidate = true
        )
        val validationResult = RelevanceValidationResult(
            category = Category.STUDY,
            title = "Project Submission",
            isActionable = true,
            confidence = 0.92f,
            validatedEntities = listOf(
                ValidatedEntity(EntityType.DATE, "Due Date", "2026-12-15", 0.95f),
                ValidatedEntity(EntityType.TIME, "Time", "17:00", 0.95f)
            ),
            debugLogs = emptyList()
        )

        val decision = AutomaticReminderEvaluator.evaluate(
            validationResult = validationResult,
            ocrDocument = doc,
            contextResult = contextResult
        )

        assertTrue("Confident academic deadline with due date must be accepted", decision.isEligible)
        assertTrue(decision.eventTimestamp > System.currentTimeMillis())
        assertTrue(decision.reminderTimestamp < decision.eventTimestamp)
        assertTrue(decision.fingerprint.isNotEmpty())
    }

    @Test
    fun `evaluator accepts flight itinerary with high confidence and departure time`() {
        val doc = createOcrDoc("Flight UA 245 Departure 2026-11-18 08:30 Gate B12 Terminal 2")
        val contextResult = ContextClassificationResult(
            context = ScreenshotContext.TRAVEL_TICKET,
            confidence = 0.96f,
            hasStatusBar = false,
            hasNavigationBar = false,
            isActionableCandidate = true
        )
        val validationResult = RelevanceValidationResult(
            category = Category.TRAVEL,
            title = "Flight UA 245",
            isActionable = true,
            confidence = 0.95f,
            validatedEntities = listOf(
                ValidatedEntity(EntityType.FLIGHT, "Flight", "UA 245", 0.98f),
                ValidatedEntity(EntityType.DATE, "Date", "2026-11-18", 0.95f),
                ValidatedEntity(EntityType.TIME, "Time", "08:30", 0.95f)
            ),
            debugLogs = emptyList()
        )

        val decision = AutomaticReminderEvaluator.evaluate(
            validationResult = validationResult,
            ocrDocument = doc,
            contextResult = contextResult
        )

        assertTrue("High confidence flight with departure date/time must be eligible", decision.isEligible)
        assertTrue(decision.eventTimestamp > System.currentTimeMillis())
        // Lead time for travel >24h ahead is 180 minutes (3 hours)
        assertTrue(decision.leadTimeMinutes == 180)
    }
}
