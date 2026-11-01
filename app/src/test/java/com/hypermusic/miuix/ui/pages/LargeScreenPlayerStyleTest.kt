package com.hypermusic.miuix.ui.pages

import org.junit.Assert.assertEquals
import org.junit.Test

class LargeScreenPlayerStyleTest {
    @Test fun fullscreenFallsBackToCardThenCar() {
        assertEquals(1, resolveLargeScreenPlayerStyle(1, 4f / 3f))
        assertEquals(0, resolveLargeScreenPlayerStyle(1, 1f))
        assertEquals(2, resolveLargeScreenPlayerStyle(1, 2136f / 3200f))
        assertEquals(0, resolveLargeScreenPlayerStyle(1, 0.67f))
        assertEquals(2, resolveLargeScreenPlayerStyle(1, 0.6f))
    }
    @Test fun cardFallsBackOnlyBelowThresholdAndCarRemainsSelected() {
        assertEquals(2, resolveLargeScreenPlayerStyle(0, 10f / 16f))
        assertEquals(0, resolveLargeScreenPlayerStyle(0, 0.67f))
        assertEquals(2, resolveLargeScreenPlayerStyle(0, 0.6f))
        assertEquals(2, resolveLargeScreenPlayerStyle(2, 2f))
        assertEquals(2, resolveLargeScreenPlayerStyle(2, 0.5f))
    }
}
