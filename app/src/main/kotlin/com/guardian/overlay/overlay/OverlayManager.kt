package com.guardian.overlay.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.guardian.overlay.R
import com.guardian.overlay.model.DetectionResult

class OverlayManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    fun show(result: DetectionResult) {
        val view = overlayView ?: createView().also { created ->
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 120
            }
            windowManager.addView(created, params)
            overlayView = created
        }

        view.findViewById<TextView>(R.id.overlayScore).text = "Risk: ${result.prettyScore()}"
        view.findViewById<TextView>(R.id.overlayReasons).text = result.reasons.joinToString(separator = "\n• ", prefix = "• ")
    }

    fun hide() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    private fun createView(): View {
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_warning, null)
        view.findViewById<Button>(R.id.overlayDismiss).setOnClickListener {
            hide()
        }
        return view
    }
}
