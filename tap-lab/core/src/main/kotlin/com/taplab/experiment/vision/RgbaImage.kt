package com.taplab.experiment.vision

import com.taplab.experiment.model.GrabSquare
import com.taplab.experiment.model.ScreenSpec

object RgbaImage {
    const val FORMAT = 1

    /** 从一张已经换成 720×1080 的 RGBA 画面上，按逻辑正方形裁出模板。 */
    fun crop(width: Int, height: Int, pixels: ByteArray, square: GrabSquare): TemplateImage? {
        if (width <= 0 || height <= 0 || pixels.size < width.toLong() * height * 4) return null
        val window = ScreenSpec.toFrameWindow(square, width, height)
        if (window.width !in 1..ScreenSpec.MAX_SIDE || window.height !in 1..ScreenSpec.MAX_SIDE) return null
        if (window.x < 0 || window.y < 0 || window.x + window.width > width || window.y + window.height > height) return null
        val out = ByteArray(window.width * window.height * 4)
        val rowBytes = window.width * 4
        for (row in 0 until window.height) {
            val source = ((window.y + row) * width + window.x) * 4
            System.arraycopy(pixels, source, out, row * rowBytes, rowBytes)
        }
        return TemplateImage(window.width, window.height, FORMAT, out)
    }
}
