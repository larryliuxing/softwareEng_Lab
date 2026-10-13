package com.taplab.experiment.engine

import com.taplab.experiment.model.BehaviorCodec
import com.taplab.experiment.model.BehaviorMode
import com.taplab.experiment.model.GrabSpec
import com.taplab.experiment.model.ScreenSpec
import com.taplab.experiment.model.Step
import com.taplab.experiment.solidFrame
import com.taplab.experiment.vision.ScanWindow
import com.taplab.experiment.vision.TemplateFile
import com.taplab.experiment.vision.TemplateImage
import com.taplab.experiment.vision.TemplateMatcher
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeRunnerTest {
    @Test
    fun squareStaysInside720x1080() {
        val square = ScreenSpec.clampSquare(2, 2, 48)
        assertEquals(24, square.centerX)
        assertEquals(24, square.centerY)
        assertEquals(48, square.side)
        assertEquals(ScanWindow(0, 0, 48, 48), ScreenSpec.toFrameWindow(square, 720, 1080))
    }

    @Test
    fun modeRoundTripKeepsNestedBranch() {
        val mode = BehaviorMode(
            id = "m1",
            name = "早晨",
            grabs = listOf(GrabSpec("g1", "按钮", 100, 200, 32)),
            steps = listOf(
                Step.Delay("d1", 150),
                Step.Compare(
                    id = "c1",
                    grabId = "g1",
                    tolerance = 12,
                    matchPercent = 80,
                    whenMatch = listOf(Step.Click("k1", 360, 540)),
                    whenMiss = listOf(Step.Delay("d2", 400)),
                ),
            ),
        )
        val restored = BehaviorCodec.decode(BehaviorCodec.encode(mode))
        assertEquals(mode.sanitized(), restored)
    }

    @Test
    fun templateFileRoundTripAndMatch() {
        val pixels = ByteArray(2 * 2 * 4)
        for (index in 0 until 4) {
            pixels[index * 4] = 10
            pixels[index * 4 + 1] = 20
            pixels[index * 4 + 2] = 30
            pixels[index * 4 + 3] = 255.toByte()
        }
        val image = TemplateImage(2, 2, 1, pixels)
        val decoded = TemplateFile.decode(TemplateFile.encode(image))
        assertEquals(2, decoded?.width)
        assertTrue(pixels.contentEquals(decoded?.pixels))
        val frame = solidFrame(2, 2, 10, 20, 30)
        assertTrue(TemplateMatcher.matches(frame, image, tolerance = 0, matchPercent = 100))
        val other = image.copy(pixels = pixels.copyOf().also { it[0] = 0 })
        assertFalse(TemplateMatcher.matches(frame, other, tolerance = 0, matchPercent = 100))
        assertTrue(TemplateMatcher.matches(frame, other, tolerance = 0, matchPercent = 75))
    }

    @Test
    fun clickThenDelayUsesLogicalPoint() {
        val taps = mutableListOf<Pair<Int, Int>>()
        val waits = mutableListOf<Long>()
        val runner = runner(
            mode = BehaviorMode(
                id = "m",
                name = "点",
                steps = listOf(Step.Click("c", 120, 340, sigmaX = 0.0, sigmaY = 0.0), Step.Delay("d", 200)),
            ),
            taps = taps,
            waits = waits,
        )
        assertEquals(StepResult.TAPPED, runner.runOnce())
        assertEquals(listOf(120 to 340), taps)
        assertEquals(listOf(200L), waits)
    }

    @Test
    fun compareChoosesTheMatchingBranch() {
        val taps = mutableListOf<Pair<Int, Int>>()
        val image = TemplateImage(2, 2, 1, ByteArray(16))
        val runner = runner(
            mode = compareMode(),
            taps = taps,
            frame = imageFrame(image),
            images = mapOf("g" to image),
        )
        runner.runOnce()
        assertEquals(listOf(10 to 10), taps)
    }

    @Test
    fun compareMissSkipsTheMatchBranch() {
        val taps = mutableListOf<Pair<Int, Int>>()
        val saved = TemplateImage(2, 2, 1, ByteArray(16).also { it[0] = 9 })
        val live = TemplateImage(2, 2, 1, ByteArray(16))
        val runner = runner(
            mode = compareMode(),
            taps = taps,
            frame = imageFrame(live),
            images = mapOf("g" to saved),
        )
        runner.runOnce()
        assertEquals(listOf(20 to 20), taps)
    }

    @Test
    fun pauseDuringDelayDoesNotRunTheNextClick() {
        val taps = mutableListOf<Int>()
        var active = true
        val runner = ModeRunner(
            capture = stillCapture(solidFrame(2, 2, 0, 0, 0)),
            tap = { x, _ -> taps += x },
            waiter = Waiter { _, still ->
                active = false
                still()
            },
            clock = { 0L },
            isActive = { active },
            onStatus = {},
            random = Random(1),
            modeSource = {
                BehaviorMode(
                    id = "m",
                    name = "停",
                    steps = listOf(Step.Delay("d", 100), Step.Click("c", 7, 7)),
                )
            },
            templates = { emptyMap() },
        )
        assertEquals(StepResult.CANCELLED, runner.runOnce())
        assertTrue(taps.isEmpty())
    }

    private fun runner(
        mode: BehaviorMode,
        taps: MutableList<Pair<Int, Int>> = mutableListOf(),
        waits: MutableList<Long> = mutableListOf(),
        frame: com.taplab.experiment.model.Frame = solidFrame(2, 2, 0, 0, 0),
        images: Map<String, TemplateImage> = emptyMap(),
    ): ModeRunner {
        var now = 0L
        return ModeRunner(
            capture = stillCapture(frame),
            tap = { x, y -> taps += x to y },
            waiter = Waiter { millis, still ->
                if (!still()) return@Waiter false
                waits += millis
                now += millis
                true
            },
            clock = { now },
            isActive = { true },
            onStatus = {},
            random = Random(1),
            modeSource = { mode },
            templates = { images },
        )
    }

    private fun compareMode(): BehaviorMode {
        return BehaviorMode(
            id = "m",
            name = "比",
            grabs = listOf(GrabSpec("g", "图", 40, 40, 16)),
            steps = listOf(
                Step.Compare(
                    id = "c",
                    grabId = "g",
                    tolerance = 0,
                    matchPercent = 100,
                    whenMatch = listOf(Step.Click("yes", 10, 10)),
                    whenMiss = listOf(Step.Click("no", 20, 20)),
                ),
            ),
        )
    }

    private fun imageFrame(image: TemplateImage): com.taplab.experiment.model.Frame {
        return com.taplab.experiment.model.Frame(
            width = ScreenSpec.WIDTH,
            height = ScreenSpec.HEIGHT,
            format = image.format,
            pixels = image.pixels,
            originX = 0,
            originY = 0,
            bufferWidth = image.width,
            bufferHeight = image.height,
        )
    }

    private fun stillCapture(frame: com.taplab.experiment.model.Frame): Capture {
        return object : Capture {
            override fun <T> use(block: (ScreenCropper) -> T): T {
                return block(object : ScreenCropper {
                    override val screenWidth = ScreenSpec.WIDTH
                    override val screenHeight = ScreenSpec.HEIGHT
                    override fun crop(window: ScanWindow) = frame
                })
            }
        }
    }
}
