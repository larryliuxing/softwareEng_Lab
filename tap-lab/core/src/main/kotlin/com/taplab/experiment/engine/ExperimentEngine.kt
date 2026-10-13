package com.taplab.experiment.engine

import com.taplab.experiment.model.ExperimentConfig
import com.taplab.experiment.model.Frame
import com.taplab.experiment.model.TrialRecord
import java.util.Random

fun interface Waiter {
    fun waitMs(totalMs: Long, stillActive: () -> Boolean): Boolean
}

enum class StepResult {
    NO_MATCH,
    TAPPED,
    CANCELLED,
}

class ExperimentEngine(
    private val capture: () -> Frame,
    private val tap: (Int, Int) -> Unit,
    private val waiter: Waiter,
    private val clock: () -> Long,
    private val configSource: () -> ExperimentConfig,
    private val isActive: () -> Boolean,
    private val onTrial: (TrialRecord) -> Unit,
    private val onStatus: (String) -> Unit,
) {
    private var random: Random? = null

    fun reseedFromConfig() {
        random = Random(configSource().seed)
    }

    fun step(): StepResult {
        val config = configSource().sanitized()
        onStatus("扫描")
        val frame = capture()
        val matched = TrialPlanner.firstMatch(frame, config) ?: run {
            if (!waiter.waitMs(config.scanIntervalMs, isActive)) return StepResult.CANCELLED
            onStatus("未命中")
            return StepResult.NO_MATCH
        }
        val planned = TrialPlanner.plan(rng(config), frame, config, matched)
        onStatus("等待 ${planned.delayMs} ms · ${matched.id.functionName}")
        val stimulus = clock()
        if (!waiter.waitMs(planned.delayMs, isActive)) return StepResult.CANCELLED
        val issued = clock()
        tap(planned.tapX, planned.tapY)
        onTrial(
            TrialRecord(
                stimulusEpochMs = stimulus,
                functionName = matched.id.functionName,
                branchName = matched.id.branchName,
                distribution = config.distribution.name,
                plannedDelayMs = planned.delayMs,
                actualIntervalMs = issued - stimulus,
                aimX = planned.aimX,
                aimY = planned.aimY,
                tapX = planned.tapX,
                tapY = planned.tapY,
            ),
        )
        onStatus("点击 ${planned.tapX}, ${planned.tapY}")
        waiter.waitMs(config.scanIntervalMs, isActive)
        return StepResult.TAPPED
    }

    private fun rng(config: ExperimentConfig): Random {
        val existing = random
        if (existing != null) return existing
        val created = Random(config.seed)
        random = created
        return created
    }
}

object ThreadWaiter : Waiter {
    override fun waitMs(totalMs: Long, stillActive: () -> Boolean): Boolean {
        var left = totalMs
        while (left > 0) {
            if (!stillActive()) return false
            val slice = minOf(left, 50L)
            try {
                Thread.sleep(slice)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
            left -= slice
        }
        return stillActive()
    }
}
