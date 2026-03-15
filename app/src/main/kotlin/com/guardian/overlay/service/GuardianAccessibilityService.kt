package com.guardian.overlay.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.guardian.overlay.detection.DetectorProvider
import com.guardian.overlay.detection.PhrasePackLoader
import com.guardian.overlay.MainActivity
import com.guardian.overlay.model.DetectionResult
import com.guardian.overlay.ocr.OcrProcessor
import com.guardian.overlay.overlay.OverlayManager
import com.guardian.overlay.overlay.OverlayVisualData
import com.guardian.overlay.processing.RiskTextHighlighter
import com.guardian.overlay.settings.AppSettingsStore

class GuardianAccessibilityService : AccessibilityService() {
    private lateinit var overlayManager: OverlayManager
    private val detector by lazy { DetectorProvider.get(this) }
    private val ocrProcessor = OcrProcessor()
    private val riskKeywords by lazy {
        RiskTextHighlighter.buildKeywordSet(PhrasePackLoader(this).loadRules())
    }
    private lateinit var settings: AppSettingsStore
    private var lastScanTs = 0L
    private var isScanInFlight = false
    private val minOverlayRiskScore = 0.12f
    private val assistiveActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_SHOW_ASSISTIVE_BUBBLE -> {
                    settings.setAssistiveTouchEnabled(true)
                    syncAssistiveBubbleState(forceShow = true)
                }

                ACTION_HIDE_ASSISTIVE_BUBBLE -> {
                    settings.setAssistiveTouchEnabled(false)
                    syncAssistiveBubbleState()
                }

                ACTION_SYNC_ASSISTIVE_BUBBLE -> {
                    syncAssistiveBubbleState()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayManager = OverlayManager(this)
        settings = AppSettingsStore(this)
        registerAssistiveActionReceiver()
        syncAssistiveBubbleState()
        Log.i(TAG, "GuardianAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        syncAssistiveBubbleState()

        if (!settings.isDetectionEnabled()) {
            overlayManager.hide(force = true)
            return
        }

        if (isScanInFlight) return

        val now = System.currentTimeMillis()
        if (now - lastScanTs < 1200) {
            return
        }

        lastScanTs = now
        val source = event.packageName?.toString() ?: "unknown"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runScreenshotDetection(source)
        } else {
            val result = detectFromNodeText(source)
            renderResult(result, null)
        }
    }

    override fun onInterrupt() {
        isScanInFlight = false
        if (::overlayManager.isInitialized) {
            overlayManager.hide()
            overlayManager.hideAssistiveBubble()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(assistiveActionReceiver) }
        if (::overlayManager.isInitialized) {
            overlayManager.hideAssistiveBubble()
        }
    }

    private fun runScreenshotDetection(source: String, forceShowResult: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        isScanInFlight = true
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val fullBitmap = screenshotToBitmap(screenshot)
                if (fullBitmap == null) {
                    val fallback = detectFromNodeText(source)
                    renderResult(fallback, null, forceShowResult)
                    isScanInFlight = false
                    return
                }

                ocrProcessor.extractScanDataFromBitmap(
                    bitmap = fullBitmap,
                    onSuccess = { scanData ->
                        val result = detector.detect(scanData.text, source)
                        val boxes = RiskTextHighlighter.findSuspiciousBoxes(scanData.lines, riskKeywords)
                        val visualData = OverlayVisualData(
                            imageWidth = fullBitmap.width,
                            imageHeight = fullBitmap.height,
                            boxes = boxes
                        )
                        renderResult(result, visualData, forceShowResult)
                        isScanInFlight = false
                    },
                    onError = { err ->
                        Log.w(TAG, "Screenshot OCR failed", err)
                        val fallback = detectFromNodeText(source)
                        renderResult(fallback, null, forceShowResult)
                        isScanInFlight = false
                    }
                )
            }

            override fun onFailure(errorCode: Int) {
                Log.w(TAG, "takeScreenshot failed with code=$errorCode")
                val fallback = detectFromNodeText(source)
                renderResult(fallback, null, forceShowResult)
                isScanInFlight = false
            }
        })
    }

    private fun screenshotToBitmap(screenshot: ScreenshotResult): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val hardwareBuffer = screenshot.hardwareBuffer
        return try {
            val colorSpace = screenshot.colorSpace
            Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                ?.copy(Bitmap.Config.ARGB_8888, false)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to create bitmap from screenshot", t)
            null
        } finally {
            hardwareBuffer.close()
        }
    }

    private fun detectFromNodeText(source: String): DetectionResult? {
        val root = rootInActiveWindow ?: return null
        val text = collectText(root).trim()
        if (text.length < 25) return null
        return detector.detect(text, source)
    }

    private fun renderResult(result: DetectionResult?, visualData: OverlayVisualData?, forceShow: Boolean = false) {
        if (result == null) {
            overlayManager.hide()
            return
        }

        if (forceShow || result.score >= minOverlayRiskScore) {
            val holdMs = if (forceShow) 18000L else 12000L
            overlayManager.show(result, visualData, holdDurationMs = holdMs)
        } else {
            overlayManager.hide()
        }
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
        const val ACTION_SHOW_ASSISTIVE_BUBBLE = "com.guardian.overlay.action.SHOW_ASSISTIVE_BUBBLE"
        const val ACTION_HIDE_ASSISTIVE_BUBBLE = "com.guardian.overlay.action.HIDE_ASSISTIVE_BUBBLE"
        const val ACTION_SYNC_ASSISTIVE_BUBBLE = "com.guardian.overlay.action.SYNC_ASSISTIVE_BUBBLE"
    }

    private fun registerAssistiveActionReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_SHOW_ASSISTIVE_BUBBLE)
            addAction(ACTION_HIDE_ASSISTIVE_BUBBLE)
            addAction(ACTION_SYNC_ASSISTIVE_BUBBLE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(assistiveActionReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(assistiveActionReceiver, filter)
        }
    }

    private fun syncAssistiveBubbleState(forceShow: Boolean = false) {
        val shouldShow = forceShow || settings.isAssistiveTouchEnabled()
        if (shouldShow) {
            overlayManager.showAssistiveBubble(
                onRemoved = {
                    settings.setAssistiveTouchEnabled(false)
                },
                onToggleDetection = {
                    val enabled = !settings.isDetectionEnabled()
                    settings.setDetectionEnabled(enabled)
                    enabled
                },
                isDetectionEnabled = {
                    settings.isDetectionEnabled()
                },
                onOpenQr = {
                    val intent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_QR)
                    }
                    startActivity(intent)
                },
                onCheckCurrentScreen = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        if (!isScanInFlight) {
                            runScreenshotDetection(source = "manual_screen_check", forceShowResult = true)
                        }
                    } else {
                        val result = detectFromNodeText("manual_screen_check")
                        renderResult(result, null, forceShow = true)
                    }
                },
                onOpenApp = {
                    val intent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_HOME)
                    }
                    startActivity(intent)
                }
            )
        } else {
            overlayManager.hideAssistiveBubble()
        }
    }
}
