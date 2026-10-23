package io.localizator.core.signal

import io.localizator.core.nextGaussian
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KalmanFilterTest {
    @Test
    fun `converge al valor real con ruido gaussiano`() {
        val rnd = Random(42)
        val f = KalmanFilter()
        var t = 0L
        repeat(200) {
            f.update(-70.0 + rnd.nextGaussian() * 4.0, t)
            t += 100
        }
        assertEquals(-70.0, f.estimate, 1.0)
    }

    @Test
    fun `un pico aislado apenas mueve la estimacion`() {
        val f = KalmanFilter()
        var t = 0L
        repeat(50) { f.update(-70.0, t); t += 100 }
        f.update(-40.0, t)
        assertTrue(abs(f.estimate - (-70.0)) < 2.0, "estimate=${f.estimate}")
    }

    @Test
    fun `sigue un cambio real sostenido`() {
        val f = KalmanFilter()
        var t = 0L
        repeat(50) { f.update(-80.0, t); t += 100 }
        repeat(50) { f.update(-60.0, t); t += 100 }
        assertEquals(-60.0, f.estimate, 1.5)
    }

    @Test
    fun `reacciona a un cambio real en menos de un segundo`() {
        val rnd = Random(3)
        val f = KalmanFilter()
        var t = 0L
        repeat(50) { f.update(-80.0 + rnd.nextGaussian() * 4, t); t += 100 }
        repeat(10) { f.update(-60.0 + rnd.nextGaussian() * 4, t); t += 100 }
        assertEquals(-60.0, f.estimate, 4.0)
    }
}
