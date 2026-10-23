package io.localizator.core.beacon

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Teléfono protegido conocido: su secreto y el nombre elegido por el dueño.
 *
 * @property id identificador local estable: `beacon:` + 4 bytes de `SHA-256(secret)` en hexadecimal.
 */
class PairedBeacon(val secret: ByteArray, val name: String) {
    init {
        require(secret.size == BeaconProtocol.SECRET_SIZE) { "El secreto debe tener ${BeaconProtocol.SECRET_SIZE} bytes" }
        require(name.isNotBlank()) { "El nombre no puede estar vacío" }
    }

    val id: String = ID_PREFIX + MessageDigest.getInstance("SHA-256").digest(secret)
        .copyOf(ID_HASH_BYTES)
        .joinToString("") { "%02x".format(it) }

    override fun equals(other: Any?): Boolean = other is PairedBeacon && other.id == id && other.name == name

    override fun hashCode(): Int = 31 * id.hashCode() + name.hashCode()

    companion object {
        const val ID_PREFIX = "beacon:"
        private const val ID_HASH_BYTES = 4

        fun newSecret(random: SecureRandom = SecureRandom()): ByteArray =
            ByteArray(BeaconProtocol.SECRET_SIZE).also(random::nextBytes)
    }
}
