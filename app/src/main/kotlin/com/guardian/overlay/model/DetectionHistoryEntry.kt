package com.guardian.overlay.model

data class DetectionHistoryEntry(
    val timestamp: Long,
    val source: String,
    val isScam: Boolean,
    val score: Float,
    val reasons: List<String>,
    val snippet: String
)
