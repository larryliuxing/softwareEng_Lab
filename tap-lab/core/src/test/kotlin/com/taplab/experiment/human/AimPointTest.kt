package com.taplab.experiment.human

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AimPointTest {
    @Test
    fun zeroSigmaHitsTheAimPixel() {
        val point = AimPoint.sample(Random(1), 40, 70, 0.0, 0.0, 200, 400)
        assertEquals(40 to 70, point)
    }

    @Test
    fun scatterStaysOnScreen() {
        val random = Random(5)
        val points = List(500) { AimPoint.sample(random, 2, 2, 30.0, 40.0, 80, 90) }
        assertTrue(points.all { it.first in 0..79 && it.second in 0..89 })
        assertTrue(points.any { it != 2 to 2 })
    }

    @Test
    fun sameSeedRepeats() {
        val firstRandom = Random(8)
        val secondRandom = Random(8)
        val first = List(15) { AimPoint.sample(firstRandom, 100, 100, 4.0, 6.0, 300, 300) }
        val second = List(15) { AimPoint.sample(secondRandom, 100, 100, 4.0, 6.0, 300, 300) }
        assertEquals(first, second)
        assertTrue(first.distinct().size > 1)
    }
}
