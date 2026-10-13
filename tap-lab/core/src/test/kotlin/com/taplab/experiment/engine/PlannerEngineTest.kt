package com.taplab.experiment.engine

import com.taplab.experiment.model.BranchId
import com.taplab.experiment.model.ConfigCodec
import com.taplab.experiment.model.Distribution
import com.taplab.experiment.model.ExperimentConfig
import com.taplab.experiment.model.Frame
import com.taplab.experiment.model.TrialRecord
import com.taplab.experiment.solidFrame
import com.taplab.experiment.vision.ScanWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerEngineTest {
    @Test
    fun configRoundTripKeepsBranches() {
        val original = ExperimentConfig.default()
            .copy(distribution = Distribution.UNIFORM, seed = 99, scanIntervalMs = 80)
            .withBranch(
                BranchId.F2B2,
                ExperimentConfig.default().branch(BranchId.F2B2).copy(enabled = true, sigmaX = 1.5, color = 0x112233),
            )
        val restored = ConfigCodec.decode(ConfigCodec.encode(original))
        assertEquals(original.sanitized(), restored)
    }

    @Test
    fun firstEnabledMatchWins() {
        val frame = solidFrame(20, 20, 229, 57, 53)
        val config = enabledOn(frameColor = 0xE53935).withBranch(
            BranchId.F1B2,
            ExperimentConfig.default().branch(BranchId.F1B2).copy(
                enabled = true,
                regionX = 0,
                regionY = 0,
                regionW = 20,
                regionH = 20,
                color = 0xE53935,
                tolerance = 0,
                matchPercent = 50,
                aimX = 9,
                aimY = 9,
            ),
        )
        val matched = TrialPlanner.firstMatch(frame, config)
        assertEquals(BranchId.F1B1, matched?.id)
    }

    @Test
    fun skipsDisabledBranch() {
        val frame = solidFrame(8, 8, 0, 0, 0)
        assertNull(TrialPlanner.firstMatch(frame, ExperimentConfig.default()))
    }

    @Test
    fun stepTapsAimWhenSigmaIsZero() {
        val frame = solidFrame(30, 30, 229, 57, 53)
        val taps = mutableListOf<Pair<Int, Int>>()
        val trials = mutableListOf<TrialRecord>()
        var now = 5_000L
        val engine = engine(frame, enabledOn(0xE53935), taps, trials, clock = { now }) { millis, still ->
            if (!still()) return@engine false
            now += millis
            true
        }
        engine.reseedFromConfig()
        assertEquals(StepResult.TAPPED, engine.step())
        assertEquals(listOf(10 to 12), taps)
        assertEquals(200L, trials.single().plannedDelayMs)
        assertEquals(200L, trials.single().actualIntervalMs)
        assertEquals(10, trials.single().aimX)
        assertEquals(10, trials.single().tapX)
    }

    @Test
    fun pauseDuringDelayDoesNotTap() {
        val frame = solidFrame(30, 30, 229, 57, 53)
        val taps = mutableListOf<Pair<Int, Int>>()
        var active = true
        val engine = ExperimentEngine(
            capture = captureOf(frame),
            tap = { x, y -> taps += x to y },
            waiter = Waiter { _, still ->
                active = false
                still()
            },
            clock = { 0L },
            configSource = { enabledOn(0xE53935) },
            isActive = { active },
            onTrial = {},
            onStatus = {},
        )
        assertEquals(StepResult.CANCELLED, engine.step())
        assertTrue(taps.isEmpty())
    }

    @Test
    fun noMatchDoesNotTap() {
        val frame = solidFrame(30, 30, 0, 0, 255)
        val taps = mutableListOf<Pair<Int, Int>>()
        val engine = engine(frame, enabledOn(0xE53935), taps, mutableListOf(), clock = { 0L }) { _, _ -> true }
        assertEquals(StepResult.NO_MATCH, engine.step())
        assertTrue(taps.isEmpty())
    }

    @Test
    fun disabledBranchesDoNotCapture() {
        var captures = 0
        val engine = ExperimentEngine(
            capture = captureOf(solidFrame(4, 4, 0, 0, 0)) { captures++ },
            tap = { _, _ -> },
            waiter = { _, _ -> true },
            clock = { 0L },
            configSource = { ExperimentConfig.default() },
            isActive = { true },
            onTrial = {},
            onStatus = {},
        )
        assertEquals(StepResult.NO_MATCH, engine.step())
        assertEquals(0, captures)
    }

    @Test
    fun idleWaitFillsOnlyTheRemainderOfTheInterval() {
        var now = 1_000L
        val frame = solidFrame(30, 30, 0, 0, 255)
        val engine = ExperimentEngine(
            capture = object : Capture {
                override fun <T> use(block: (ScreenCropper) -> T): T {
                    now += 50
                    return block(staticCropper(frame))
                }
            },
            tap = { _, _ -> },
            waiter = Waiter { millis, _ ->
                now += millis
                true
            },
            clock = { now },
            configSource = { enabledOn(0xE53935).copy(scanIntervalMs = 200) },
            isActive = { true },
            onTrial = {},
            onStatus = {},
        )
        assertEquals(StepResult.NO_MATCH, engine.step())
        assertEquals(1_200L, now)
    }

    private fun enabledOn(frameColor: Int): ExperimentConfig {
        return ExperimentConfig.default()
            .copy(scanIntervalMs = 40, seed = 1)
            .withBranch(
                BranchId.F1B1,
                ExperimentConfig.default().branch(BranchId.F1B1).copy(
                    enabled = true,
                    regionX = 0,
                    regionY = 0,
                    regionW = 30,
                    regionH = 30,
                    color = frameColor,
                    tolerance = 0,
                    matchPercent = 50,
                    aimX = 10,
                    aimY = 12,
                    sigmaX = 0.0,
                    sigmaY = 0.0,
                    medianMs = 200,
                    dispersion = 0.0,
                    floorMs = 50,
                    uniformMaxMs = 400,
                ),
            )
    }

    private fun engine(
        frame: Frame,
        config: ExperimentConfig,
        taps: MutableList<Pair<Int, Int>>,
        trials: MutableList<TrialRecord>,
        clock: () -> Long,
        waiter: Waiter,
    ): ExperimentEngine {
        return ExperimentEngine(
            capture = captureOf(frame),
            tap = { x, y -> taps += x to y },
            waiter = waiter,
            clock = clock,
            configSource = { config },
            isActive = { true },
            onTrial = { trials += it },
            onStatus = {},
        )
    }

    private fun captureOf(frame: Frame, onUse: () -> Unit = {}): Capture {
        return object : Capture {
            override fun <T> use(block: (ScreenCropper) -> T): T {
                onUse()
                return block(staticCropper(frame))
            }
        }
    }

    private fun staticCropper(frame: Frame): ScreenCropper {
        return object : ScreenCropper {
            override val screenWidth = frame.width
            override val screenHeight = frame.height
            override fun crop(window: ScanWindow): Frame = frame
        }
    }
}
