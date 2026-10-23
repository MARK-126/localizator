package io.localizator.core.signal

import kotlin.test.Test
import kotlin.test.assertEquals

class TrendDetectorTest {
    @Test
    fun `detecta acercamiento y alejamiento`() {
        val up = TrendDetector()
        val down = TrendDetector()
        for (i in 0..20) {
            up.add(i * 100L, -80.0 + i * 0.3)
            down.add(i * 100L, -60.0 - i * 0.3)
        }
        assertEquals(Trend.APPROACHING, up.trend())
        assertEquals(Trend.MOVING_AWAY, down.trend())
    }

    @Test
    fun `senal estable da STEADY`() {
        val t = TrendDetector()
        for (i in 0..20) t.add(i * 100L, -70.0)
        assertEquals(Trend.STEADY, t.trend())
    }
}
