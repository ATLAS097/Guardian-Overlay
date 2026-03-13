package com.guardian.overlay.detection

import android.content.Context
import com.guardian.overlay.model.DetectionResult

class RuleBasedScamDetector(context: Context) : ScamDetector {
    private val delegate = FakeMiniTfliteDetector(context)

    override fun detect(text: String, source: String): DetectionResult {
        return delegate.detect(text, source)
    }
}
