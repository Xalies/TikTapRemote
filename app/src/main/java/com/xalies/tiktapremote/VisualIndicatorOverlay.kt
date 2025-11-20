package com.xalies.tiktapremote

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class VisualIndicatorOverlay(context: Context) : View(context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val paint = Paint().apply {
        color = 0x80FF0000.toInt() // Semi-transparent Red
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var touchX = -1f
    private var touchY = -1f
    private var isVisible = false

    // Auto-hide handler
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable {
        isVisible = false
        invalidate() // Redraw to clear the screen
    }

    init {
        // We want this view to be full screen but let touches pass through
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or // Draw over status bar
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, // Use GPU
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.LEFT

        try {
            windowManager.addView(this, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showTap(x: Int, y: Int) {
        touchX = x.toFloat()
        touchY = y.toFloat()
        isVisible = true

        // Force a redraw immediately
        invalidate()

        // Reset the hide timer
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 300) // Disappear after 300ms
    }

    fun remove() {
        try {
            windowManager.removeView(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isVisible) {
            // Draw a circle of radius 40 at the touch point
            canvas.drawCircle(touchX, touchY, 40f, paint)
        }
    }
}