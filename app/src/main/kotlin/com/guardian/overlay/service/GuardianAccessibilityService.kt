package com.guardian.overlay.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.Display
import android.widget.Toast
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.guardian.overlay.R
import com.guardian.overlay.detection.DetectorProvider
import com.guardian.overlay.detection.PhrasePackLoader
import com.guardian.overlay.MainActivity
import com.guardian.overlay.model.DetectionResult
import com.guardian.overlay.ocr.OcrProcessor
import com.guardian.overlay.overlay.OverlayManager
import com.guardian.overlay.overlay.OverlayVisualData
import com.guardian.overlay.processing.RiskTextHighlighter
import com.guardian.overlay.settings.AppSettingsStore
import com.guardian.overlay.settings.TrustedContactActionMode

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
    private var manualHoldUntilTs = 0L
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

        val now = System.currentTimeMillis()
        val source = event.packageName?.toString() ?: "unknown"

        // Avoid self-detection while user is in Guardian app screens.
        if (source == packageName) {
            return
        }

        if (!settings.isDetectionEnabled()) {
            // Manual scan can force-show a result; avoid hiding it until hold expires.
            if (now >= manualHoldUntilTs) {
                overlayManager.hide(force = true)
            }
            return
        }

        if (now < manualHoldUntilTs) {
            return
        }

        if (isScanInFlight) return

        if (now - lastScanTs < LIVE_SCAN_THROTTLE_MS) {
            return
        }

        lastScanTs = now

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
            if (!forceShow && System.currentTimeMillis() < manualHoldUntilTs) {
                return
            }
            overlayManager.hide()
            return
        }

        if (forceShow || result.score >= MIN_OVERLAY_RISK_SCORE) {
            val holdMs = if (forceShow) MANUAL_RESULT_HOLD_MS else AUTO_RESULT_HOLD_MS
            val canContactTrustedPerson = shouldShowTrustedContactAction(result)
            overlayManager.show(
                result = result,
                visualData = visualData,
                holdDurationMs = holdMs,
                showTrustedContactAction = canContactTrustedPerson,
                onContactTrustedPerson = if (canContactTrustedPerson) {
                    { openTrustedContactChooser(result) }
                } else {
                    null
                }
            )
        } else {
            if (System.currentTimeMillis() < manualHoldUntilTs) {
                return
            }
            overlayManager.hide()
        }
    }

    private fun shouldShowTrustedContactAction(result: DetectionResult): Boolean {
        if (!settings.isTrustedContactEnabled()) return false
        if (!settings.hasTrustedContactConfigured()) return false
        return result.isScam || result.score >= TRUSTED_CONTACT_MIN_RISK_SCORE
    }

    private fun openTrustedContactChooser(result: DetectionResult) {
        val actionMode = settings.getTrustedContactActionMode()
        val rawNumber = settings.getTrustedContactNumber()
        if (rawNumber.isBlank()) {
            showTrustedActionFailure()
            return
        }
        val normalizedNumber = normalizePhoneNumber(rawNumber)
        if (!isPhoneLikeNumber(normalizedNumber)) {
            Log.w(TAG, "Trusted contact number is invalid: '$rawNumber'")
            showTrustedActionFailure()
            return
        }

        val summary = result.reasons.take(3).joinToString(separator = "; ").ifBlank { "Suspicious content detected" }
        val smsBody = getString(R.string.trusted_contact_sms_body, result.prettyScore(), summary)

        val smsIntent = buildSmsIntent(normalizedNumber, smsBody)
        val callIntent = buildCallIntent(normalizedNumber)
        val shareIntent = buildShareIntent(smsBody)

        when (actionMode) {
            TrustedContactActionMode.SMS -> {
                if (!launchAny(listOf(smsIntent, callIntent, shareIntent))) {
                    showTrustedActionFailure()
                }
            }
            TrustedContactActionMode.CALL -> {
                if (!launchAny(listOf(callIntent, smsIntent, shareIntent))) {
                    showTrustedActionFailure()
                }
            }
            TrustedContactActionMode.SHARE -> {
                if (!launchAny(listOf(shareIntent, smsIntent, callIntent))) {
                    showTrustedActionFailure()
                }
            }
            TrustedContactActionMode.CHOOSER -> {
                overlayManager.showTrustedContactPicker(
                    onSms = {
                        if (!launchAny(listOf(smsIntent, callIntent, shareIntent))) {
                            showTrustedActionFailure()
                        }
                    },
                    onCall = {
                        if (!launchAny(listOf(callIntent, smsIntent, shareIntent))) {
                            showTrustedActionFailure()
                        }
                    },
                    onShare = {
                        if (!launchAny(listOf(shareIntent, smsIntent, callIntent))) {
                            showTrustedActionFailure()
                        }
                    }
                )
            }
        }
    }

    private fun launchAny(candidates: List<Intent>): Boolean {
        candidates.distinctBy { "${it.action}:${it.data}:${it.type}" }.forEach { intent ->
            if (launchIntent(intent)) {
                return true
            }
        }
        return false
    }

    private fun launchIntent(intent: Intent): Boolean {
        return runCatching {
            startActivity(intent)
            true
        }.getOrElse { err ->
            Log.w(TAG, "Failed to open trusted contact action", err)
            false
        }
    }

    private fun showTrustedActionFailure() {
        Toast.makeText(
            this,
            getString(R.string.trusted_contact_action_unavailable),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun buildSmsIntent(number: String, body: String): Intent {
        return Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).apply {
            putExtra("sms_body", body)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun buildCallIntent(number: String): Intent {
        return Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun buildShareIntent(body: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.trusted_contact_chooser_title))
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun normalizePhoneNumber(number: String): String {
        val trimmed = number.trim()
        if (trimmed.isBlank()) return ""
        val keepPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        return if (keepPlus && digits.isNotEmpty()) "+$digits" else digits
    }

    private fun isPhoneLikeNumber(number: String): Boolean {
        return number.matches(Regex("^\\+?\\d{7,15}$"))
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
        private const val LIVE_SCAN_THROTTLE_MS = 1200L
        private const val MANUAL_RESULT_HOLD_MS = 20000L
        private const val AUTO_RESULT_HOLD_MS = 12000L
        private const val MIN_OVERLAY_RISK_SCORE = 0.12f
        private const val TRUSTED_CONTACT_MIN_RISK_SCORE = 0.55f
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
                            manualHoldUntilTs = System.currentTimeMillis() + MANUAL_RESULT_HOLD_MS
                            runScreenshotDetection(source = "manual_screen_check", forceShowResult = true)
                        }
                    } else {
                        manualHoldUntilTs = System.currentTimeMillis() + MANUAL_RESULT_HOLD_MS
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
