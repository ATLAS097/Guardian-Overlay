package com.guardian.overlay.model

data class DetectionResult(
    val isScam: Boolean,
    val score: Float,
    val reasons: List<String>,
    val source: String,
    val snippet: String,
    val scamType: ScamType = ScamType.UNKNOWN,
    val sourceChannel: SourceChannel = SourceChannel.UNKNOWN
) {
    fun prettyScore(): String = "${(score * 100).toInt()}%"
}

enum class ScamType {
    PHISHING,
    IMPERSONATION,
    FINANCIAL_PRESSURE,
    INVESTMENT_FRAUD,
    ACCOUNT_TAKEOVER,
    UNKNOWN
}

enum class SourceChannel {
    SMS,
    EMAIL,
    CHAT,
    SOCIAL,
    WEB,
    UNKNOWN
}
