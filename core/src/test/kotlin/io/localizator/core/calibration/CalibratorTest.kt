package io.localizator.core.calibration

import io.localizator.core.nextGaussian
import io.localizator.core.signal.PathLossModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CalibratorTest {
    @Test
    fun `una sola distancia ajusta P1m con n por defecto`() {
        val c = Calibrator(defaultExponent = 2.0)
        repeat(20) { c.addSample(2.0, -65.0) }
        val m = assertNotNull(c.fit())
        assertEquals(2.0, m.pathLossExponent, 1e-9)
        assertEquals(2.0, m.distanceMeters(-65.0), 1e-6)
    }

    @Test
    fun `dos distancias recuperan P1m y n`() {
        val truth = PathLossModel(-61.0, 2.8)
        val rnd = Random(7)
        val c = Calibrator()
        for (d in listOf(1.0, 3.0)) {
            repeat(40) { c.addSample(d, truth.expectedRssi(d) + rnd.nextGaussian() * 3) }
        }
        val m = assertNotNull(c.fit())
        assertEquals(-61.0, m.measuredPowerAt1m, 1.5)
        assertEquals(2.8, m.pathLossExponent, 0.4)
    }

    @Test
    fun `sin muestras suficientes no devuelve modelo`() {
        val c = Calibrator()
        repeat(3) { c.addSample(1.0, -60.0) }
        assertEquals(null, c.fit())
    }
}
