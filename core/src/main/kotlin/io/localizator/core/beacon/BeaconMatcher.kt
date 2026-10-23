package io.localizator.core.beacon

/**
 * Reconoce payloads de faro de teléfonos emparejados.
 *
 * Un payload coincide con un [PairedBeacon] si su ID rotativo es el de la ventana actual, la
 * anterior o la siguiente (tolerancia de ±[BeaconProtocol.WINDOW_MS]). Los IDs esperados se
 * calculan una vez por ventana. No es thread-safe.
 */
class BeaconMatcher(private val paired: List<PairedBeacon>) {

    private var cachedWindow: Long? = null
    private var expected: Map<String, PairedBeacon> = emptyMap()

    /** Teléfono emparejado al que corresponde [payload] en [nowEpochMs], o `null`. */
    fun match(payload: BeaconPayload, nowEpochMs: Long): PairedBeacon? {
        val window = BeaconProtocol.windowOf(nowEpochMs)
        if (window != cachedWindow) {
            expected = paired.flatMap { beacon ->
                (window - 1..window + 1).map { RotatingId.compute(beacon.secret, it).toKey() to beacon }
            }.toMap()
            cachedWindow = window
        }
        return expected[payload.rotatingId.toKey()]
    }

    private fun ByteArray.toKey(): String = joinToString("") { "%02x".format(it) }
}
