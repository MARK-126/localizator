package io.localizator.core.beacon

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** ID rotativo: `HMAC-SHA256(secreto, ventana como int64 big-endian)[0..8]`. */
object RotatingId {
    fun compute(secret: ByteArray, window: Long): ByteArray {
        require(secret.size == BeaconProtocol.SECRET_SIZE) { "El secreto debe tener ${BeaconProtocol.SECRET_SIZE} bytes" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        val digest = mac.doFinal(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(window).array())
        return digest.copyOf(BeaconProtocol.ROTATING_ID_SIZE)
    }
}
