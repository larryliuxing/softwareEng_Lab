package com.taplab.experiment.model

import java.util.Locale

enum class Distribution {
    LOG_NORMAL,
    UNIFORM,
}

enum class BranchId(val functionName: String, val branchName: String, val key: String) {
    F1B1("一号功能", "一号分支", "f1b1"),
    F1B2("一号功能", "二号分支", "f1b2"),
    F2B1("二号功能", "一号分支", "f2b1"),
    F2B2("二号功能", "二号分支", "f2b2"),
}

object Limits {
    const val MAX_DELAY_MS = 60_000L
    const val MIN_SCAN_MS = 80L
    const val MAX_SCAN_MS = 5_000L
    /** 单次从截屏里读进内存的上限。8 核、4GB 内存的环境装不下一整屏的反复分配。 */
    const val MAX_FRAME_BYTES = 16_000_000
    const val MAX_DISPERSION = 2.0
    const val MAX_SIGMA_PX = 200.0
    const val MAX_PERCENT = 100
}

data class BranchConfig(
    val enabled: Boolean,
    val regionX: Int,
    val regionY: Int,
    val regionW: Int,
    val regionH: Int,
    /** 0xRRGGBB */
    val color: Int,
    val tolerance: Int,
    /** 区域内有多少比例的像素落在容差内才算命中，1..100。 */
    val matchPercent: Int,
    val aimX: Int,
    val aimY: Int,
    val medianMs: Long,
    val dispersion: Double,
    val floorMs: Long,
    val uniformMaxMs: Long,
    val sigmaX: Double,
    val sigmaY: Double,
) {
    fun sanitized(): BranchConfig {
        val floor = floorMs.coerceIn(0L, Limits.MAX_DELAY_MS)
        val median = medianMs.coerceIn(1L, Limits.MAX_DELAY_MS).coerceAtLeast(floor.coerceAtLeast(1L))
        val uniformMax = uniformMaxMs.coerceIn(floor, Limits.MAX_DELAY_MS)
        return copy(
            regionW = regionW.coerceAtLeast(1),
            regionH = regionH.coerceAtLeast(1),
            color = color and 0xFFFFFF,
            tolerance = tolerance.coerceIn(0, 255),
            matchPercent = matchPercent.coerceIn(1, Limits.MAX_PERCENT),
            medianMs = median,
            dispersion = dispersion.coerceIn(0.0, Limits.MAX_DISPERSION),
            floorMs = floor,
            uniformMaxMs = uniformMax,
            sigmaX = sigmaX.coerceIn(0.0, Limits.MAX_SIGMA_PX),
            sigmaY = sigmaY.coerceIn(0.0, Limits.MAX_SIGMA_PX),
        )
    }

    fun colorHex(): String = String.format(Locale.US, "%06X", color and 0xFFFFFF)
}

data class ExperimentConfig(
    val branches: Map<BranchId, BranchConfig>,
    val distribution: Distribution,
    val seed: Long,
    val scanIntervalMs: Long,
) {
    fun branch(id: BranchId): BranchConfig = branches.getValue(id)

    fun withBranch(id: BranchId, branch: BranchConfig): ExperimentConfig {
        return copy(branches = branches + (id to branch))
    }

    fun sanitized(): ExperimentConfig {
        return copy(
            branches = BranchId.entries.associateWith { id ->
                (branches[id] ?: defaultBranch(id)).sanitized()
            },
            scanIntervalMs = scanIntervalMs.coerceIn(Limits.MIN_SCAN_MS, Limits.MAX_SCAN_MS),
        )
    }

    companion object {
        fun default(): ExperimentConfig {
            return ExperimentConfig(
                branches = BranchId.entries.associateWith { defaultBranch(it) },
                distribution = Distribution.LOG_NORMAL,
                seed = 1L,
                scanIntervalMs = 200L,
            )
        }
    }
}

fun defaultBranch(id: BranchId): BranchConfig {
    val column = if (id == BranchId.F1B1 || id == BranchId.F1B2) 80 else 360
    val row = if (id == BranchId.F1B1 || id == BranchId.F2B1) 180 else 420
    return BranchConfig(
        enabled = false,
        regionX = column,
        regionY = row,
        regionW = 48,
        regionH = 48,
        color = 0xE53935,
        tolerance = 28,
        matchPercent = 40,
        aimX = column + 24,
        aimY = row + 24,
        medianMs = 250,
        dispersion = 0.22,
        floorMs = 150,
        uniformMaxMs = 480,
        sigmaX = 3.0,
        sigmaY = 3.5,
    )
}

data class MatchedBranch(val id: BranchId, val branch: BranchConfig)

data class PlannedTap(
    val id: BranchId,
    val delayMs: Long,
    val aimX: Int,
    val aimY: Int,
    val tapX: Int,
    val tapY: Int,
)

data class TrialRecord(
    val stimulusEpochMs: Long,
    val functionName: String,
    val branchName: String,
    val distribution: String,
    val plannedDelayMs: Long,
    val actualIntervalMs: Long,
    val aimX: Int,
    val aimY: Int,
    val tapX: Int,
    val tapY: Int,
)

data class Frame(
    val width: Int,
    val height: Int,
    val format: Int,
    val pixels: ByteArray,
    val originX: Int = 0,
    val originY: Int = 0,
    val bufferWidth: Int = width,
    val bufferHeight: Int = height,
)

fun parseColor(text: String): Int? {
    val hex = text.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
    if (hex.length != 6) return null
    return hex.toIntOrNull(16)
}
