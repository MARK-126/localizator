package io.localizator.core.calibration

import io.localizator.core.nextGaussian
import io.localizator.core.signal.PathLossModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    @Test
    fun `evaluacion compara la distancia estimada antes y despues`() {
        val truth = PathLossModel(-65.0, 2.5)
        val c = Calibrator()
        for (d in listOf(1.0, 3.0)) repeat(20) { c.addSample(d, truth.expectedRssi(d)) }
        val calibrated = assertNotNull(c.fit())
        val after = c.evaluate(calibrated)
        val before = c.evaluate(PathLossModel())
        assertEquals(1.0, after.getValue(1.0), 0.01)
        assertEquals(3.0, after.getValue(3.0), 0.01)
        assertTrue(kotlin.math.abs(before.getValue(3.0) - 3.0) > 1.0)
    }

    @Test
    fun `mediana y limpieza por distancia`() {
        val c = Calibrator()
        listOf(-60.0, -70.0, -65.0).forEach { c.addSample(1.0, it) }
        assertEquals(-65.0, c.medianRssi(1.0))
        c.clear(1.0)
        assertEquals(null, c.medianRssi(1.0))
        assertEquals(0, c.sampleCount(1.0))
    }
}
