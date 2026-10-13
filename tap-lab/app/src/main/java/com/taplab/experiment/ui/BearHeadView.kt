package com.taplab.experiment.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class BearHeadView(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99C4A07A.toInt() }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC4A372C.toInt() }
    private var running = false

    fun setRunning(active: Boolean) {
        running = active
        fill.color = if (active) 0x99B7C7A3.toInt() else 0x99C4A07A.toInt()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w * 0.52f
        val cy = h * 0.56f
        val radius = w * 0.34f
        canvas.drawCircle(cx - radius * 0.72f, cy - radius * 0.85f, radius * 0.38f, fill)
        canvas.drawCircle(cx + radius * 0.72f, cy - radius * 0.85f, radius * 0.38f, fill)
        canvas.drawCircle(cx, cy, radius, fill)
        ink.color = if (running) 0xCC2C3A28.toInt() else 0xCC4A372C.toInt()
        canvas.drawCircle(cx - radius * 0.32f, cy - radius * 0.08f, radius * 0.08f, ink)
        canvas.drawCircle(cx + radius * 0.32f, cy - radius * 0.08f, radius * 0.08f, ink)
        canvas.drawCircle(cx, cy + radius * 0.18f, radius * 0.11f, ink)
    }
}
