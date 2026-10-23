package io.localizator.core.signal

import kotlin.math.log10
import kotlin.math.pow

/**
 * Modelo log-distance path loss: `RSSI(d) = P1m − 10·n·log10(d)`.
 *
 * @property measuredPowerAt1m P1m, RSSI esperado a 1 m (dBm).
 * @property pathLossExponent n, exponente de atenuación (> 0). 2.0 en espacio libre, 2.5–4 en interiores.
 */
data class PathLossModel(
    val measuredPowerAt1m: Double = DEFAULT_MEASURED_POWER,
    val pathLossExponent: Double = DEFAULT_EXPONENT,
) {
    init {
        require(pathLossExponent > 0) { "pathLossExponent debe ser > 0" }
    }

    /** Distancia (m) para un [rssi] (dBm): `10^((P1m − rssi) / (10·n))`. */
    fun distanceMeters(rssi: Double): Double =
        10.0.pow((measuredPowerAt1m - rssi) / (10 * pathLossExponent))

    /** RSSI (dBm) esperado a [distanceMeters] (> 0). Inversa de [distanceMeters]. */
    fun expectedRssi(distanceMeters: Double): Double {
        require(distanceMeters > 0) { "distance debe ser > 0" }
        return measuredPowerAt1m - 10 * pathLossExponent * log10(distanceMeters)
    }

    companion object {
        const val DEFAULT_MEASURED_POWER = -59.0
        const val DEFAULT_EXPONENT = 2.0

        /** Pérdida (dB) entre 0 m y 1 m según la convención iBeacon/Eddystone. */
        const val TX_POWER_TO_1M_LOSS = 41.0

        /** Modelo con `P1m = txPowerDbm − 41`, a partir del TX Power Level anunciado (dBm a 0 m). */
        fun fromAdvertisedTxPower(txPowerDbm: Int, exponent: Double = DEFAULT_EXPONENT) =
            PathLossModel(txPowerDbm - TX_POWER_TO_1M_LOSS, exponent)
    }
}
