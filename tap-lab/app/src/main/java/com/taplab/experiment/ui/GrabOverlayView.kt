package com.taplab.experiment.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.taplab.experiment.model.GrabSquare
import com.taplab.experiment.model.ScreenSpec
import kotlin.math.roundToInt

class GrabOverlayView(
    context: Context,
    private val onConfirm: (GrabSquare) -> Unit,
    private val onCancel: () -> Unit,
) : FrameLayout(context) {
    private var centerX = -1
    private var centerY = -1
    private var side = 64
    private val scrim = Paint().apply { color = 0x33000000 }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF4F1EA.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
    }
    private val hintView = TextView(context)

    init {
        setWillNotDraw(false)
        hintView.text = "切到要抓的画面，点一下中心，再拖动调整正方形"
        hintView.asLabel(15f, 0xFFF4F1EA.toInt())
        hintView.setPadding(dp(16), dp(16), dp(16), dp(8))
        addView(hintView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(0xF21C1B19.toInt(), dp(12).toFloat())
        }
        bar.addView(context.textButton("确定", 0xFFE7E1D6.toInt(), 0xFF1C1B19.toInt()).apply {
            setOnClickListener {
                if (centerX < 0) {
                    hintView.text = "先点一个中心"
                    return@setOnClickListener
                }
                onConfirm(ScreenSpec.clampSquare(centerX, centerY, side))
            }
        })
        bar.addView(context.textButton("取消", 0xFF3A3834.toInt(), 0xFFF4F1EA.toInt()).apply {
            setOnClickListener { onCancel() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(8)
        })
        addView(bar, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(24)
        })
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)
        if (centerX < 0 || width == 0 || height == 0) return
        val vx = centerX * width / ScreenSpec.WIDTH.toFloat()
        val vy = centerY * height / ScreenSpec.HEIGHT.toFloat()
        val half = side * width / ScreenSpec.WIDTH.toFloat() / 2f
        canvas.drawRect(vx - half, vy - half, vx + half, vy + half, stroke)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width == 0 || height == 0) return true
        val logicalX = (event.x / width * ScreenSpec.WIDTH).roundToInt()
        val logicalY = (event.y / height * ScreenSpec.HEIGHT).roundToInt()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (centerX < 0 || event.actionMasked == MotionEvent.ACTION_DOWN && !insideSquare(logicalX, logicalY)) {
                    if (centerX < 0) {
                        val square = ScreenSpec.clampSquare(logicalX, logicalY, side)
                        centerX = square.centerX
                        centerY = square.centerY
                        side = square.side
                    }
                }
                if (centerX >= 0 && event.actionMasked == MotionEvent.ACTION_MOVE) {
                    val dx = kotlin.math.abs(logicalX - centerX)
                    val dy = kotlin.math.abs(logicalY - centerY)
                    val reach = maxOf(dx, dy) * 2
                    if (reach > side / 3) {
                        val square = ScreenSpec.clampSquare(centerX, centerY, reach)
                        side = square.side
                        centerX = square.centerX
                        centerY = square.centerY
                    } else {
                        val square = ScreenSpec.clampSquare(logicalX, logicalY, side)
                        centerX = square.centerX
                        centerY = square.centerY
                    }
                }
                hintView.text = if (centerX < 0) "点一下中心" else "中心 $centerX, $centerY  边长 $side"
                invalidate()
            }
        }
        return true
    }

    private fun insideSquare(x: Int, y: Int): Boolean {
        val half = side / 2
        return x in (centerX - half)..(centerX + half) && y in (centerY - half)..(centerY + half)
    }
}
