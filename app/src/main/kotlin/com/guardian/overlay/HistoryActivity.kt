package com.guardian.overlay

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.guardian.overlay.data.DetectionHistoryStore
import com.guardian.overlay.databinding.ActivityHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding
    private lateinit var store: DetectionHistoryStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = DetectionHistoryStore(this)
        val entries = store.loadEntries()

        if (entries.isEmpty()) {
            binding.historyText.text = "No detections yet. Run a scan first."
            return
        }

        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        binding.historyText.text = buildString {
            entries.forEachIndexed { index, e ->
                appendLine("#${index + 1}  ${formatter.format(Date(e.timestamp))}")
                appendLine("Source: ${e.source}")
                appendLine("Verdict: ${if (e.isScam) "SCAM" else "NOT SCAM"}")
                appendLine("Score: ${(e.score * 100).toInt()}%")
                appendLine("Reasons: ${e.reasons.joinToString()}")
                appendLine("Snippet: ${e.snippet}")
                appendLine("----------------------------------------")
            }
        }
    }
}
