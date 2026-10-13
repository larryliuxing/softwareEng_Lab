package com.taplab.experiment.ui

import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class ControlBarView(
    context: Context,
    private val onStartPause: () -> Unit,
    private val onSettings: () -> Unit,
    private val onStop: () -> Unit,
    private val onExit: () -> Unit,
) : LinearLayout(context) {
    val dragHandle: TextView
    private val startButton: Button
    private val statusView: TextView

    init {
        orientation = VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = rounded(0xF21C1B19.toInt(), dp(12).toFloat())

        val buttons = LinearLayout(context).apply { orientation = HORIZONTAL }
        dragHandle = TextView(context).apply {
            text = "拖动"
            asLabel(12f, 0xFFB7B2A8.toInt())
            setPadding(dp(6), dp(8), dp(8), dp(8))
        }
        startButton = barButton("开始", 0xFFE7E1D6.toInt(), 0xFF1C1B19.toInt(), onStartPause)
        val settings = barButton("设置", 0xFF3A3834.toInt(), 0xFFF4F1EA.toInt(), onSettings)
        val stop = barButton("停止", 0xFF3A3834.toInt(), 0xFFF4F1EA.toInt(), onStop)
        val exit = barButton("退出", 0xFF6E2E2A.toInt(), 0xFFF8F4F2.toInt(), onExit)
        buttons.addView(dragHandle)
        listOf(startButton, settings, stop, exit).forEach { button ->
            buttons.addView(button, LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)).apply {
                marginStart = dp(6)
            })
        }
        addView(buttons)
        statusView = TextView(context).apply {
            text = "未开始"
            asLabel(12f, 0xFFD9D3C8.toInt())
            setPadding(dp(6), dp(6), dp(4), 0)
        }
        addView(statusView)
    }

    fun render(state: RunState) {
        startButton.text = if (state == RunState.RUNNING) "暂停" else "开始"
    }

    fun setStatus(text: String) {
        statusView.text = text
    }

    private fun barButton(label: String, fill: Int, textColor: Int, action: () -> Unit): Button {
        return context.textButton(label, fill, textColor).apply {
            setOnClickListener { action() }
            minimumWidth = 0
            minWidth = 0
        }
    }
}
