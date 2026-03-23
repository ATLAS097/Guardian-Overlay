package com.guardian.overlay.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
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
        extractScanDataFromImageUri(
            context = context,
            imageUri = imageUri,
            onSuccess = { data -> onSuccess(data.text) },
            onError = onError
        )
    }

    fun extractScanDataFromImageUri(
        context: Context,
        imageUri: Uri,
        onSuccess: (OcrScanData) -> Unit,
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
                val lines = result.textBlocks
                    .flatMap { block ->
                        val blockBox = block.boundingBox?.let { Rect(it) }
                        block.lines.mapNotNull { line ->
                            val lineBox = line.boundingBox ?: return@mapNotNull null
                            OcrLineBox(
                                text = line.text,
                                bounds = Rect(lineBox),
                                blockBounds = blockBox?.let { Rect(it) }
                            )
                        }
                    }
                onSuccess(OcrScanData(text = result.text, lines = lines))
            }
            .addOnFailureListener { ex ->
                onError(ex)
            }
    }

    fun extractScanDataFromBitmap(
        bitmap: Bitmap,
        onSuccess: (OcrScanData) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val lines = result.textBlocks
                    .flatMap { block ->
                        val blockBox = block.boundingBox?.let { Rect(it) }
                        block.lines.mapNotNull { line ->
                            val lineBox = line.boundingBox ?: return@mapNotNull null
                            OcrLineBox(
                                text = line.text,
                                bounds = Rect(lineBox),
                                blockBounds = blockBox?.let { Rect(it) }
                            )
                        }
                    }
                onSuccess(OcrScanData(text = result.text, lines = lines))
            }
            .addOnFailureListener { ex ->
                onError(ex)
            }
    }
}

data class OcrScanData(
    val text: String,
    val lines: List<OcrLineBox>
)

data class OcrLineBox(
    val text: String,
    val bounds: Rect,
    val blockBounds: Rect? = null
)
