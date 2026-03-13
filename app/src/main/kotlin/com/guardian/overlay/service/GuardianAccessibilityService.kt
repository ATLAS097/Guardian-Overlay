package com.guardian.overlay.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.guardian.overlay.data.DetectionHistoryStore
import com.guardian.overlay.detection.DetectorProvider
import com.guardian.overlay.overlay.OverlayManager

class GuardianAccessibilityService : AccessibilityService() {
    private lateinit var overlayManager: OverlayManager
    private val detector by lazy { DetectorProvider.get(this) }
    private lateinit var historyStore: DetectionHistoryStore
    private var lastScanTs = 0L
    private val minOverlayRiskScore = 0.12f

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayManager = OverlayManager(this)
        historyStore = DetectionHistoryStore(this)
        Log.i(TAG, "GuardianAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val now = System.currentTimeMillis()
        if (now - lastScanTs < 1200) {
            return
        }

        val root = rootInActiveWindow ?: return
        val text = collectText(root).trim()
        if (text.length < 25) {
            overlayManager.hide()
            return
        }

        lastScanTs = now
        val result = detector.detect(text, source = event.packageName?.toString() ?: "unknown")
        historyStore.saveResult(result)

        if (result.score >= minOverlayRiskScore) {
            overlayManager.show(result)
        } else {
            overlayManager.hide()
        }
    }

    override fun onInterrupt() {
        overlayManager.hide()
    }

    private fun collectText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        node.text?.let { sb.append(it).append(' ') }
        node.contentDescription?.let { sb.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            sb.append(collectText(node.getChild(i)))
        }
        return sb.toString()
    }

    companion object {
        private const val TAG = "GuardianService"
    }
}
