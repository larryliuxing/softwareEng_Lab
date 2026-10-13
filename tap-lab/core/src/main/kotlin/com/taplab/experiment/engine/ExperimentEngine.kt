package com.taplab.experiment.engine

import com.taplab.experiment.model.BranchId
import com.taplab.experiment.model.ExperimentConfig
import com.taplab.experiment.model.MatchedBranch
import com.taplab.experiment.model.PlannedTap
import com.taplab.experiment.model.TrialRecord
import com.taplab.experiment.vision.ColorMatcher
import com.taplab.experiment.vision.ScanWindows
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
    private val capture: Capture,
    private val tap: (Int, Int) -> Unit,
    private val waiter: Waiter,
    private val clock: () -> Long,
    private val configSource: () -> ExperimentConfig,
    private val isActive: () -> Boolean,
    private val onTrial: (TrialRecord) -> Unit,
    private val onStatus: (String) -> Unit,
) {
    private var random: Random? = null
    private var seenConfig: ExperimentConfig? = null
    private var cleanConfig: ExperimentConfig? = null

    fun reseedFromConfig() {
        random = Random(config().seed)
    }

    fun step(): StepResult {
        val config = config()
        if (BranchId.entries.none { config.branch(it).enabled }) {
            onStatus("没有勾选分支")
            if (!waiter.waitMs(config.scanIntervalMs, isActive)) return StepResult.CANCELLED
            return StepResult.NO_MATCH
        }
        val started = clock()
        val planned = capture.use { cropper ->
            var plannedTap: PlannedTap? = null
            for (id in BranchId.entries) {
                val branch = config.branch(id)
                if (!branch.enabled) continue
                val frame = cropper.crop(ScanWindows.ofBranch(branch))
                if (!ColorMatcher.matches(frame, branch)) continue
                plannedTap = TrialPlanner.plan(rng(config), frame, config, MatchedBranch(id, branch))
                break
            }
            plannedTap
        }
        if (planned == null) {
            val rest = config.scanIntervalMs - (clock() - started)
            if (rest > 0 && !waiter.waitMs(rest, isActive)) return StepResult.CANCELLED
            onStatus("未命中")
            return StepResult.NO_MATCH
        }
        onStatus("等待 ${planned.delayMs} ms · ${planned.id.functionName}")
        val stimulus = clock()
        if (!waiter.waitMs(planned.delayMs, isActive)) return StepResult.CANCELLED
        val issued = clock()
        tap(planned.tapX, planned.tapY)
        onTrial(
            TrialRecord(
                stimulusEpochMs = stimulus,
                functionName = planned.id.functionName,
                branchName = planned.id.branchName,
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

    private fun config(): ExperimentConfig {
        val source = configSource()
        val cached = cleanConfig
        if (source === seenConfig && cached != null) return cached
        val clean = source.sanitized()
        seenConfig = source
        cleanConfig = clean
        return clean
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
