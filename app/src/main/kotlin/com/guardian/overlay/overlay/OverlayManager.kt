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
import android.widget.ImageView
import android.widget.TextView
import android.widget.Button
import androidx.core.content.ContextCompat
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
    private var trustedContactPickerView: View? = null
    private var trustedContactPickerDismissLayer: View? = null
    private var assistiveBubbleParams: WindowManager.LayoutParams? = null
    private var snapAnimator: ValueAnimator? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var popupHoldUntilMs: Long = 0L
    private val autoHideRunnable = Runnable {
        hide(force = true)
    }
    private val marginPx = (context.resources.displayMetrics.density * EDGE_MARGIN_DP).toInt()
    private val gapPx = (context.resources.displayMetrics.density * POPUP_GAP_DP).toInt()
    private val touchSlopPx = (context.resources.displayMetrics.density * TOUCH_SLOP_DP).toInt()
    private val removeTargetProximityPx = (context.resources.displayMetrics.density * REMOVE_TARGET_PROXIMITY_DP).toInt()

    fun show(
        result: DetectionResult,
        visualData: OverlayVisualData? = null,
        holdDurationMs: Long = 12000L,
        showTrustedContactAction: Boolean = false,
        onContactTrustedPerson: (() -> Unit)? = null
    ) {
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

        popupHoldUntilMs = System.currentTimeMillis() + holdDurationMs.coerceAtLeast(MIN_HOLD_DURATION_MS)
        mainHandler.removeCallbacks(autoHideRunnable)
        mainHandler.postDelayed(autoHideRunnable, holdDurationMs.coerceAtLeast(MIN_HOLD_DURATION_MS))

        view.findViewById<TextView>(R.id.overlayScore).text = "Risk: ${result.prettyScore()}"
        view.findViewById<TextView>(R.id.overlayReasons).text = result.reasons.joinToString(separator = "\n• ", prefix = "• ")
        val highlightSummary = view.findViewById<TextView>(R.id.overlayHighlightSummary)
        val contactTrustedBtn = view.findViewById<Button>(R.id.overlayContactTrusted)

        if (showTrustedContactAction && onContactTrustedPerson != null) {
            contactTrustedBtn.visibility = View.VISIBLE
            contactTrustedBtn.setOnClickListener {
                onContactTrustedPerson()
            }
        } else {
            contactTrustedBtn.visibility = View.GONE
            contactTrustedBtn.setOnClickListener(null)
        }

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
        hideTrustedContactPicker()
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
        val bubbleSize = (BUBBLE_SIZE_DP * displayMetrics.density).toInt()

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
                    bubbleView.animate().scaleX(BUBBLE_DOWN_SCALE).scaleY(BUBBLE_DOWN_SCALE).setDuration(BUBBLE_PRESS_DURATION_MS).start()
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
                    bubbleView.animate().scaleX(1f).scaleY(1f).setDuration(BUBBLE_RELEASE_DURATION_MS).start()

                    if (didDrag) {
                        if (shouldRemove) {
                            hideAssistiveBubble()
                            onRemoved()
                        } else {
                            snapBubbleToEdge(bubbleView, bubbleParams, screenW, bubbleSize)
                        }
                    } else {
                        val pressDuration = System.currentTimeMillis() - downTs
                        if (pressDuration < CLICK_MAX_PRESS_DURATION_MS) {
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
        hideTrustedContactPicker()
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
            duration = BUBBLE_SNAP_DURATION_MS
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
        val targetSize = (REMOVE_TARGET_SIZE_DP * context.resources.displayMetrics.density).toInt()
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
            nearTarget || bottomZone -> REMOVE_TARGET_NEAR_ALPHA
            else -> 0f
        }

        if (targetAlpha == 0f) {
            if (target.visibility == View.VISIBLE) {
                target.animate().alpha(0f).setDuration(REMOVE_TARGET_FADE_DURATION_MS).withEndAction {
                    target.visibility = View.INVISIBLE
                }.start()
            }
        } else {
            if (target.visibility != View.VISIBLE) {
                target.visibility = View.VISIBLE
                target.alpha = 0f
            }
            target.animate().alpha(targetAlpha).setDuration(REMOVE_TARGET_FADE_DURATION_MS).start()
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
        return bubbleCy >= (screenH * REMOVE_TARGET_BOTTOM_TRIGGER_RATIO).toInt()
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

        val bubbleView = assistiveBubbleView ?: return
        val bubbleParams = assistiveBubbleParams ?: return

        val panelView = LayoutInflater.from(context).inflate(R.layout.overlay_assistive_panel, null)
        val toggleAction = panelView.findViewById<View>(R.id.toggleDetectionBtn)
        val openQrAction = panelView.findViewById<View>(R.id.openQrBtn)
        val checkScreenAction = panelView.findViewById<View>(R.id.checkScreenBtn)
        val openAppAction = panelView.findViewById<View>(R.id.openAppBtn)
        val toggleLabel = panelView.findViewById<TextView>(R.id.toggleDetectionLabel)
        val toggleState = panelView.findViewById<TextView>(R.id.toggleDetectionState)
        val toggleIcon = panelView.findViewById<ImageView>(R.id.toggleDetectionIcon)

        fun refreshToggleLabel() {
            val enabled = isDetectionEnabled()
            toggleLabel.text = context.getString(R.string.assistive_action_detection_title)

            if (enabled) {
                toggleAction.setBackgroundResource(R.drawable.bg_assistive_toggle_on)
                toggleState.setBackgroundResource(R.drawable.bg_assistive_toggle_on)
                toggleState.text = context.getString(R.string.assistive_detection_state_on)
                toggleState.setTextColor(ContextCompat.getColor(context, R.color.assistive_detection_on_text))
                toggleIcon.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                toggleAction.setBackgroundResource(R.drawable.bg_assistive_toggle_off)
                toggleState.setBackgroundResource(R.drawable.bg_assistive_toggle_off)
                toggleState.text = context.getString(R.string.assistive_detection_state_off)
                toggleState.setTextColor(ContextCompat.getColor(context, R.color.assistive_detection_off_text))
                toggleIcon.setImageResource(android.R.drawable.ic_media_play)
            }

            val iconTintRes = if (enabled) {
                R.color.assistive_detection_on_text
            } else {
                R.color.assistive_detection_off_text
            }
            toggleIcon.setColorFilter(ContextCompat.getColor(context, iconTintRes))
        }

        refreshToggleLabel()

        toggleAction.setOnClickListener {
            onToggleDetection()
            refreshToggleLabel()
        }

        openQrAction.setOnClickListener {
            onOpenQr()
            hideAssistivePanel()
        }

        checkScreenAction.setOnClickListener {
            onCheckCurrentScreen()
            hideAssistivePanel()
        }

        openAppAction.setOnClickListener {
            onOpenApp()
            hideAssistivePanel()
        }

        val displayMetrics = context.resources.displayMetrics
        val screenW = displayMetrics.widthPixels
        val screenH = displayMetrics.heightPixels
        val maxWidth = (screenW * PANEL_MAX_WIDTH_RATIO).toInt().coerceAtLeast(PANEL_MIN_WIDTH_PX)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST)
        panelView.measure(widthSpec, heightSpec)
        val panelW = panelView.measuredWidth.coerceAtLeast(PANEL_MIN_WIDTH_PX)
        val panelH = panelView.measuredHeight.coerceAtLeast(PANEL_MIN_HEIGHT_PX)

        val panelX = ((screenW - panelW) / 2).coerceAtLeast(marginPx)
        val panelY = ((screenH - panelH) / 2).coerceAtLeast(marginPx)

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
        panelView.translationY = PANEL_ENTER_TRANSLATION_Y
        panelView.scaleX = PANEL_ENTER_SCALE
        panelView.scaleY = PANEL_ENTER_SCALE
        panelView.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(PANEL_ENTER_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
        assistivePanelView = panelView

        // Keep bubble above panel for better perceived responsiveness.
        windowManager.updateViewLayout(bubbleView, bubbleParams)
    }

    fun showTrustedContactPicker(
        onSms: () -> Unit,
        onCall: () -> Unit,
        onShare: () -> Unit
    ) {
        if (trustedContactPickerView != null) return

        hideAssistivePanel()

        val pickerView = LayoutInflater.from(context).inflate(R.layout.overlay_trusted_contact_picker, null)
        val smsAction = pickerView.findViewById<View>(R.id.trustedPickerSmsBtn)
        val callAction = pickerView.findViewById<View>(R.id.trustedPickerCallBtn)
        val shareAction = pickerView.findViewById<View>(R.id.trustedPickerShareBtn)
        val cancelAction = pickerView.findViewById<Button>(R.id.trustedPickerCancelBtn)

        fun consume(action: () -> Unit) {
            action()
            hideTrustedContactPicker()
        }

        smsAction.setOnClickListener { consume(onSms) }
        callAction.setOnClickListener { consume(onCall) }
        shareAction.setOnClickListener { consume(onShare) }
        cancelAction.setOnClickListener { hideTrustedContactPicker() }

        val displayMetrics = context.resources.displayMetrics
        val screenW = displayMetrics.widthPixels
        val screenH = displayMetrics.heightPixels
        val maxWidth = (screenW * PANEL_MAX_WIDTH_RATIO).toInt().coerceAtLeast(PANEL_MIN_WIDTH_PX)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST)
        pickerView.measure(widthSpec, heightSpec)
        val pickerW = pickerView.measuredWidth.coerceAtLeast(PANEL_MIN_WIDTH_PX)
        val pickerH = pickerView.measuredHeight.coerceAtLeast(PANEL_MIN_HEIGHT_PX)

        val pickerParams = WindowManager.LayoutParams(
            pickerW,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((screenW - pickerW) / 2).coerceAtLeast(marginPx)
            y = ((screenH - pickerH) / 2).coerceAtLeast(marginPx)
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
            hideTrustedContactPicker()
            true
        }

        windowManager.addView(dismissLayer, dismissLayerParams)
        trustedContactPickerDismissLayer = dismissLayer

        windowManager.addView(pickerView, pickerParams)
        pickerView.alpha = 0f
        pickerView.translationY = PANEL_ENTER_TRANSLATION_Y
        pickerView.scaleX = PANEL_ENTER_SCALE
        pickerView.scaleY = PANEL_ENTER_SCALE
        pickerView.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(PANEL_ENTER_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
        trustedContactPickerView = pickerView
    }

    fun hideTrustedContactPicker() {
        trustedContactPickerView?.let {
            val panel = it
            panel.animate()
                .alpha(0f)
                .translationY(PANEL_EXIT_TRANSLATION_Y)
                .scaleX(PANEL_EXIT_SCALE)
                .scaleY(PANEL_EXIT_SCALE)
                .setDuration(PANEL_EXIT_DURATION_MS)
                .withEndAction {
                    runCatching { windowManager.removeView(panel) }
                    if (trustedContactPickerView === panel) {
                        trustedContactPickerView = null
                    }
                }
                .start()
        }

        trustedContactPickerDismissLayer?.let {
            runCatching { windowManager.removeView(it) }
            trustedContactPickerDismissLayer = null
        }
    }

    private fun hideAssistivePanel() {
        assistivePanelView?.let {
            val panel = it
            panel.animate()
                .alpha(0f)
                .translationY(PANEL_EXIT_TRANSLATION_Y)
                .scaleX(PANEL_EXIT_SCALE)
                .scaleY(PANEL_EXIT_SCALE)
                .setDuration(PANEL_EXIT_DURATION_MS)
                .withEndAction {
                    runCatching { windowManager.removeView(panel) }
                    if (assistivePanelView === panel) {
                        assistivePanelView = null
                    }
                }
                .start()
        }
        assistivePanelDismissLayer?.let {
            runCatching { windowManager.removeView(it) }
            assistivePanelDismissLayer = null
        }
    }

    private fun createView(): View {
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_warning, null)
        view.findViewById<Button>(R.id.overlayDismiss).setOnClickListener {
            hideTrustedContactPicker()
            hide(force = true)
        }
        return view
    }

    companion object {
        private const val EDGE_MARGIN_DP = 12f
        private const val POPUP_GAP_DP = 12f
        private const val TOUCH_SLOP_DP = 8f
        private const val REMOVE_TARGET_PROXIMITY_DP = 190f
        private const val BUBBLE_SIZE_DP = 56
        private const val REMOVE_TARGET_SIZE_DP = 64

        private const val MIN_HOLD_DURATION_MS = 1000L
        private const val CLICK_MAX_PRESS_DURATION_MS = 550L
        private const val BUBBLE_PRESS_DURATION_MS = 90L
        private const val BUBBLE_RELEASE_DURATION_MS = 120L
        private const val BUBBLE_SNAP_DURATION_MS = 560L
        private const val REMOVE_TARGET_FADE_DURATION_MS = 120L
        private const val PANEL_ENTER_DURATION_MS = 180L
        private const val PANEL_EXIT_DURATION_MS = 130L

        private const val REMOVE_TARGET_NEAR_ALPHA = 0.86f
        private const val REMOVE_TARGET_BOTTOM_TRIGGER_RATIO = 0.72f
        private const val PANEL_MAX_WIDTH_RATIO = 0.62f
        private const val PANEL_MIN_WIDTH_PX = 180
        private const val PANEL_MIN_HEIGHT_PX = 120
        private const val PANEL_ENTER_TRANSLATION_Y = 10f
        private const val PANEL_EXIT_TRANSLATION_Y = 12f
        private const val PANEL_ENTER_SCALE = 0.94f
        private const val PANEL_EXIT_SCALE = 0.96f
        private const val BUBBLE_DOWN_SCALE = 0.94f
    }
}

data class OverlayVisualData(
    val imageWidth: Int,
    val imageHeight: Int,
    val boxes: List<Rect>
)
