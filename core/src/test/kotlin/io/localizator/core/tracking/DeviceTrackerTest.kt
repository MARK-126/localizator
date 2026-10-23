package io.localizator.core.tracking

import io.localizator.core.nextGaussian
import io.localizator.core.signal.PathLossModel
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceTrackerTest {
    @Test
    fun `el filtrado mejora la precision frente al RSSI crudo`() {
        val truth = PathLossModel(-59.0, 2.0)
        val tracker = DeviceTracker(defaultModel = truth)
        val rnd = Random(1)
        val realDistance = 3.0
        var rawError = 0.0
        var filteredError = 0.0
        var n = 0
        for (i in 0 until 300) {
            val rssi = (truth.expectedRssi(realDistance) + rnd.nextGaussian() * 5).toInt()
            val est = tracker.onAdvertisement(Advertisement("AA", null, rssi, timestampMs = i * 100L))
            if (i >= WARM_UP_SAMPLES) {
                rawError += abs(truth.distanceMeters(rssi.toDouble()) - realDistance)
                filteredError += abs(est.distanceMeters - realDistance)
                n++
            }
        }
        rawError /= n
        filteredError /= n
        assertTrue(filteredError < rawError / 2, "raw=$rawError filtered=$filteredError")
        assertTrue(filteredError < 0.5)
    }

    @Test
    fun `prioridad de modelo calibrado sobre tx power y por defecto`() {
        val tracker = DeviceTracker()
        assertEquals(ModelSource.DEFAULT, tracker.modelFor("X", null).second)
        assertEquals(ModelSource.ADVERTISED_TX_POWER, tracker.modelFor("X", -8).second)
        tracker.setCalibration("X", PathLossModel(-55.0, 2.2))
        assertEquals(ModelSource.CALIBRATED, tracker.modelFor("X", -8).second)
    }

    @Test
    fun `la calibracion estrecha el intervalo de confianza`() {
        val tracker = DeviceTracker()
        var t = 0L
        repeat(50) { tracker.onAdvertisement(Advertisement("X", null, -65, timestampMs = t)); t += 100 }
        val before = tracker.estimateFor("X")!!
        tracker.setCalibration("X", PathLossModel(-59.0, 2.0))
        val after = tracker.estimateFor("X")!!
        assertTrue(
            after.distanceMaxMeters - after.distanceMinMeters <
                before.distanceMaxMeters - before.distanceMinMeters,
        )
    }

    @Test
    fun `elimina dispositivos que dejan de anunciarse y ordena por distancia`() {
        val tracker = DeviceTracker(staleAfterMs = 5_000)
        tracker.onAdvertisement(Advertisement("lejos", null, -85, timestampMs = 0))
        tracker.onAdvertisement(Advertisement("cerca", null, -50, timestampMs = 4_000))
        tracker.onAdvertisement(Advertisement("viejo", null, -60, timestampMs = -10_000))
        val snap = tracker.snapshot(nowMs = 4_500)
        assertEquals(listOf("cerca", "lejos"), snap.map { it.id })
    }

    @Test
    fun `conserva el nombre aunque un paquete no lo traiga`() {
        val tracker = DeviceTracker()
        tracker.onAdvertisement(Advertisement("X", "Auriculares", -60, timestampMs = 0))
        val est = tracker.onAdvertisement(Advertisement("X", null, -61, timestampMs = 100))
        assertEquals("Auriculares", est.name)
    }
}

private const val WARM_UP_SAMPLES = 50
