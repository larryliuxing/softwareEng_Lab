package com.taplab.experiment.vision

import com.taplab.experiment.model.BranchConfig
import com.taplab.experiment.model.Frame
import kotlin.math.abs

object ColorMatcher {
    fun matches(frame: Frame, branch: BranchConfig): Boolean {
        val ratio = matchRatio(frame, branch) ?: return false
        return ratio * 100.0 >= branch.matchPercent
    }

    fun colorAt(frame: Frame, x: Int, y: Int): Int? {
        val (red, green, blue) = channels(frame, x, y) ?: return null
        return (red shl 16) or (green shl 8) or blue
    }

    fun matchRatio(frame: Frame, branch: BranchConfig): Double? {
        val x0 = maxOf(branch.regionX, 0)
        val y0 = maxOf(branch.regionY, 0)
        val x1 = minOf(branch.regionX + branch.regionW, frame.width)
        val y1 = minOf(branch.regionY + branch.regionH, frame.height)
        if (x0 >= x1 || y0 >= y1) return null
        val targetRed = (branch.color shr 16) and 0xFF
        val targetGreen = (branch.color shr 8) and 0xFF
        val targetBlue = branch.color and 0xFF
        val tolerance = branch.tolerance
        var hits = 0
        var total = 0
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val (red, green, blue) = channels(frame, x, y) ?: continue
                total++
                if (
                    abs(red - targetRed) <= tolerance &&
                    abs(green - targetGreen) <= tolerance &&
                    abs(blue - targetBlue) <= tolerance
                ) {
                    hits++
                }
            }
        }
        if (total == 0) return null
        return hits.toDouble() / total.toDouble()
    }

    private fun channels(frame: Frame, x: Int, y: Int): Triple<Int, Int, Int>? {
        if (x !in 0 until frame.width || y !in 0 until frame.height) return null
        val bytesPerPixel = when (frame.format) {
            1, 2, 5 -> 4
            3 -> 3
            else -> return null
        }
        val index = (y * frame.width + x) * bytesPerPixel
        if (index + bytesPerPixel > frame.pixels.size) return null
        val pixels = frame.pixels
        return when (frame.format) {
            5 -> Triple(
                pixels[index + 2].toInt() and 0xFF,
                pixels[index + 1].toInt() and 0xFF,
                pixels[index].toInt() and 0xFF,
            )
            else -> Triple(
                pixels[index].toInt() and 0xFF,
                pixels[index + 1].toInt() and 0xFF,
                pixels[index + 2].toInt() and 0xFF,
            )
        }
    }
}
