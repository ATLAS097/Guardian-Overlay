package com.guardian.overlay.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class OcrProcessor {
    fun extractTextFromImageUri(
        context: Context,
        imageUri: Uri,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val image = try {
            InputImage.fromFilePath(context, imageUri)
        } catch (t: Throwable) {
            onError(t)
            return
        }

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                onSuccess(result.text ?: "")
            }
            .addOnFailureListener { ex ->
                onError(ex)
            }
    }
}
