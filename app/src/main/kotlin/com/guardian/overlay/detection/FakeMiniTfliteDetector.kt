package com.guardian.overlay.detection

import android.content.Context
import com.guardian.overlay.model.DetectionResult
import com.guardian.overlay.model.PhraseRule
import com.guardian.overlay.model.ScamType
import com.guardian.overlay.model.SourceChannel
import com.guardian.overlay.processing.TextNormalizer

/**
 * Prototype detector that mimics a tiny TFLite pipeline shape while remaining rule-based.
 * Later, you can replace runFakeInference() with a real Interpreter.run(...) call.
 */
class FakeMiniTfliteDetector(context: Context) : ScamDetector {
    private val phraseRules: List<PhraseRule> = PhrasePackLoader(context.applicationContext).loadRules()
    private val threshold = 0.20f

    override fun detect(text: String, source: String): DetectionResult {
        val normalized = TextNormalizer.normalize(text)
        val inputVector = buildInputVector(normalized)
        val output = runFakeInference(inputVector, normalized)
        val scamType = inferScamType(output.reasons, normalized)
        val sourceChannel = inferSourceChannel(source)
        val enrichedReasons = buildList {
            add("Likely type: ${formatScamType(scamType)}")
            add("Channel: ${formatSourceChannel(sourceChannel)}")
            addAll(output.reasons)
        }

        return DetectionResult(
            isScam = output.scamProbability >= threshold,
            score = output.scamProbability,
            reasons = enrichedReasons
                .ifEmpty { listOf("No strong scam signals detected") }
                .distinct()
                .take(6),
            source = source,
            snippet = normalized.take(220),
            scamType = scamType,
            sourceChannel = sourceChannel
        )
    }

    private fun buildInputVector(text: String): IntArray {
        val tokens = text.split(" ").filter { it.isNotBlank() }
        val maxLen = 64
        val vector = IntArray(maxLen)
        for (i in 0 until minOf(tokens.size, maxLen)) {
            vector[i] = (tokens[i].hashCode() and 0x7fffffff) % 10000
        }
        return vector
    }

    private fun runFakeInference(inputVector: IntArray, normalizedText: String): FakeInferenceOutput {
        var scamProb = 0.05f + (inputVector.count { it > 0 } * 0.0004f)
        val matchedReasons = mutableListOf<String>()

        for (rule in phraseRules) {
            if (rule.keywords.any { normalizedText.contains(it) }) {
                scamProb += rule.weight
                matchedReasons.add(rule.reason)
            }
        }

        return FakeInferenceOutput(
            scamProbability = scamProb.coerceIn(0.01f, 0.99f),
            reasons = matchedReasons.distinct()
        )
    }

    private data class FakeInferenceOutput(
        val scamProbability: Float,
        val reasons: List<String>
    )

    private fun inferScamType(reasons: List<String>, normalizedText: String): ScamType {
        val reasonText = reasons.joinToString(separator = " ").lowercase()
        val combined = "$reasonText $normalizedText"

        return when {
            combined.contains("guaranteed returns") ||
                combined.contains("risk free profit") ||
                combined.contains("double your money") -> ScamType.INVESTMENT_FRAUD

            combined.contains("otp") ||
                combined.contains("verify account") ||
                combined.contains("account suspended") ||
                combined.contains("log in") -> ScamType.ACCOUNT_TAKEOVER

            combined.contains("bank") ||
                combined.contains("police") ||
                combined.contains("government") ||
                combined.contains("authority impersonation") -> ScamType.IMPERSONATION

            combined.contains("urgent") ||
                combined.contains("act now") ||
                combined.contains("fine") ||
                combined.contains("transfer sekarang") ||
                combined.contains("hantar duit") ||
                combined.contains("padala") -> ScamType.FINANCIAL_PRESSURE

            combined.contains("http://") ||
                combined.contains("https://") ||
                combined.contains("bit.ly") ||
                combined.contains("tinyurl") ||
                combined.contains("click link") ||
                combined.contains("verify here") -> ScamType.PHISHING

            else -> ScamType.UNKNOWN
        }
    }

    private fun inferSourceChannel(source: String): SourceChannel {
        val lower = source.lowercase()
        return when {
            lower.contains("sms") ||
                lower.contains("message") ||
                lower.contains("mms") -> SourceChannel.SMS

            lower.contains("mail") ||
                lower.contains("gmail") ||
                lower.contains("outlook") ||
                lower.contains("email") -> SourceChannel.EMAIL

            lower.contains("whatsapp") ||
                lower.contains("telegram") ||
                lower.contains("messenger") ||
                lower.contains("wechat") ||
                lower.contains("line") -> SourceChannel.CHAT

            lower.contains("facebook") ||
                lower.contains("instagram") ||
                lower.contains("x.") ||
                lower.contains("twitter") ||
                lower.contains("tiktok") -> SourceChannel.SOCIAL

            lower.contains("chrome") ||
                lower.contains("browser") ||
                lower.contains("webview") -> SourceChannel.WEB

            else -> SourceChannel.UNKNOWN
        }
    }

    private fun formatScamType(type: ScamType): String {
        return when (type) {
            ScamType.PHISHING -> "Phishing"
            ScamType.IMPERSONATION -> "Impersonation"
            ScamType.FINANCIAL_PRESSURE -> "Financial Pressure"
            ScamType.INVESTMENT_FRAUD -> "Investment Fraud"
            ScamType.ACCOUNT_TAKEOVER -> "Account Takeover"
            ScamType.UNKNOWN -> "Unclear"
        }
    }

    private fun formatSourceChannel(channel: SourceChannel): String {
        return when (channel) {
            SourceChannel.SMS -> "SMS"
            SourceChannel.EMAIL -> "Email"
            SourceChannel.CHAT -> "Chat"
            SourceChannel.SOCIAL -> "Social"
            SourceChannel.WEB -> "Web"
            SourceChannel.UNKNOWN -> "Unknown"
        }
    }
}
