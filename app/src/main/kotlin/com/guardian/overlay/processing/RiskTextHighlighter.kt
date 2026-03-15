package com.guardian.overlay.processing

import android.graphics.Rect
import com.guardian.overlay.model.PhraseRule
import com.guardian.overlay.ocr.OcrLineBox

object RiskTextHighlighter {
    fun buildKeywordSet(rules: List<PhraseRule>): List<String> {
        return rules
            .flatMap { it.keywords }
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedByDescending { it.length }
    }

    fun findSuspiciousBoxes(lines: List<OcrLineBox>, riskKeywords: List<String>, maxBoxes: Int = 24): List<Rect> {
        if (lines.isEmpty() || riskKeywords.isEmpty()) return emptyList()

        return lines
            .filter { line ->
                val normalizedLine = TextNormalizer.normalize(line.text)
                riskKeywords.any { keyword -> normalizedLine.contains(keyword) }
            }
            .map { Rect(it.bounds) }
            .take(maxBoxes)
    }
}
