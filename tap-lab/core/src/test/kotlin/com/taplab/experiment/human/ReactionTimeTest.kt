package com.taplab.experiment.human

import com.taplab.experiment.model.DelaySpec
import com.taplab.experiment.model.Distribution
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionTimeTest {
    @Test
    fun logNormalStaysAboveFloorAndNearMedian() {
        val spec = DelaySpec(Distribution.LOG_NORMAL, medianMs = 250, dispersion = 0.22, floorMs = 150, uniformMaxMs = 480)
        val random = Random(9)
        val samples = List(4_000) { ReactionTime.sampleMillis(random, spec) }
        assertTrue(samples.all { it >= 150 })
        val median = samples.sorted()[samples.size / 2]
        assertTrue("median=$median", median in 220..290)
    }

    @Test
    fun zeroDispersionIsTheMedian() {
        val spec = DelaySpec(Distribution.LOG_NORMAL, medianMs = 250, dispersion = 0.0, floorMs = 150, uniformMaxMs = 480)
        val sample = ReactionTime.sampleMillis(Random(1), spec)
        assertEquals(250L, sample)
    }

    @Test
    fun uniformStaysInsideBounds() {
        val spec = DelaySpec(Distribution.UNIFORM, medianMs = 250, dispersion = 0.2, floorMs = 120, uniformMaxMs = 400)
        val random = Random(3)
        val samples = List(1_000) { ReactionTime.sampleMillis(random, spec) }
        assertTrue(samples.all { it in 120..400 })
    }

    @Test
    fun sameSeedRepeats() {
        val spec = DelaySpec(Distribution.LOG_NORMAL, medianMs = 260, dispersion = 0.3, floorMs = 140, uniformMaxMs = 500)
        val firstRandom = Random(42)
        val secondRandom = Random(42)
        val first = List(20) { ReactionTime.sampleMillis(firstRandom, spec) }
        val second = List(20) { ReactionTime.sampleMillis(secondRandom, spec) }
        assertEquals(first, second)
        assertTrue(first.distinct().size > 1)
    }
}
