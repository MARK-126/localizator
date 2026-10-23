package io.localizator.core.signal

import kotlin.test.Test
import kotlin.test.assertEquals

class PathLossModelTest {
    @Test
    fun `distancia a 1m es igual a P1m`() {
        val m = PathLossModel(-59.0, 2.0)
        assertEquals(1.0, m.distanceMeters(-59.0), 1e-9)
        assertEquals(10.0, m.distanceMeters(-79.0), 1e-9)
    }

    @Test
    fun `expectedRssi es la inversa de distanceMeters`() {
        val m = PathLossModel(-62.0, 2.7)
        for (d in listOf(0.3, 1.0, 2.5, 7.0)) {
            assertEquals(d, m.distanceMeters(m.expectedRssi(d)), 1e-9)
        }
    }

    @Test
    fun `tx power anunciado se convierte a P1m`() {
        assertEquals(-53.0, PathLossModel.fromAdvertisedTxPower(-12).measuredPowerAt1m, 1e-9)
    }
}
