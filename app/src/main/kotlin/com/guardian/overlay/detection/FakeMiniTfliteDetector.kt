package com.guardian.overlay.detection

import android.content.Context
import com.guardian.overlay.model.DetectionResult
import com.guardian.overlay.model.PhraseRule
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

        return DetectionResult(
            isScam = output.scamProbability >= threshold,
            score = output.scamProbability,
            reasons = output.reasons.ifEmpty { listOf("No strong scam signals detected") }.take(4),
            source = source,
            snippet = normalized.take(220)
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
}
