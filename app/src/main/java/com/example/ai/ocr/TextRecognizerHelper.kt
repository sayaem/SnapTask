package com.example.ai.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object TextRecognizerHelper {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeText(context: Context, imageUri: Uri): String {
        return suspendCancellableCoroutine { continuation ->
            try {
                val inputImage = InputImage.fromFilePath(context, imageUri)
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
}
