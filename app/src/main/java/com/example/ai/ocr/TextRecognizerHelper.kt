package com.example.ai.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import com.example.domain.model.BoundingBox
import com.example.domain.model.OcrBlock
import com.example.domain.model.OcrDocument
import com.example.domain.model.OcrElement
import com.example.domain.model.OcrLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object TextRecognizerHelper {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeDocument(context: Context, imageUri: Uri): OcrDocument {
        return suspendCancellableCoroutine { continuation ->
            try {
                val inputImage = InputImage.fromFilePath(context, imageUri)
                val width = inputImage.width.coerceAtLeast(1)
                val height = inputImage.height.coerceAtLeast(1)

                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val doc = buildOcrDocument(visionText, width, height)
                        continuation.resume(doc)
                    }
                    .addOnFailureListener {
                        continuation.resume(OcrDocument("", emptyList(), emptyList(), width, height))
                    }
            } catch (e: Exception) {
                continuation.resume(OcrDocument("", emptyList(), emptyList(), 0, 0))
            }
        }
    }

    suspend fun recognizeText(context: Context, imageUri: Uri): String {
        return recognizeDocument(context, imageUri).fullText
    }

    suspend fun recognizeBitmap(bitmap: Bitmap): String {
        return suspendCancellableCoroutine { continuation ->
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        continuation.resume(visionText.text)
                    }
                    .addOnFailureListener {
                        continuation.resume("")
                    }
            } catch (e: Exception) {
                continuation.resume("")
            }
        }
    }

    private fun buildOcrDocument(visionText: Text, width: Int, height: Int): OcrDocument {
        val w = width.toFloat()
        val h = height.toFloat()

        fun toBoundingBox(rect: Rect?): BoundingBox? {
            if (rect == null) return null
            return BoundingBox(
                left = (rect.left.toFloat() / w).coerceIn(0f, 1f),
                top = (rect.top.toFloat() / h).coerceIn(0f, 1f),
                right = (rect.right.toFloat() / w).coerceIn(0f, 1f),
                bottom = (rect.bottom.toFloat() / h).coerceIn(0f, 1f)
            )
        }

        val allLines = mutableListOf<OcrLine>()

        val blocks = visionText.textBlocks.map { block ->
            val blockLines = block.lines.map { line ->
                val elements = line.elements.map { element ->
                    OcrElement(
                        text = element.text,
                        bounds = toBoundingBox(element.boundingBox),
                        confidence = element.confidence ?: 1.0f
                    )
                }
                val ocrLine = OcrLine(
                    text = line.text,
                    bounds = toBoundingBox(line.boundingBox),
                    elements = elements,
                    confidence = line.confidence ?: 1.0f
                )
                allLines.add(ocrLine)
                ocrLine
            }

            OcrBlock(
                text = block.text,
                bounds = toBoundingBox(block.boundingBox),
                lines = blockLines
            )
        }

        return OcrDocument(
            fullText = visionText.text,
            blocks = blocks,
            lines = allLines,
            imageWidth = width,
            imageHeight = height
        )
    }
}
