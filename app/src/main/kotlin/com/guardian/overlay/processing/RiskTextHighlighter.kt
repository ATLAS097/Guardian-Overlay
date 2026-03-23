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

        val matched = lines
            .filter { line ->
                val normalizedLine = TextNormalizer.normalize(line.text)
                riskKeywords.any { keyword -> normalizedLine.contains(keyword) }
            }
        if (matched.isEmpty()) return emptyList()

        val contextualRects = matched
            .map { line ->
                val block = line.blockBounds
                if (block != null) {
                    val expanded = Rect(block)
                    expanded.inset(-12, -10)
                    expanded
                } else {
                    val expanded = Rect(line.bounds)
                    expanded.inset(-8, -6)
                    expanded
                }
            }

        return mergeOverlappingRects(contextualRects)
            .take(maxBoxes)
    }

    private fun mergeOverlappingRects(rects: List<Rect>): List<Rect> {
        if (rects.isEmpty()) return emptyList()

        val pending = rects.map { Rect(it) }.toMutableList()
        val merged = mutableListOf<Rect>()

        while (pending.isNotEmpty()) {
            val current = pending.removeAt(0)
            var didMerge: Boolean
            do {
                didMerge = false
                val iterator = pending.iterator()
                while (iterator.hasNext()) {
                    val next = iterator.next()
                    if (isNearOrOverlapping(current, next)) {
                        current.union(next)
                        iterator.remove()
                        didMerge = true
                    }
                }
            } while (didMerge)
            merged.add(current)
        }

        return merged.sortedByDescending { it.width() * it.height() }
    }

    private fun isNearOrOverlapping(a: Rect, b: Rect, margin: Int = 16): Boolean {
        if (Rect.intersects(a, b)) return true
        val expandedA = Rect(a.left - margin, a.top - margin, a.right + margin, a.bottom + margin)
        return Rect.intersects(expandedA, b)
    }
}
