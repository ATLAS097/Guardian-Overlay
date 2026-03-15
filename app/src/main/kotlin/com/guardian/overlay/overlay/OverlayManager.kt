package com.guardian.overlay.overlay

import android.content.Context
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import com.guardian.overlay.R
import com.guardian.overlay.model.DetectionResult

class OverlayManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var liveHighlightView: View? = null
    private var assistiveBubbleView: View? = null
    private var removeTargetView: View? = null
    private var removeTargetParams: WindowManager.LayoutParams? = null
    private var assistivePanelView: View? = null
    private var assistivePanelDismissLayer: View? = null
    private var assistiveBubbleParams: WindowManager.LayoutParams? = null
    private var snapAnimator: ValueAnimator? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var popupHoldUntilMs: Long = 0L
    private val autoHideRunnable = Runnable {
        hide(force = true)
    }
    private val marginPx = (context.resources.displayMetrics.density * 12).toInt()
    private val gapPx = (context.resources.displayMetrics.density * 12).toInt()
    private val touchSlopPx = (context.resources.displayMetrics.density * 8).toInt()
    private val removeTargetProximityPx = (context.resources.displayMetrics.density * 190).toInt()

    fun show(result: DetectionResult, visualData: OverlayVisualData? = null, holdDurationMs: Long = 12000L) {
        val view = overlayView ?: createView().also { created ->
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = marginPx
                y = marginPx
            }
            windowManager.addView(created, params)
            overlayView = created
        }

        popupHoldUntilMs = System.currentTimeMillis() + holdDurationMs.coerceAtLeast(1000L)
        mainHandler.removeCallbacks(autoHideRunnable)
        mainHandler.postDelayed(autoHideRunnable, holdDurationMs.coerceAtLeast(1000L))

        view.findViewById<TextView>(R.id.overlayScore).text = "Risk: ${result.prettyScore()}"
        view.findViewById<TextView>(R.id.overlayReasons).text = result.reasons.joinToString(separator = "\n• ", prefix = "• ")
        val highlightSummary = view.findViewById<TextView>(R.id.overlayHighlightSummary)

        clearLiveHighlightBox()
        val mostNotable = visualData?.boxes
            ?.maxByOrNull { box -> box.width().coerceAtLeast(0) * box.height().coerceAtLeast(0) }

        if (visualData == null || mostNotable == null) {
            highlightSummary.visibility = View.VISIBLE
            highlightSummary.text = context.getString(R.string.live_overlay_no_highlights)
            positionPopup(view, null)
        } else {
            highlightSummary.visibility = View.VISIBLE
            highlightSummary.text = context.getString(R.string.live_overlay_box_placed)
            val highlightRect = drawLiveHighlightBox(visualData, mostNotable)
            positionPopup(view, highlightRect)
        }
    }

    fun hide(force: Boolean = false) {
        if (!force && System.currentTimeMillis() < popupHoldUntilMs) {
            return
        }

        mainHandler.removeCallbacks(autoHideRunnable)
        popupHoldUntilMs = 0L
        clearLiveHighlightBox()
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    fun showAssistiveBubble(
        onRemoved: () -> Unit,
        onToggleDetection: () -> Boolean,
        isDetectionEnabled: () -> Boolean,
        onOpenQr: () -> Unit,
        onCheckCurrentScreen: () -> Unit,
        onOpenApp: () -> Unit
    ) {
        if (assistiveBubbleView != null) return

        val displayMetrics = context.resources.displayMetrics
        val screenW = displayMetrics.widthPixels
        val screenH = displayMetrics.heightPixels

        val bubbleView = LayoutInflater.from(context).inflate(R.layout.overlay_assistive_bubble, null)
        val bubbleSize = (56 * displayMetrics.density).toInt()

        val bubbleParams = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenW - bubbleSize - marginPx).coerceAtLeast(marginPx)
            y = (screenH / 2 - bubbleSize / 2).coerceAtLeast(marginPx)
        }
        assistiveBubbleParams = bubbleParams

        var initialTouchX = 0f
        var initialTouchY = 0f
        var initialX = 0
        var initialY = 0
        var didDrag = false
        var downTs = 0L

        bubbleView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    didDrag = false
                    downTs = System.currentTimeMillis()
                    snapAnimator?.cancel()
                    hideAssistivePanel()
                    showRemoveTarget()
                    bubbleView.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (!didDrag && (kotlin.math.abs(dx) > touchSlopPx || kotlin.math.abs(dy) > touchSlopPx)) {
                        didDrag = true
                    }

                    bubbleParams.x = (initialX + dx).coerceIn(
                        marginPx,
                        (screenW - bubbleSize - marginPx).coerceAtLeast(marginPx)
                    )
                    bubbleParams.y = (initialY + dy).coerceIn(
                        marginPx,
                        (screenH - bubbleSize - marginPx).coerceAtLeast(marginPx)
                    )
                    windowManager.updateViewLayout(bubbleView, bubbleParams)

                    updateRemoveTargetHoverState(bubbleParams)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val shouldRemove = isBubbleOverRemoveTarget(bubbleParams)
                    hideRemoveTarget()
                    bubbleView.animate().scaleX(1f).scaleY(1f).setDuration(120).start()

                    if (didDrag) {
                        if (shouldRemove) {
                            hideAssistiveBubble()
                            onRemoved()
                        } else {
                            snapBubbleToEdge(bubbleView, bubbleParams, screenW, bubbleSize)
                        }
                    } else {
                        val pressDuration = System.currentTimeMillis() - downTs
                        if (pressDuration < 550L) {
                            toggleAssistivePanel(
                                isDetectionEnabled = isDetectionEnabled,
                                onToggleDetection = onToggleDetection,
                                onOpenQr = onOpenQr,
                                onCheckCurrentScreen = onCheckCurrentScreen,
                                onOpenApp = onOpenApp
                            )
                        }
                    }
                    true
                }

                else -> false
            }
        }

        windowManager.addView(bubbleView, bubbleParams)
        assistiveBubbleView = bubbleView
    }

    fun hideAssistiveBubble() {
        snapAnimator?.cancel()
        hideAssistivePanel()
        hideRemoveTarget()
        assistiveBubbleView?.let {
            windowManager.removeView(it)
            assistiveBubbleView = null
        }
        assistiveBubbleParams = null
    }

    private fun drawLiveHighlightBox(visualData: OverlayVisualData, sourceBox: Rect): Rect {
        val displayMetrics = context.resources.displayMetrics
        val scaleX = displayMetrics.widthPixels.toFloat() / visualData.imageWidth.coerceAtLeast(1)
        val scaleY = displayMetrics.heightPixels.toFloat() / visualData.imageHeight.coerceAtLeast(1)
        val screenW = displayMetrics.widthPixels
        val screenH = displayMetrics.heightPixels

        val left = (sourceBox.left * scaleX).toInt().coerceAtLeast(0)
        val top = (sourceBox.top * scaleY).toInt().coerceAtLeast(0)
        val width = ((sourceBox.width() * scaleX).toInt()).coerceAtLeast(48)
        val height = ((sourceBox.height() * scaleY).toInt()).coerceAtLeast(36)
        val clampedLeft = left.coerceAtMost((screenW - width).coerceAtLeast(0))
        val clampedTop = top.coerceAtMost((screenH - height).coerceAtLeast(0))

        val highlightView = View(context).apply {
            background = context.getDrawable(R.drawable.bg_live_highlight_box)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = clampedLeft
            y = clampedTop
        }

        windowManager.addView(highlightView, params)
        liveHighlightView = highlightView
        return Rect(clampedLeft, clampedTop, clampedLeft + width, clampedTop + height)
    }

    private fun positionPopup(view: View, highlightRect: Rect?) {
        val displayMetrics = context.resources.displayMetrics
        val screenW = displayMetrics.widthPixels
        val screenH = displayMetrics.heightPixels

        val maxPopupWidth = (screenW - marginPx * 2).coerceAtLeast(1)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(maxPopupWidth, View.MeasureSpec.AT_MOST)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST)
        view.measure(widthSpec, heightSpec)
        val popupW = view.measuredWidth.coerceAtLeast(1)
        val popupH = view.measuredHeight.coerceAtLeast(1)

        val params = (view.layoutParams as? WindowManager.LayoutParams) ?: return
        params.gravity = Gravity.TOP or Gravity.START

        if (highlightRect == null) {
            params.x = ((screenW - popupW) / 2).coerceAtLeast(marginPx)
            params.y = marginPx
            windowManager.updateViewLayout(view, params)
            return
        }

        val centerX = highlightRect.centerX()
        val centerY = highlightRect.centerY()

        data class Candidate(val rect: Rect, val freeSpace: Int)

        fun makeRect(x: Int, y: Int): Rect = Rect(x, y, x + popupW, y + popupH)
        fun isInside(r: Rect): Boolean {
            return r.left >= marginPx && r.top >= marginPx && r.right <= (screenW - marginPx) && r.bottom <= (screenH - marginPx)
        }

        val candidates = listOf(
            Candidate(
                rect = makeRect(
                    x = highlightRect.right + gapPx,
                    y = (centerY - popupH / 2).coerceIn(marginPx, (screenH - marginPx - popupH).coerceAtLeast(marginPx))
                ),
                freeSpace = screenW - marginPx - (highlightRect.right + gapPx)
            ),
            Candidate(
                rect = makeRect(
                    x = highlightRect.left - gapPx - popupW,
                    y = (centerY - popupH / 2).coerceIn(marginPx, (screenH - marginPx - popupH).coerceAtLeast(marginPx))
                ),
                freeSpace = highlightRect.left - gapPx - marginPx
            ),
            Candidate(
                rect = makeRect(
                    x = (centerX - popupW / 2).coerceIn(marginPx, (screenW - marginPx - popupW).coerceAtLeast(marginPx)),
                    y = highlightRect.bottom + gapPx
                ),
                freeSpace = screenH - marginPx - (highlightRect.bottom + gapPx)
            ),
            Candidate(
                rect = makeRect(
                    x = (centerX - popupW / 2).coerceIn(marginPx, (screenW - marginPx - popupW).coerceAtLeast(marginPx)),
                    y = highlightRect.top - gapPx - popupH
                ),
                freeSpace = highlightRect.top - gapPx - marginPx
            )
        ).sortedByDescending { it.freeSpace }

        val chosen = candidates.firstOrNull { candidate ->
            isInside(candidate.rect) && !Rect.intersects(candidate.rect, highlightRect)
        }?.rect ?: run {
            // Fallback: choose position with minimal overlap and clamp into screen.
            candidates
                .map { candidate ->
                    val r = Rect(
                        candidate.rect.left.coerceIn(marginPx, (screenW - marginPx - popupW).coerceAtLeast(marginPx)),
                        candidate.rect.top.coerceIn(marginPx, (screenH - marginPx - popupH).coerceAtLeast(marginPx)),
                        0,
                        0
                    ).apply {
                        right = left + popupW
                        bottom = top + popupH
                    }
                    val intersect = Rect(r)
                    val overlapArea = if (intersect.intersect(highlightRect)) {
                        intersect.width() * intersect.height()
                    } else {
                        0
                    }
                    Pair(r, overlapArea)
                }
                .minByOrNull { it.second }
                ?.first
                ?: makeRect(marginPx, marginPx)
        }

        params.x = chosen.left
        params.y = chosen.top
        windowManager.updateViewLayout(view, params)
    }

    private fun clearLiveHighlightBox() {
        liveHighlightView?.let {
            windowManager.removeView(it)
            liveHighlightView = null
        }
    }

    private fun snapBubbleToEdge(bubbleView: View, params: WindowManager.LayoutParams, screenW: Int, bubbleSize: Int) {
        val leftEdge = marginPx
        val rightEdge = (screenW - bubbleSize - marginPx).coerceAtLeast(marginPx)
        val targetX = if (params.x + bubbleSize / 2 < screenW / 2) leftEdge else rightEdge

        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 560
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                params.x = animator.animatedValue as Int
                windowManager.updateViewLayout(bubbleView, params)
            }
            start()
        }
    }

    private fun showRemoveTarget() {
        if (removeTargetView != null) return

        val targetView = LayoutInflater.from(context).inflate(R.layout.overlay_assistive_remove_target, null)
        val targetSize = (64 * context.resources.displayMetrics.density).toInt()
        val screenW = context.resources.displayMetrics.widthPixels
        val screenH = context.resources.displayMetrics.heightPixels

        val params = WindowManager.LayoutParams(
            targetSize,
            targetSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenW - targetSize) / 2
            y = (screenH - targetSize - marginPx)
        }

        targetView.alpha = 0f
        targetView.visibility = View.INVISIBLE
        windowManager.addView(targetView, params)
        removeTargetView = targetView
        removeTargetParams = params
    }

    private fun hideRemoveTarget() {
        removeTargetView?.let {
            windowManager.removeView(it)
            removeTargetView = null
        }
        removeTargetParams = null
    }

    private fun updateRemoveTargetHoverState(bubbleParams: WindowManager.LayoutParams) {
        val target = removeTargetView ?: return
        val nearTarget = isBubbleNearRemoveTarget(bubbleParams)
        val overTarget = isBubbleOverRemoveTarget(bubbleParams)
        val bottomZone = isBubbleInBottomTriggerZone(bubbleParams)

        val targetAlpha = when {
            overTarget -> 1f
            nearTarget || bottomZone -> 0.86f
            else -> 0f
        }

        if (targetAlpha == 0f) {
            if (target.visibility == View.VISIBLE) {
                target.animate().alpha(0f).setDuration(120).withEndAction {
                    target.visibility = View.INVISIBLE
                }.start()
            }
        } else {
            if (target.visibility != View.VISIBLE) {
                target.visibility = View.VISIBLE
                target.alpha = 0f
            }
            target.animate().alpha(targetAlpha).setDuration(120).start()
        }
    }

    private fun isBubbleNearRemoveTarget(bubbleParams: WindowManager.LayoutParams): Boolean {
        val targetParams = removeTargetParams ?: return false

        val bubbleCx = bubbleParams.x + bubbleParams.width / 2
        val bubbleCy = bubbleParams.y + bubbleParams.height / 2
        val targetCx = targetParams.x + targetParams.width / 2
        val targetCy = targetParams.y + targetParams.height / 2

        val dx = bubbleCx - targetCx
        val dy = bubbleCy - targetCy
        val distanceSq = dx * dx + dy * dy
        val radiusSq = removeTargetProximityPx * removeTargetProximityPx
        return distanceSq <= radiusSq
    }

    private fun isBubbleInBottomTriggerZone(bubbleParams: WindowManager.LayoutParams): Boolean {
        val screenH = context.resources.displayMetrics.heightPixels
        val bubbleCy = bubbleParams.y + bubbleParams.height / 2
        return bubbleCy >= (screenH * 0.72f).toInt()
    }

    private fun isBubbleOverRemoveTarget(bubbleParams: WindowManager.LayoutParams): Boolean {
        val target = removeTargetView ?: return false
        val targetParams = target.layoutParams as? WindowManager.LayoutParams ?: return false

        val bubbleRect = Rect(
            bubbleParams.x,
            bubbleParams.y,
            bubbleParams.x + bubbleParams.width,
            bubbleParams.y + bubbleParams.height
        )
        val targetRect = Rect(
            targetParams.x,
            targetParams.y,
            targetParams.x + targetParams.width,
            targetParams.y + targetParams.height
        )
        return Rect.intersects(bubbleRect, targetRect)
    }

    private fun toggleAssistivePanel(
        isDetectionEnabled: () -> Boolean,
        onToggleDetection: () -> Boolean,
        onOpenQr: () -> Unit,
        onCheckCurrentScreen: () -> Unit,
        onOpenApp: () -> Unit
    ) {
        if (assistivePanelView != null) {
            hideAssistivePanel()
            return
        }

        val bubbleParams = assistiveBubbleParams ?: return
        val bubbleView = assistiveBubbleView ?: return

        val panelView = LayoutInflater.from(context).inflate(R.layout.overlay_assistive_panel, null)
        val toggleBtn = panelView.findViewById<Button>(R.id.toggleDetectionBtn)
        val openQrBtn = panelView.findViewById<Button>(R.id.openQrBtn)
        val checkScreenBtn = panelView.findViewById<Button>(R.id.checkScreenBtn)
        val openAppBtn = panelView.findViewById<Button>(R.id.openAppBtn)

        fun refreshToggleText() {
            val textRes = if (isDetectionEnabled()) {
                R.string.assistive_toggle_detection_off
            } else {
                R.string.assistive_toggle_detection_on
            }
            toggleBtn.text = context.getString(textRes)
        }

        refreshToggleText()

        toggleBtn.setOnClickListener {
            onToggleDetection()
            refreshToggleText()
        }

        openQrBtn.setOnClickListener {
            onOpenQr()
            hideAssistivePanel()
        }

        checkScreenBtn.setOnClickListener {
            onCheckCurrentScreen()
            hideAssistivePanel()
        }

        openAppBtn.setOnClickListener {
            onOpenApp()
            hideAssistivePanel()
        }

        val displayMetrics = context.resources.displayMetrics
        val screenW = displayMetrics.widthPixels
        val screenH = displayMetrics.heightPixels
        val maxWidth = (screenW * 0.62f).toInt().coerceAtLeast(180)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST)
        panelView.measure(widthSpec, heightSpec)
        val panelW = panelView.measuredWidth.coerceAtLeast(180)
        val panelH = panelView.measuredHeight.coerceAtLeast(120)

        val bubbleRect = Rect(
            bubbleParams.x,
            bubbleParams.y,
            bubbleParams.x + bubbleParams.width,
            bubbleParams.y + bubbleParams.height
        )

        val panelX = if (bubbleRect.centerX() < screenW / 2) {
            (bubbleRect.right + gapPx).coerceAtMost(screenW - panelW - marginPx)
        } else {
            (bubbleRect.left - gapPx - panelW).coerceAtLeast(marginPx)
        }
        val panelY = (bubbleRect.centerY() - panelH / 2)
            .coerceIn(marginPx, (screenH - panelH - marginPx).coerceAtLeast(marginPx))

        val panelParams = WindowManager.LayoutParams(
            panelW,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = panelX
            this.y = panelY
        }

        val dismissLayer = View(context)
        val dismissLayerParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        dismissLayer.setOnTouchListener { _, _ ->
            hideAssistivePanel()
            true
        }

        windowManager.addView(dismissLayer, dismissLayerParams)
        assistivePanelDismissLayer = dismissLayer

        windowManager.addView(panelView, panelParams)
        panelView.alpha = 0f
        panelView.translationY = 16f
        panelView.animate().alpha(1f).translationY(0f).setDuration(180).setInterpolator(DecelerateInterpolator()).start()
        assistivePanelView = panelView

        // Keep bubble above panel for better perceived responsiveness.
        windowManager.updateViewLayout(bubbleView, bubbleParams)
    }

    private fun hideAssistivePanel() {
        assistivePanelView?.let {
            val panel = it
            panel.animate().alpha(0f).translationY(12f).setDuration(130).withEndAction {
                runCatching { windowManager.removeView(panel) }
                if (assistivePanelView === panel) {
                    assistivePanelView = null
                }
            }.start()
        }
        assistivePanelDismissLayer?.let {
            runCatching { windowManager.removeView(it) }
            assistivePanelDismissLayer = null
        }
    }

    private fun createView(): View {
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_warning, null)
        view.findViewById<Button>(R.id.overlayDismiss).setOnClickListener {
            hide(force = true)
        }
        return view
    }
}

data class OverlayVisualData(
    val imageWidth: Int,
    val imageHeight: Int,
    val boxes: List<Rect>
)
