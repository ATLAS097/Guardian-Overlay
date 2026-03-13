package com.guardian.overlay.detection

import com.guardian.overlay.model.DetectionResult

interface ScamDetector {
    fun detect(text: String, source: String): DetectionResult
}
