package io.localizator.core.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocationPayloadTest {
    private val secret = ByteArray(16) { (it * 7).toByte() }
    private val rotatingId = ByteArray(8) { (it + 1).toByte() }
    private val location = SharedLocation(-34.6037345, -58.3815704, accuracyMeters = 12, ageSeconds = 40)

    @Test
    fun `ida y vuelta conserva la posicion con precision de centimetros`() {
        val bytes = LocationPayload.encode(location, secret, rotatingId, sequence = 3)
        assertEquals(LocationPayload.SIZE, bytes.size)
        val decoded = assertNotNull(LocationPayload.decode(bytes, secret, rotatingId))
        assertEquals(location.latitude, decoded.latitude, 1e-7)
        assertEquals(location.longitude, decoded.longitude, 1e-7)
        assertEquals(12, decoded.accuracyMeters)
        assertEquals(40, decoded.ageSeconds)
    }

    @Test
    fun `cabe en una respuesta de escaneo`() {
        assertEquals(31, 2 + 16 + LocationPayload.SIZE)
    }

    @Test
    fun `satura precision y antiguedad`() {
        val big = location.copy(accuracyMeters = 900, ageSeconds = 10_000)
        val decoded = assertNotNull(LocationPayload.decode(LocationPayload.encode(big, secret, rotatingId, 0), secret, rotatingId))
        assertEquals(255, decoded.accuracyMeters)
        assertEquals(2_550, decoded.ageSeconds)
    }

    @Test
    fun `el cifrado oculta la posicion y depende de secuencia e ID`() {
        val a = LocationPayload.encode(location, secret, rotatingId, 1)
        val b = LocationPayload.encode(location, secret, rotatingId, 2)
        val c = LocationPayload.encode(location, secret, ByteArray(8) { 9 }, 1)
        assertFalse(a.copyOfRange(2, 12).contentEquals(b.copyOfRange(2, 12)))
        assertFalse(a.copyOfRange(2, 12).contentEquals(c.copyOfRange(2, 12)))
    }

    @Test
    fun `con otro secreto no se obtiene la posicion real`() {
        val bytes = LocationPayload.encode(location, secret, rotatingId, 5)
        val wrong = LocationPayload.decode(bytes, ByteArray(16) { 1 }, rotatingId)
        assertTrue(wrong == null || wrong.latitude != location.latitude)
    }

    @Test
    fun `rechaza tamano o version invalidos`() {
        assertNull(LocationPayload.decode(ByteArray(12), secret, rotatingId))
        val bytes = LocationPayload.encode(location, secret, rotatingId, 0).also { it[0] = 9 }
        assertNull(LocationPayload.decode(bytes, secret, rotatingId))
    }
}

class GeoTest {
    private val obelisco = GeoPoint(-34.6037345, -58.3815704)

    @Test
    fun `destino y distancia son consistentes`() {
        for (bearing in listOf(0.0, 45.0, 135.0, 270.0)) {
            val p = Geo.destination(obelisco, bearing, 25.0)
            assertEquals(25.0, Geo.distanceMeters(obelisco, p), 0.01)
            assertEquals(bearing, Geo.bearingDegrees(obelisco, p), 0.1)
        }
    }

    @Test
    fun `un grado de latitud mide unos 111 km`() {
        val north = GeoPoint(obelisco.latitude + 1, obelisco.longitude)
        assertEquals(111_195.0, Geo.distanceMeters(obelisco, north), 50.0)
    }
}

class LocationFusionTest {
    private val seeker = GeoPoint(-34.6037345, -58.3815704)

    @Test
    fun `estima sobre el anillo BLE en la direccion del GPS`() {
        val target = Geo.destination(seeker, 60.0, 12.0)
        val fused = LocationFusion.fuse(seeker, target, 5.0, bleDistanceMeters = 8.0, bleMinMeters = 6.0, bleMaxMeters = 11.0)
        assertEquals(8.0, Geo.distanceMeters(seeker, fused.estimate), 0.01)
        assertEquals(60.0, fused.bearingDegrees, 0.1)
        assertTrue(fused.consistent)
        assertFalse(fused.preferBluetooth)
    }

    @Test
    fun `cerca, con distancia BLE menor que el error GPS, manda el Bluetooth`() {
        val target = Geo.destination(seeker, 90.0, 9.0)
        val fused = LocationFusion.fuse(seeker, target, 12.0, bleDistanceMeters = 0.4, bleMinMeters = 0.2, bleMaxMeters = 0.8)
        assertTrue(fused.preferBluetooth)
    }

    @Test
    fun `detecta circulos que no se cortan`() {
        val target = Geo.destination(seeker, 0.0, 80.0)
        val fused = LocationFusion.fuse(seeker, target, 10.0, bleDistanceMeters = 5.0, bleMinMeters = 3.0, bleMaxMeters = 7.0)
        assertFalse(fused.consistent)
    }
}

class BatteryPolicyTest {
    private val policy = BatteryPolicy()

    @Test
    fun `umbrales de 30 y 15 por ciento`() {
        assertEquals(PowerMode.NORMAL, policy.modeFor(30, charging = false))
        assertEquals(PowerMode.SAVING, policy.modeFor(29, charging = false))
        assertEquals(PowerMode.SAVING, policy.modeFor(15, charging = false))
        assertEquals(PowerMode.CRITICAL, policy.modeFor(14, charging = false))
    }

    @Test
    fun `cargando siempre es normal`() {
        assertEquals(PowerMode.NORMAL, policy.modeFor(5, charging = true))
    }

    @Test
    fun `intervalos de GPS por modo`() {
        assertEquals(30_000, BatteryPolicy.gpsIntervalMs(PowerMode.NORMAL))
        assertEquals(300_000, BatteryPolicy.gpsIntervalMs(PowerMode.SAVING))
        assertNull(BatteryPolicy.gpsIntervalMs(PowerMode.CRITICAL))
    }
}
