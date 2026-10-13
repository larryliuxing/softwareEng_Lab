package com.taplab.experiment.engine

import com.taplab.experiment.model.Frame
import com.taplab.experiment.vision.ScanWindow

/**
 * 一次截屏里可以按窗口取多块像素。
 * [ScreenCropper.crop] 返回的像素只保留到下一次 [ScreenCropper.crop] 或 [Capture.use] 返回之前。
 */
interface Capture {
    fun <T> use(block: (ScreenCropper) -> T): T
}

interface ScreenCropper {
    val screenWidth: Int
    val screenHeight: Int
    fun crop(window: ScanWindow): Frame
}
