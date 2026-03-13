package com.guardian.overlay.model

data class DetectionResult(
    val isScam: Boolean,
    val score: Float,
    val reasons: List<String>,
    val source: String,
    val snippet: String
) {
    fun prettyScore(): String = "${(score * 100).toInt()}%"
}
