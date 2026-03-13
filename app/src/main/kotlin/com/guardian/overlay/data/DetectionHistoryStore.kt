package com.guardian.overlay.data

import android.content.Context
import com.guardian.overlay.model.DetectionHistoryEntry
import com.guardian.overlay.model.DetectionResult
import org.json.JSONArray
import org.json.JSONObject

class DetectionHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveResult(result: DetectionResult) {
        val current = loadEntries().toMutableList()
        val newEntry = DetectionHistoryEntry(
            timestamp = System.currentTimeMillis(),
            source = result.source,
            isScam = result.isScam,
            score = result.score,
            reasons = result.reasons,
            snippet = result.snippet
        )

        current.add(0, newEntry)
        val trimmed = current.take(MAX_ENTRIES)

        val json = JSONArray()
        trimmed.forEach { entry ->
            json.put(
                JSONObject().apply {
                    put("timestamp", entry.timestamp)
                    put("source", entry.source)
                    put("isScam", entry.isScam)
                    put("score", entry.score)
                    put("snippet", entry.snippet)
                    put("reasons", JSONArray(entry.reasons))
                }
            )
        }

        prefs.edit().putString(KEY_HISTORY, json.toString()).apply()
    }

    fun loadEntries(): List<DetectionHistoryEntry> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val arr = try {
            JSONArray(raw)
        } catch (_: Throwable) {
            return emptyList()
        }

        val out = mutableListOf<DetectionHistoryEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val reasonArr = obj.optJSONArray("reasons") ?: JSONArray()
            val reasons = mutableListOf<String>()
            for (j in 0 until reasonArr.length()) {
                reasonArr.optString(j)?.let { reasons.add(it) }
            }

            out.add(
                DetectionHistoryEntry(
                    timestamp = obj.optLong("timestamp", 0L),
                    source = obj.optString("source", "unknown"),
                    isScam = obj.optBoolean("isScam", false),
                    score = obj.optDouble("score", 0.0).toFloat(),
                    reasons = reasons,
                    snippet = obj.optString("snippet", "")
                )
            )
        }
        return out
    }

    companion object {
        private const val PREF_NAME = "guardian_history"
        private const val KEY_HISTORY = "entries"
        private const val MAX_ENTRIES = 20
    }
}
