package com.taplab.experiment.vision

import com.taplab.experiment.model.defaultBranch
import com.taplab.experiment.model.BranchId
import com.taplab.experiment.rawScreen
import com.taplab.experiment.solidFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTests {
    @Test
    fun parsesRawHeaderAndPixels() {
        val pixels = ByteArray(2 * 2 * 4) { index -> (index + 1).toByte() }
        val frame = RawScreen.parse(rawScreen(2, 2, 1, pixels))
        assertEquals(2, frame?.width)
        assertEquals(2, frame?.height)
        assertEquals(1, frame?.format)
        assertEquals(16, frame?.pixels?.size)
    }

    @Test
    fun rejectsShortBuffer() {
        assertNull(RawScreen.parse(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun matchesTargetColorByRatio() {
        val frame = solidFrame(4, 2, 229, 57, 53)
        val branch = defaultBranch(BranchId.F1B1).copy(
            regionX = 0,
            regionY = 0,
            regionW = 4,
            regionH = 2,
            color = 0xE53935,
            tolerance = 0,
            matchPercent = 100,
        )
        assertTrue(ColorMatcher.matches(frame, branch))
        assertFalse(ColorMatcher.matches(frame, branch.copy(color = 0x0000FF, tolerance = 0)))
    }

    @Test
    fun partialRegionUsesOnlyTheVisiblePixels() {
        val frame = solidFrame(4, 1, 0, 0, 0)
        frame.pixels[0] = 255.toByte()
        frame.pixels[1] = 0
        frame.pixels[2] = 0
        val branch = defaultBranch(BranchId.F1B1).copy(
            regionX = 0,
            regionY = 0,
            regionW = 2,
            regionH = 1,
            color = 0xFF0000,
            tolerance = 0,
            matchPercent = 50,
        )
        assertTrue(ColorMatcher.matches(frame, branch))
        assertFalse(ColorMatcher.matches(frame, branch.copy(matchPercent = 51)))
    }

    @Test
    fun readsBgraPixels() {
        val frame = solidFrame(1, 1, 10, 20, 30, format = 5)
        assertEquals(0x0A141E, ColorMatcher.colorAt(frame, 0, 0))
    }

    @Test
    fun regionOutsideTheFrameDoesNotMatch() {
        val frame = solidFrame(2, 2, 255, 0, 0)
        val branch = defaultBranch(BranchId.F1B1).copy(regionX = 20, regionY = 20, regionW = 4, regionH = 4)
        assertFalse(ColorMatcher.matches(frame, branch))
    }
}
