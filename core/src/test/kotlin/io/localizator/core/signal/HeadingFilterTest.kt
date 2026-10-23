package io.localizator.core.signal

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeadingFilterTest {
    @Test
    fun `la primera lectura se devuelve tal cual`() {
        assertEquals(90.0, HeadingFilter().update(90.0), 1e-9)
    }

    @Test
    fun `promedia sin saltar en el cruce 359 a 1`() {
        val filter = HeadingFilter(alpha = 0.5)
        filter.update(359.0)
        val heading = filter.update(1.0)
        assertTrue(heading > 358 || heading < 2, "heading=$heading")
    }

    @Test
    fun `converge a un rumbo estable`() {
        val filter = HeadingFilter()
        var heading = filter.update(0.0)
        repeat(100) { heading = filter.update(120.0) }
        assertEquals(120.0, heading, 0.5)
    }

    @Test
    fun `normaliza angulos negativos y mayores de 360`() {
        assertEquals(350.0, HeadingFilter.normalize(-10.0), 1e-9)
        assertEquals(10.0, HeadingFilter.normalize(370.0), 1e-9)
    }
}

class ProximityFeedbackTest {
    private val feedback = ProximityFeedback()

    @Test
    fun `satura en los extremos`() {
        assertEquals(150, feedback.pulseIntervalMs(0.1))
        assertEquals(1_500, feedback.pulseIntervalMs(25.0))
        assertEquals(1_500, feedback.pulseIntervalMs(Double.NaN))
    }

    @Test
    fun `mas cerca vibra mas rapido`() {
        val near = feedback.pulseIntervalMs(1.0)
        val mid = feedback.pulseIntervalMs(3.0)
        val far = feedback.pulseIntervalMs(8.0)
        assertTrue(near < mid && mid < far, "near=$near mid=$mid far=$far")
    }

    @Test
    fun `interpolacion logaritmica en el punto medio geometrico`() {
        val geometricMid = kotlin.math.sqrt(0.5 * 10.0)
        assertTrue(abs(feedback.pulseIntervalMs(geometricMid) - 825) <= 1)
    }
}
