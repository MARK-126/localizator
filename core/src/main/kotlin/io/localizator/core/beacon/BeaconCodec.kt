package io.localizator.core.beacon

/**
 * Payload decodificado del faro.
 *
 * @property measuredPowerAt1m P1m (dBm) anunciado, o `null` si es desconocido.
 */
class BeaconPayload(val rotatingId: ByteArray, val measuredPowerAt1m: Int?)

/** Codificación del payload de 10 bytes: versión, ID rotativo (8) y P1m (int8, `0x7F` = desconocido). */
object BeaconCodec {

    fun encode(rotatingId: ByteArray, measuredPowerAt1m: Int?): ByteArray {
        require(rotatingId.size == BeaconProtocol.ROTATING_ID_SIZE) { "ID rotativo de tamaño inválido" }
        val p1m = measuredPowerAt1m?.coerceIn(Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt() - 1)?.toByte()
            ?: BeaconProtocol.P1M_UNKNOWN
        return byteArrayOf(BeaconProtocol.VERSION) + rotatingId + p1m
    }

    /** Payload decodificado, o `null` si el tamaño o la versión no corresponden. */
    fun decode(bytes: ByteArray): BeaconPayload? {
        if (bytes.size != BeaconProtocol.PAYLOAD_SIZE || bytes[0] != BeaconProtocol.VERSION) return null
        val id = bytes.copyOfRange(1, 1 + BeaconProtocol.ROTATING_ID_SIZE)
        val p1m = bytes.last().takeIf { it != BeaconProtocol.P1M_UNKNOWN }?.toInt()
        return BeaconPayload(id, p1m)
    }
}
