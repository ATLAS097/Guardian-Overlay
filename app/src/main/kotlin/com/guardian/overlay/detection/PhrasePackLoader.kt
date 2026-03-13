package com.guardian.overlay.detection

import android.content.Context
import com.guardian.overlay.model.PhraseRule
import org.json.JSONArray
import org.json.JSONObject

class PhrasePackLoader(private val context: Context) {
    fun loadRules(): List<PhraseRule> {
        val raw = context.assets.open("risk_phrases.json").bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        val rules = mutableListOf<PhraseRule>()

        val languages = root.optJSONArray("languages") ?: JSONArray()
        for (i in 0 until languages.length()) {
            val langObj = languages.optJSONObject(i) ?: continue
            val langRules = langObj.optJSONArray("rules") ?: JSONArray()
            parseRules(langRules, rules)
        }

        val globalRules = root.optJSONArray("global_rules") ?: JSONArray()
        parseRules(globalRules, rules)

        return rules
    }

    private fun parseRules(array: JSONArray, out: MutableList<PhraseRule>) {
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val reason = obj.optString("reason", "Unknown reason")
            val weight = obj.optDouble("weight", 0.1).toFloat()
            val kwArray = obj.optJSONArray("keywords") ?: JSONArray()
            val keywords = mutableListOf<String>()
            for (j in 0 until kwArray.length()) {
                val kw = kwArray.optString(j).trim()
                if (kw.isNotEmpty()) keywords.add(kw.lowercase())
            }
            if (keywords.isNotEmpty()) {
                out.add(PhraseRule(reason = reason, weight = weight, keywords = keywords))
            }
        }
    }
}
