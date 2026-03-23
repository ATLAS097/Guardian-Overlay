package com.guardian.overlay.ui.gallery

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.guardian.overlay.R

class ScamHighlightOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2f
        color = ContextCompat.getColor(context, R.color.highlight_box_stroke)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.highlight_box_fill)
    }

    private var sourceWidth: Int = 1
    private var sourceHeight: Int = 1
    private var boxes: List<Rect> = emptyList()
    private var isScamVerdict: Boolean = true

    fun setHighlightData(sourceWidth: Int, sourceHeight: Int, boxes: List<Rect>, isScam: Boolean = true) {
        this.sourceWidth = sourceWidth.coerceAtLeast(1)
        this.sourceHeight = sourceHeight.coerceAtLeast(1)
        this.boxes = boxes
        this.isScamVerdict = isScam

        if (isScamVerdict) {
            strokePaint.color = ContextCompat.getColor(context, R.color.highlight_box_stroke)
            fillPaint.color = ContextCompat.getColor(context, R.color.highlight_box_fill)
        } else {
            strokePaint.color = ContextCompat.getColor(context, R.color.highlight_box_safe_stroke)
            fillPaint.color = ContextCompat.getColor(context, R.color.highlight_box_safe_fill)
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (boxes.isEmpty()) return

        val vw = width.toFloat()
        val vh = height.toFloat()
        val iw = sourceWidth.toFloat()
        val ih = sourceHeight.toFloat()

        val scale = minOf(vw / iw, vh / ih)
        val dx = (vw - iw * scale) / 2f
        val dy = (vh - ih * scale) / 2f

        for (box in boxes) {
            val left = dx + box.left * scale
            val top = dy + box.top * scale
            val right = dx + box.right * scale
            val bottom = dy + box.bottom * scale
            canvas.drawRect(left, top, right, bottom, fillPaint)
            canvas.drawRect(left, top, right, bottom, strokePaint)
        }
    }
}
