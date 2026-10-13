package com.taplab.experiment.human

import com.taplab.experiment.model.DelaySpec
import com.taplab.experiment.model.Distribution
import com.taplab.experiment.model.Limits
import java.util.Random
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToLong

object ReactionTime {
    private const val MAX_REJECTS = 32

    /**
     * 对数正态用中位反应时做中心，离散程度是对数域标准差。
     * 抽到生理下限以下就重抽，避免出现人做不到的间隔。
     * 均匀分布在下限和上限之间，用作对照。
     */
    fun sampleMillis(random: Random, spec: DelaySpec): Long {
        val floor = spec.floorMs.coerceAtLeast(0L)
        return when (spec.distribution) {
            Distribution.LOG_NORMAL -> sampleLogNormal(random, spec, floor)
            Distribution.UNIFORM -> {
                val high = spec.uniformMaxMs.coerceAtLeast(floor).toDouble()
                val low = floor.toDouble()
                val drawn = low + random.nextDouble() * (high - low)
                drawn.roundToLong().coerceIn(floor, spec.uniformMaxMs.coerceAtLeast(floor))
            }
        }
    }

    private fun sampleLogNormal(random: Random, spec: DelaySpec, floor: Long): Long {
        val median = spec.medianMs.coerceAtLeast(1L).toDouble()
        val sigma = spec.dispersion.coerceAtLeast(0.0)
        val mu = ln(median)
        repeat(MAX_REJECTS) {
            val drawn = exp(mu + sigma * random.nextGaussian()).roundToLong()
            if (drawn >= floor) return drawn.coerceAtMost(Limits.MAX_DELAY_MS)
        }
        return floor
    }
}
