package com.taplab.experiment.model

data class GrabSpec(
    val id: String,
    val name: String,
    val centerX: Int,
    val centerY: Int,
    val side: Int,
)

sealed class Step {
    abstract val id: String

    data class Click(
        override val id: String,
        val x: Int,
        val y: Int,
        val sigmaX: Double = 0.0,
        val sigmaY: Double = 0.0,
    ) : Step()

    data class Delay(
        override val id: String,
        val millis: Long,
    ) : Step()

    data class Compare(
        override val id: String,
        val grabId: String,
        val tolerance: Int = 18,
        val matchPercent: Int = 90,
        val whenMatch: List<Step> = emptyList(),
        val whenMiss: List<Step> = emptyList(),
    ) : Step()
}

data class BehaviorMode(
    val id: String,
    val name: String,
    val grabs: List<GrabSpec> = emptyList(),
    val steps: List<Step> = emptyList(),
) {
    fun sanitized(): BehaviorMode {
        return copy(
            name = name.trim().ifBlank { "未命名" }.take(40),
            grabs = grabs.map { it.sanitized() },
            steps = steps.map { it.sanitized() },
        )
    }
}

fun GrabSpec.sanitized(): GrabSpec {
    val square = ScreenSpec.clampSquare(centerX, centerY, side)
    return copy(
        name = name.trim().ifBlank { "抓取" }.take(40),
        centerX = square.centerX,
        centerY = square.centerY,
        side = square.side,
    )
}

fun GrabSpec.square(): GrabSquare = GrabSquare(centerX, centerY, side)

fun Step.sanitized(): Step {
    return when (this) {
        is Step.Click -> copy(
            x = x.coerceIn(0, ScreenSpec.WIDTH - 1),
            y = y.coerceIn(0, ScreenSpec.HEIGHT - 1),
            sigmaX = sigmaX.coerceIn(0.0, 80.0),
            sigmaY = sigmaY.coerceIn(0.0, 80.0),
        )
        is Step.Delay -> copy(millis = millis.coerceIn(0L, Limits.MAX_DELAY_MS))
        is Step.Compare -> copy(
            tolerance = tolerance.coerceIn(0, 255),
            matchPercent = matchPercent.coerceIn(1, 100),
            whenMatch = whenMatch.map { it.sanitized() },
            whenMiss = whenMiss.map { it.sanitized() },
        )
    }
}
