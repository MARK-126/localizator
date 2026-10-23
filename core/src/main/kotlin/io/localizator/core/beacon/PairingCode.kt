package io.localizator.core.beacon

import java.util.Base64

/** Contenido del QR de emparejamiento: `LOCALIZATOR:1:<secreto>:<nombre>` en Base64 URL-safe sin relleno. */
object PairingCode {
    private const val PREFIX = "LOCALIZATOR"
    private const val FORMAT_VERSION = "1"

    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun format(beacon: PairedBeacon): String = listOf(
        PREFIX,
        FORMAT_VERSION,
        encoder.encodeToString(beacon.secret),
        encoder.encodeToString(beacon.name.toByteArray(Charsets.UTF_8)),
    ).joinToString(":")

    /** [PairedBeacon] del código, o `null` si el formato, la versión, el secreto o el nombre son inválidos. */
    fun parse(text: String): PairedBeacon? {
        val parts = text.trim().split(":")
        if (parts.size != 4 || parts[0] != PREFIX || parts[1] != FORMAT_VERSION) return null
        return runCatching {
            val secret = decoder.decode(parts[2])
            val name = String(decoder.decode(parts[3]), Charsets.UTF_8)
            PairedBeacon(secret, name.trim())
        }.getOrNull()
    }
}
