package com.guardian.overlay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.guardian.overlay.data.DetectionHistoryStore
import com.guardian.overlay.databinding.ActivityMainBinding
import com.guardian.overlay.detection.DetectorProvider
import com.guardian.overlay.ocr.OcrProcessor

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val detector by lazy { DetectorProvider.get(this) }
    private val ocrProcessor = OcrProcessor()
    private lateinit var historyStore: DetectionHistoryStore

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) {
            binding.snippetValue.text = "No screenshot selected."
            return@registerForActivityResult
        }

        binding.snippetValue.text = "Running OCR..."
        ocrProcessor.extractTextFromImageUri(
            context = this,
            imageUri = uri,
            onSuccess = { text ->
                val result = detector.detect(text, "screenshot_ocr")
                historyStore.saveResult(result)
                renderResult(result.source, result.isScam, result.prettyScore(), result.reasons, result.snippet)
            },
            onError = { ex ->
                binding.snippetValue.text = "OCR failed: ${ex.message}"
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        historyStore = DetectionHistoryStore(this)

        binding.openAccessibilityBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.pickScreenshotBtn.setOnClickListener {
            pickImage.launch("image/*")
        }

        binding.openHistoryBtn.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        renderResult(
            source = "-",
            isScam = false,
            score = "-",
            reasons = listOf("No scan yet."),
            snippet = "Ready. Enable accessibility and test screenshot OCR."
        )
    }

    private fun renderResult(
        source: String,
        isScam: Boolean,
        score: String,
        reasons: List<String>,
        snippet: String
    ) {
        binding.sourceValue.text = source
        binding.verdictValue.text = if (isScam) "SCAM" else "POTENTIAL / LOW RISK"
        binding.scoreValue.text = score
        binding.reasonsValue.text = reasons.joinToString(separator = "\n- ", prefix = "- ")
        binding.snippetValue.text = snippet
    }
}
