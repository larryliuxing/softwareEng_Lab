package com.taplab.experiment.vision

import com.taplab.experiment.model.BranchConfig
import com.taplab.experiment.model.Frame
import kotlin.math.abs

object ColorMatcher {
    fun matches(frame: Frame, branch: BranchConfig): Boolean {
        val left = maxOf(branch.regionX, frame.originX, 0)
        val top = maxOf(branch.regionY, frame.originY, 0)
        val right = minOf(branch.regionX + branch.regionW, frame.originX + frame.bufferWidth, frame.width)
        val bottom = minOf(branch.regionY + branch.regionH, frame.originY + frame.bufferHeight, frame.height)
        if (left >= right || top >= bottom) return false
        val total = (right - left).toLong() * (bottom - top)
        val percent = branch.matchPercent.toLong()
        val targetRed = (branch.color shr 16) and 0xFF
        val targetGreen = (branch.color shr 8) and 0xFF
        val targetBlue = branch.color and 0xFF
        val tolerance = branch.tolerance
        var hits = 0L
        var seen = 0L
        for (y in top until bottom) {
            for (x in left until right) {
                seen++
                if (near(frame, x, y, targetRed, targetGreen, targetBlue, tolerance)) hits++
                if (hits * 100L >= percent * total) return true
                val remaining = total - seen
                if ((hits + remaining) * 100L < percent * total) return false
            }
        }
        return hits * 100L >= percent * total
    }

    fun colorAt(frame: Frame, x: Int, y: Int): Int? {
        val packed = packed(frame, x, y)
        return if (packed < 0) null else packed
    }

    fun matchRatio(frame: Frame, branch: BranchConfig): Double? {
        val left = maxOf(branch.regionX, frame.originX, 0)
        val top = maxOf(branch.regionY, frame.originY, 0)
        val right = minOf(branch.regionX + branch.regionW, frame.originX + frame.bufferWidth, frame.width)
        val bottom = minOf(branch.regionY + branch.regionH, frame.originY + frame.bufferHeight, frame.height)
        if (left >= right || top >= bottom) return null
        val targetRed = (branch.color shr 16) and 0xFF
        val targetGreen = (branch.color shr 8) and 0xFF
        val targetBlue = branch.color and 0xFF
        var hits = 0
        var total = 0
        for (y in top until bottom) {
            for (x in left until right) {
                total++
                if (near(frame, x, y, targetRed, targetGreen, targetBlue, branch.tolerance)) hits++
            }
        }
        if (total == 0) return null
        return hits.toDouble() / total.toDouble()
    }

    private fun near(
        frame: Frame,
        x: Int,
        y: Int,
        targetRed: Int,
        targetGreen: Int,
        targetBlue: Int,
        tolerance: Int,
    ): Boolean {
        val packed = packed(frame, x, y)
        if (packed < 0) return false
        return abs(((packed shr 16) and 0xFF) - targetRed) <= tolerance &&
            abs(((packed shr 8) and 0xFF) - targetGreen) <= tolerance &&
            abs((packed and 0xFF) - targetBlue) <= tolerance
    }

    private fun packed(frame: Frame, x: Int, y: Int): Int {
        val localX = x - frame.originX
        val localY = y - frame.originY
        if (localX !in 0 until frame.bufferWidth || localY !in 0 until frame.bufferHeight) return -1
        val bytesPerPixel = when (frame.format) {
            1, 2, 5 -> 4
            3 -> 3
            else -> return -1
        }
        val index = (localY * frame.bufferWidth + localX) * bytesPerPixel
        if (index < 0 || index + bytesPerPixel > frame.pixels.size) return -1
        val pixels = frame.pixels
        val red: Int
        val green: Int
        val blue: Int
        if (frame.format == 5) {
            blue = pixels[index].toInt() and 0xFF
            green = pixels[index + 1].toInt() and 0xFF
            red = pixels[index + 2].toInt() and 0xFF
        } else {
            red = pixels[index].toInt() and 0xFF
            green = pixels[index + 1].toInt() and 0xFF
            blue = pixels[index + 2].toInt() and 0xFF
        }
        return (red shl 16) or (green shl 8) or blue
    }
}
