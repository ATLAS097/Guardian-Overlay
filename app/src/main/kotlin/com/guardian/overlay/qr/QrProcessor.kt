package com.guardian.overlay.qr

import android.content.Context
import android.media.Image
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class QrProcessor {
    fun scanFromImageUri(
        context: Context,
        imageUri: Uri,
        onSuccess: (List<String>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val image = try {
            InputImage.fromFilePath(context, imageUri)
        } catch (t: Throwable) {
            onError(t)
            return
        }

        val scanner = BarcodeScanning.getClient()
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val payloads = barcodes.mapNotNull { it.rawValue?.trim() }.filter { it.isNotEmpty() }
                onSuccess(payloads)
            }
            .addOnFailureListener { ex ->
                onError(ex)
            }
    }

    fun scanFromMediaImage(
        mediaImage: Image,
        rotationDegrees: Int,
        onSuccess: (List<String>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        val scanner = BarcodeScanning.getClient()
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val payloads = barcodes.mapNotNull { it.rawValue?.trim() }.filter { it.isNotEmpty() }
                onSuccess(payloads)
            }
            .addOnFailureListener { ex ->
                onError(ex)
            }
    }
}
