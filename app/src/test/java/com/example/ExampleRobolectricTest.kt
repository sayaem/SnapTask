package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.classifier.ScreenshotClassifier
import com.example.ai.extractor.EntityExtractor
import com.example.domain.model.Category
import com.example.domain.model.EntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SnapTask", appName)
    }

    @Test
    fun `extract flight information correctly`() {
        val rawText = "Biman Bangladesh Airlines\nFlight BG147\nDhaka → Dubai\nDeparture: September 25 • 2:15 AM"
        val entities = EntityExtractor.extractAll(rawText)
        val classification = ScreenshotClassifier.classify(rawText, entities)

        assertEquals(Category.TRAVEL, classification.category)
        assertTrue(entities.any { it.type == EntityType.FLIGHT && it.value == "BG147" })
        assertTrue(entities.any { it.type == EntityType.LOCATION })
    }

    @Test
    fun `extract doctor appointment correctly`() {
        val rawText = "Your appointment is on October 14 at 3:30 PM with Dr. Sarah Smith"
        val entities = EntityExtractor.extractAll(rawText)
        val classification = ScreenshotClassifier.classify(rawText, entities)

        assertEquals(Category.EVENT, classification.category)
        assertTrue(entities.any { it.type == EntityType.DATE && it.value.contains("October 14") })
        assertTrue(entities.any { it.type == EntityType.TIME && it.value.contains("3:30 PM") })
    }

    @Test
    fun `extract receipt correctly`() {
        val rawText = "Apple Store\nAirPods Pro\nTotal: $249.00 USD\nPurchase Date: September 12"
        val entities = EntityExtractor.extractAll(rawText)
        val classification = ScreenshotClassifier.classify(rawText, entities)

        assertEquals(Category.RECEIPT, classification.category)
        assertTrue(entities.any { it.type == EntityType.PRICE && it.value.contains("249") })
    }
}

