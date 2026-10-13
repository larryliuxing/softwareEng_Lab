package com.taplab.experiment.human

import java.util.Random
import kotlin.math.roundToInt

object AimPoint {
    /** 瞄准点加上独立的横向、纵向高斯噪声，再收到屏幕范围内。 */
    fun sample(
        random: Random,
        aimX: Int,
        aimY: Int,
        sigmaX: Double,
        sigmaY: Double,
        screenWidth: Int,
        screenHeight: Int,
    ): Pair<Int, Int> {
        val width = screenWidth.coerceAtLeast(1)
        val height = screenHeight.coerceAtLeast(1)
        val x = (aimX + random.nextGaussian() * sigmaX.coerceAtLeast(0.0)).roundToInt()
        val y = (aimY + random.nextGaussian() * sigmaY.coerceAtLeast(0.0)).roundToInt()
        return x.coerceIn(0, width - 1) to y.coerceIn(0, height - 1)
    }
}
