package com.taplab.experiment.vision

import com.taplab.experiment.model.BranchConfig

data class ScanWindow(val x: Int, val y: Int, val width: Int, val height: Int)

object ScanWindows {
    fun ofBranch(branch: BranchConfig): ScanWindow {
        return ScanWindow(branch.regionX, branch.regionY, branch.regionW, branch.regionH)
    }

    fun clamp(window: ScanWindow, screenWidth: Int, screenHeight: Int): ScanWindow? {
        if (screenWidth <= 0 || screenHeight <= 0 || window.width <= 0 || window.height <= 0) return null
        val left = window.x.coerceAtLeast(0)
        val top = window.y.coerceAtLeast(0)
        val right = (window.x + window.width).coerceAtMost(screenWidth)
        val bottom = (window.y + window.height).coerceAtMost(screenHeight)
        if (left >= right || top >= bottom) return null
        return ScanWindow(left, top, right - left, bottom - top)
    }
}
