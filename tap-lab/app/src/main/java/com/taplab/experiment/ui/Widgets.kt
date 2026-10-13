package com.taplab.experiment.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

fun android.view.View.dp(value: Int): Int = context.dp(value)

fun Context.screenWidth(): Int = resources.displayMetrics.widthPixels

fun Context.screenHeight(): Int = resources.displayMetrics.heightPixels

fun rounded(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable {
    return GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx
        if (strokePx > 0) setStroke(strokePx, strokeColor)
    }
}

fun TextView.asLabel(sizeSp: Float, color: Int) {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(color)
}

fun Context.field(decimal: Boolean = false, signed: Boolean = false): EditText {
    return EditText(this).apply {
        val flags = (if (decimal) android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL else 0) or
            (if (signed) android.text.InputType.TYPE_NUMBER_FLAG_SIGNED else 0)
        inputType = android.text.InputType.TYPE_CLASS_NUMBER or flags
        setSingleLine(true)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(0xFF1C1B19.toInt())
        setPadding(dp(8), dp(6), dp(8), dp(6))
        background = rounded(0xFFFFFFFF.toInt(), dp(6).toFloat(), 0xFFD5D0C8.toInt(), dp(1))
    }
}

fun Context.textButton(label: String, fill: Int, textColor: Int): Button {
    return Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        background = rounded(fill, dp(8).toFloat())
        setPadding(dp(10), dp(6), dp(10), dp(6))
        minimumHeight = dp(40)
    }
}

fun Context.row(vararg cells: Pair<String, android.view.View>): LinearLayout {
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val gap = dp(6)
        cells.forEachIndexed { index, (label, view) ->
            addView(TextView(context).apply {
                text = label
                asLabel(12f, 0xFF5E5A54.toInt())
            }, LinearLayout.LayoutParams(dp(52), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                if (index > 0) marginStart = gap
            })
            addView(view, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(4)
                topMargin = dp(4)
                bottomMargin = dp(4)
            })
        }
    }
}
