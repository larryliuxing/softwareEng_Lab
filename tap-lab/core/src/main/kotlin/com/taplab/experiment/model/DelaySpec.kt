package com.taplab.experiment.model

data class DelaySpec(
    val distribution: Distribution,
    val medianMs: Long,
    val dispersion: Double,
    val floorMs: Long,
    val uniformMaxMs: Long,
)
