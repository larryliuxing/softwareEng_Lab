package com.taplab.experiment.model

import com.taplab.experiment.vision.ScanWindow

object ScreenSpec {
    const val WIDTH = 720
    const val HEIGHT = 1080
    const val MIN_SIDE = 8
    const val MAX_SIDE = 320

    fun clampSquare(centerX: Int, centerY: Int, side: Int): GrabSquare {
        val size = side.coerceIn(MIN_SIDE, MAX_SIDE)
        val half = size / 2
        val x = centerX.coerceIn(half, WIDTH - (size - half))
        val y = centerY.coerceIn(half, HEIGHT - (size - half))
        return GrabSquare(x, y, size)
    }

    /** 把 720×1080 上的正方形换成当前截屏上的像素窗口。分辨率正好是 720×1080 时原样返回。 */
    fun toFrameWindow(square: GrabSquare, screenWidth: Int, screenHeight: Int): ScanWindow {
        val width = screenWidth.coerceAtLeast(1)
        val height = screenHeight.coerceAtLeast(1)
        val left = square.left.toLong() * width / WIDTH
        val top = square.top.toLong() * height / HEIGHT
        val right = (square.left + square.side).toLong() * width / WIDTH
        val bottom = (square.top + square.side).toLong() * height / HEIGHT
        val w = (right - left).toInt().coerceAtLeast(1)
        val h = (bottom - top).toInt().coerceAtLeast(1)
        return ScanWindow(left.toInt(), top.toInt(), w, h)
    }

    fun scaleX(logicalX: Int, screenWidth: Int): Int {
        return (logicalX.toLong() * screenWidth.coerceAtLeast(1) / WIDTH).toInt()
            .coerceIn(0, screenWidth.coerceAtLeast(1) - 1)
    }

    fun scaleY(logicalY: Int, screenHeight: Int): Int {
        return (logicalY.toLong() * screenHeight.coerceAtLeast(1) / HEIGHT).toInt()
            .coerceIn(0, screenHeight.coerceAtLeast(1) - 1)
    }
}

data class GrabSquare(val centerX: Int, val centerY: Int, val side: Int) {
    val left: Int get() = centerX - side / 2
    val top: Int get() = centerY - side / 2
}
