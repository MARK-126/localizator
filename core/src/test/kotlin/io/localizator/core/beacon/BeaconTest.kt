package io.localizator.core.beacon

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RotatingIdTest {
    private val secret = ByteArray(16) { it.toByte() }

    @Test
    fun `es determinista y de 8 bytes`() {
        val a = RotatingId.compute(secret, 1000)
        assertEquals(8, a.size)
        assertContentEquals(a, RotatingId.compute(secret, 1000))
    }

    @Test
    fun `cambia con la ventana y con el secreto`() {
        val base = RotatingId.compute(secret, 1000)
        assertFalse(base.contentEquals(RotatingId.compute(secret, 1001)))
        assertFalse(base.contentEquals(RotatingId.compute(ByteArray(16) { 7 }, 1000)))
    }

    @Test
    fun `ventanas de 15 minutos`() {
        assertEquals(0, BeaconProtocol.windowOf(0))
        assertEquals(0, BeaconProtocol.windowOf(899_999))
        assertEquals(1, BeaconProtocol.windowOf(900_000))
        assertEquals(1_800_000, BeaconProtocol.nextWindowStart(900_001))
    }
}

class BeaconCodecTest {
    private val id = ByteArray(8) { (it + 1).toByte() }

    @Test
    fun `codifica y decodifica con P1m`() {
        val bytes = BeaconCodec.encode(id, -62)
        assertEquals(BeaconProtocol.PAYLOAD_SIZE, bytes.size)
        val decoded = assertNotNull(BeaconCodec.decode(bytes))
        assertContentEquals(id, decoded.rotatingId)
        assertEquals(-62, decoded.measuredPowerAt1m)
    }

    @Test
    fun `P1m desconocido`() {
        val decoded = assertNotNull(BeaconCodec.decode(BeaconCodec.encode(id, null)))
        assertNull(decoded.measuredPowerAt1m)
    }

    @Test
    fun `rechaza tamano o version invalidos`() {
        assertNull(BeaconCodec.decode(ByteArray(9)))
        val wrongVersion = BeaconCodec.encode(id, null).also { it[0] = 2 }
        assertNull(BeaconCodec.decode(wrongVersion))
    }
}

class PairingCodeTest {
    private val beacon = PairedBeacon(ByteArray(16) { (it * 3).toByte() }, "S25 de Marcos")

    @Test
    fun `ida y vuelta conserva secreto, nombre e id`() {
        val text = PairingCode.format(beacon)
        assertTrue(text.startsWith("LOCALIZATOR:1:"))
        val parsed = assertNotNull(PairingCode.parse(text))
        assertContentEquals(beacon.secret, parsed.secret)
        assertEquals("S25 de Marcos", parsed.name)
        assertEquals(beacon.id, parsed.id)
    }

    @Test
    fun `nombres con acentos y dos puntos`() {
        val b = PairedBeacon(ByteArray(16), "Teléfono: Ñandú")
        assertEquals("Teléfono: Ñandú", PairingCode.parse(PairingCode.format(b))?.name)
    }

    @Test
    fun `rechaza codigos invalidos`() {
        assertNull(PairingCode.parse("hola"))
        assertNull(PairingCode.parse("LOCALIZATOR:2:AAAA:AAAA"))
        assertNull(PairingCode.parse("LOCALIZATOR:1:AAAA:QQ"))
        assertNull(PairingCode.parse("LOCALIZATOR:1:@@@@:QQ"))
    }

    @Test
    fun `id estable con prefijo`() {
        assertTrue(beacon.id.startsWith(PairedBeacon.ID_PREFIX))
        assertEquals(PairedBeacon.ID_PREFIX.length + 8, beacon.id.length)
    }
}

class BeaconMatcherTest {
    private val mine = PairedBeacon(ByteArray(16) { 1 }, "S25")
    private val other = PairedBeacon(ByteArray(16) { 2 }, "Otro")
    private val now = 1_761_234_567_000L

    private fun payloadFor(beacon: PairedBeacon, epochMs: Long) =
        BeaconCodec.decode(BeaconCodec.encode(RotatingId.compute(beacon.secret, BeaconProtocol.windowOf(epochMs)), null))!!

    @Test
    fun `reconoce el faro emparejado`() {
        val matcher = BeaconMatcher(listOf(other, mine))
        assertEquals(mine, matcher.match(payloadFor(mine, now), now))
    }

    @Test
    fun `tolera una ventana de desfase de reloj`() {
        val matcher = BeaconMatcher(listOf(mine))
        assertEquals(mine, matcher.match(payloadFor(mine, now - BeaconProtocol.WINDOW_MS), now))
        assertEquals(mine, matcher.match(payloadFor(mine, now + BeaconProtocol.WINDOW_MS), now))
        assertNull(matcher.match(payloadFor(mine, now + 2 * BeaconProtocol.WINDOW_MS), now))
    }

    @Test
    fun `no reconoce faros no emparejados`() {
        assertNull(BeaconMatcher(listOf(mine)).match(payloadFor(other, now), now))
    }

    @Test
    fun `secretos nuevos son distintos y validos`() {
        val a = PairedBeacon.newSecret()
        val b = PairedBeacon.newSecret()
        assertEquals(16, a.size)
        assertFalse(a.contentEquals(b))
    }
}
