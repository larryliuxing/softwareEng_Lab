package com.taplab.experiment.ui

import android.content.Context
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class BearBar(
    context: Context,
    private val onBear: () -> Unit,
    private val onStartPause: () -> Unit,
    private val onSettings: () -> Unit,
    private val onStop: () -> Unit,
    private val onExit: () -> Unit,
) : LinearLayout(context) {
    val bear = BearHeadView(context)
    private val buttons: LinearLayout
    private val startButton: android.widget.Button
    private val statusView: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        buttons = LinearLayout(context).apply {
            orientation = VERTICAL
            background = rounded(0xE61C1B19.toInt(), dp(12).toFloat())
            setPadding(dp(8), dp(8), dp(8), dp(8))
            visibility = GONE
        }
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        startButton = smallButton("开始", onStartPause)
        row.addView(startButton, buttonParams(gap = false))
        row.addView(smallButton("设置", onSettings), buttonParams(gap = true))
        row.addView(smallButton("停止", onStop), buttonParams(gap = true))
        row.addView(smallButton("退出", onExit), buttonParams(gap = true))
        buttons.addView(row)
        statusView = TextView(context).apply {
            asLabel(12f, 0xFFE7E1D6.toInt())
            setPadding(dp(4), dp(4), dp(4), 0)
            text = "未开始"
        }
        buttons.addView(statusView)
        addView(buttons)
        bear.isClickable = true
        addView(bear, LayoutParams(dp(64), dp(64)))
    }

    fun tapHead() {
        onBear()
    }

    fun setExpanded(expanded: Boolean) {
        buttons.visibility = if (expanded) VISIBLE else GONE
    }

    fun render(state: RunState) {
        startButton.text = if (state == RunState.RUNNING) "暂停" else "开始"
        bear.setRunning(state == RunState.RUNNING)
    }

    fun setStatus(text: String) {
        statusView.text = text
    }

    private fun smallButton(label: String, action: () -> Unit): Button {
        return context.textButton(label, 0xFF3A3834.toInt(), 0xFFF4F1EA.toInt()).apply {
            setOnClickListener { action() }
            minimumWidth = 0
            minWidth = 0
        }
    }

    private fun buttonParams(gap: Boolean): LayoutParams {
        return LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            if (gap) marginStart = dp(6)
        }
    }
}
