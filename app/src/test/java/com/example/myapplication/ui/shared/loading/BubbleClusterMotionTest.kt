package com.example.myapplication.ui.shared.loading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BubbleClusterMotionTest {

    private val epsilon = 0.015f

    @Test
    fun cycleClosesWithoutAJump() {
        assertFramesEqual(
            BubbleClusterMotion.frame(0f),
            BubbleClusterMotion.frame(1f),
        )
    }

    @Test
    fun satelliteAlternatesBetweenBothEdges() {
        val right = BubbleClusterMotion.frame(0f)
        val left = BubbleClusterMotion.frame(0.5f)

        assertTrue(right.satelliteX > 2f)
        assertTrue(left.satelliteX < -2f)
        assertTrue(right.satelliteAlpha > 0.95f)
        assertTrue(left.satelliteAlpha > 0.95f)
    }

    @Test
    fun satelliteDisappearsInsideTheCluster() {
        assertTrue(BubbleClusterMotion.frame(0.25f).satelliteAlpha < 0.05f)
        assertTrue(BubbleClusterMotion.frame(0.75f).satelliteAlpha < 0.05f)
    }

    @Test
    fun compressionWaveFollowsTravelDirection() {
        val travellingLeftEarly = BubbleClusterMotion.frame(0.17f).bubbles
        val travellingLeftLate = BubbleClusterMotion.frame(0.33f).bubbles
        assertTrue(travellingLeftEarly[2].scaleX < travellingLeftEarly[0].scaleX)
        assertTrue(travellingLeftLate[0].scaleX < travellingLeftLate[2].scaleX)

        val travellingRightEarly = BubbleClusterMotion.frame(0.67f).bubbles
        val travellingRightLate = BubbleClusterMotion.frame(0.83f).bubbles
        assertTrue(travellingRightEarly[0].scaleX < travellingRightEarly[2].scaleX)
        assertTrue(travellingRightLate[2].scaleX < travellingRightLate[0].scaleX)
    }

    @Test
    fun allFramesStayFiniteAndKeepPositiveBubbleSizes() {
        for (step in -100..300) {
            val frame = BubbleClusterMotion.frame(step / 200f)
            assertTrue(frame.satelliteX.isFinite())
            assertTrue(frame.satelliteY.isFinite())
            assertTrue(frame.satelliteAlpha in 0f..1f)
            frame.bubbles.forEach { bubble ->
                assertTrue(bubble.x.isFinite())
                assertTrue(bubble.y.isFinite())
                assertTrue(bubble.scaleX > 0f && bubble.scaleX.isFinite())
                assertTrue(bubble.scaleY > 0f && bubble.scaleY.isFinite())
            }
        }
    }

    private fun assertFramesEqual(
        expected: BubbleClusterFrame,
        actual: BubbleClusterFrame,
    ) {
        assertEquals(expected.satelliteX, actual.satelliteX, epsilon)
        assertEquals(expected.satelliteY, actual.satelliteY, epsilon)
        assertEquals(expected.satelliteAlpha, actual.satelliteAlpha, epsilon)
        expected.bubbles.zip(actual.bubbles).forEach { (first, second) ->
            assertTrue(abs(first.x - second.x) < epsilon)
            assertTrue(abs(first.y - second.y) < epsilon)
            assertTrue(abs(first.scaleX - second.scaleX) < epsilon)
            assertTrue(abs(first.scaleY - second.scaleY) < epsilon)
        }
    }
}
