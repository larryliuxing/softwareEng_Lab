package com.taplab.experiment.model

import com.taplab.experiment.solidFrame
import com.taplab.experiment.vision.RgbaImage
import com.taplab.experiment.vision.TemplateImage
import com.taplab.experiment.vision.TemplateMatcher
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModePackageTest {
    @Test
    fun packageRoundTripKeepsTemplateBytes() {
        val directory = File.createTempFile("mode", "dir")
        directory.delete()
        directory.mkdirs()
        val pixels = ByteArray(8) { index -> (index + 1).toByte() }
        val bundle = ModeBundle(
            mode = BehaviorMode(
                id = "mode-1",
                name = "早晨",
                grabs = listOf(GrabSpec("grab-1", "按钮", 100, 200, 32)),
                steps = listOf(Step.Compare(id = "cmp-1", grabId = "grab-1", whenMatch = listOf(Step.Click("clk-1", 10, 20)))),
            ),
            templates = mapOf("grab-1" to TemplateImage(2, 1, 1, pixels)),
        )
        ModePackage.write(directory, bundle)
        val restored = ModePackage.read(directory)
        assertEquals(bundle.mode.sanitized(), restored.mode)
        assertTrue(pixels.contentEquals(restored.templates.getValue("grab-1").pixels))
        directory.deleteRecursively()
    }

    @Test
    fun cropOn720x1080MatchesAbgraFrame() {
        val width = ScreenSpec.WIDTH
        val height = ScreenSpec.HEIGHT
        val pixels = ByteArray(width * height * 4)
        val square = ScreenSpec.clampSquare(100, 200, 16)
        val window = ScreenSpec.toFrameWindow(square, width, height)
        for (y in window.y until window.y + window.height) {
            for (x in window.x until window.x + window.width) {
                val index = (y * width + x) * 4
                pixels[index] = 200.toByte()
                pixels[index + 1] = 10
                pixels[index + 2] = 20
                pixels[index + 3] = 255.toByte()
            }
        }
        val image = RgbaImage.crop(width, height, pixels, square)
        assertEquals(window.width, image?.width)
        assertEquals(RgbaImage.FORMAT, image?.format)
        val frame = solidFrame(image!!.width, image.height, 200, 10, 20, format = 5)
        assertTrue(TemplateMatcher.matches(frame, image, tolerance = 0, matchPercent = 100))
    }

    @Test
    fun dragGrowsTheSquareWhenThePointerMovesAway() {
        val started = GrabGesture.begin(360, 540, 64)
        val grown = GrabGesture.drag(started, 420, 540)
        assertTrue(grown.side > started.side)
        assertEquals(360, grown.centerX)
    }
}
