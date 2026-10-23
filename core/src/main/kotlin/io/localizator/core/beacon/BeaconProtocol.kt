package io.localizator.core.beacon

import java.util.UUID

/** Constantes del protocolo del faro (ver `docs/FARO.md`). */
object BeaconProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("768d753b-11be-48c1-99d7-51df0044d579")

    const val VERSION: Byte = 1
    const val SECRET_SIZE = 16
    const val ROTATING_ID_SIZE = 8
    const val PAYLOAD_SIZE = 1 + ROTATING_ID_SIZE + 1

    /** Duración de una ventana del ID rotativo (ms). */
    const val WINDOW_MS = 15 * 60 * 1000L

    /** Valor de P1m que indica "desconocido". */
    const val P1M_UNKNOWN: Byte = 0x7F

    fun windowOf(epochMs: Long): Long = Math.floorDiv(epochMs, WINDOW_MS)

    /** Instante (epoch ms) en que empieza la ventana siguiente a la de [epochMs]. */
    fun nextWindowStart(epochMs: Long): Long = (windowOf(epochMs) + 1) * WINDOW_MS
}
