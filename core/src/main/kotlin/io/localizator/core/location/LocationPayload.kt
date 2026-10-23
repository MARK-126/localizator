package io.localizator.core.location

import io.localizator.core.beacon.BeaconProtocol
import java.nio.ByteBuffer
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToInt

/**
 * Posición GPS compartida por un teléfono protegido.
 *
 * @property accuracyMeters radio de precisión (m), saturado a 255.
 * @property ageSeconds antigüedad de la posición al codificarla (s), con resolución de 10 s.
 */
data class SharedLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Int,
    val ageSeconds: Int,
)

/**
 * Payload cifrado de ubicación (respuesta de escaneo), ver `docs/UBICACION.md`:
 * versión (1) · secuencia (1) · AES-128-CTR(lat int32 · lon int32 · precisión uint8 · antigüedad uint8) · reservado (1).
 */
object LocationPayload {
    val SERVICE_UUID: UUID = UUID.fromString("beb58366-ea27-42f2-a4bc-c169eb247e3a")

    const val VERSION: Byte = 1
    const val SIZE = 13
    private const val PLAINTEXT_SIZE = 10
    private const val COORDINATE_SCALE = 1e7
    private const val AGE_UNIT_SECONDS = 10
    private val KEY_LABEL = "localizator-ubicacion".toByteArray(Charsets.UTF_8)

    fun encode(location: SharedLocation, secret: ByteArray, rotatingId: ByteArray, sequence: Int): ByteArray {
        val plain = ByteBuffer.allocate(PLAINTEXT_SIZE)
            .putInt((location.latitude * COORDINATE_SCALE).roundToInt())
            .putInt((location.longitude * COORDINATE_SCALE).roundToInt())
            .put(location.accuracyMeters.coerceIn(0, 255).toByte())
            .put((location.ageSeconds / AGE_UNIT_SECONDS).coerceIn(0, 255).toByte())
            .array()
        val seq = sequence.toByte()
        return byteArrayOf(VERSION, seq) + crypt(plain, secret, rotatingId, seq) + 0.toByte()
    }

    /** Ubicación descifrada, o `null` si el tamaño, la versión o las coordenadas no son válidos. */
    fun decode(bytes: ByteArray, secret: ByteArray, rotatingId: ByteArray): SharedLocation? {
        if (bytes.size != SIZE || bytes[0] != VERSION) return null
        val plain = ByteBuffer.wrap(crypt(bytes.copyOfRange(2, 2 + PLAINTEXT_SIZE), secret, rotatingId, bytes[1]))
        val latitude = plain.int / COORDINATE_SCALE
        val longitude = plain.int / COORDINATE_SCALE
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        val accuracy = plain.get().toInt() and 0xFF
        val age = (plain.get().toInt() and 0xFF) * AGE_UNIT_SECONDS
        return SharedLocation(latitude, longitude, accuracy, age)
    }

    private fun crypt(data: ByteArray, secret: ByteArray, rotatingId: ByteArray, sequence: Byte): ByteArray {
        require(rotatingId.size == BeaconProtocol.ROTATING_ID_SIZE) { "ID rotativo de tamaño inválido" }
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        val iv = rotatingId + sequence + ByteArray(7)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key(secret), "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    private fun key(secret: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(KEY_LABEL).copyOf(16)
    }
}
