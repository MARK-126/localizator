package io.localizator.core.signal

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Filtro de Kalman escalar para RSSI, con modelo de paseo aleatorio y gating de innovación.
 *
 * - Predicción: `P += q·Δt`.
 * - Una lectura con `|z − x| > outlierGate·√(P + r)` se considera atípica y se pondera con
 *   `r·outlierNoiseFactor`.
 * - [sustainedChangeCount] atípicas consecutivas del mismo signo se tratan como cambio real:
 *   `P += innovación²` y se pondera con `r`.
 *
 * No es thread-safe.
 *
 * @param processNoisePerSecond q, varianza de proceso (dB²/s).
 * @param measurementNoise r, varianza de medición (dB²).
 * @param outlierGate umbral de atípicos, en desviaciones estándar de la innovación.
 * @param outlierNoiseFactor multiplicador de r para lecturas atípicas.
 * @param sustainedChangeCount atípicas consecutivas del mismo signo que se aceptan como cambio real.
 */
class KalmanFilter(
    private val processNoisePerSecond: Double = 0.5,
    private val measurementNoise: Double = 16.0,
    private val outlierGate: Double = 3.0,
    private val outlierNoiseFactor: Double = 10.0,
    private val sustainedChangeCount: Int = 3,
) {
    /** Estimación actual (dBm); `NaN` antes de la primera lectura. */
    var estimate: Double = Double.NaN
        private set

    /** Varianza de [estimate] (dB²); `NaN` antes de la primera lectura. */
    var variance: Double = Double.NaN
        private set

    private var lastTimestampMs: Long = 0
    private var consecutiveOutliers = 0
    private var lastOutlierSign = 0

    val isInitialized: Boolean get() = !estimate.isNaN()

    /** Incorpora la lectura [measurement] (dBm) tomada en [timestampMs] y devuelve [estimate]. */
    fun update(measurement: Double, timestampMs: Long): Double {
        if (!isInitialized) {
            estimate = measurement
            variance = measurementNoise
            lastTimestampMs = timestampMs
            return estimate
        }

        val dtSeconds = ((timestampMs - lastTimestampMs).coerceAtLeast(0)) / 1000.0
        lastTimestampMs = timestampMs
        variance += processNoisePerSecond * dtSeconds.coerceAtLeast(MIN_DT_SECONDS)

        val innovation = measurement - estimate
        val innovationStd = sqrt(variance + measurementNoise)
        val isOutlier = abs(innovation) > outlierGate * innovationStd
        val sign = if (innovation >= 0) 1 else -1
        consecutiveOutliers = when {
            !isOutlier -> 0
            sign == lastOutlierSign -> consecutiveOutliers + 1
            else -> 1
        }
        lastOutlierSign = if (isOutlier) sign else 0

        val r = when {
            consecutiveOutliers >= sustainedChangeCount -> {
                variance += innovation * innovation
                measurementNoise
            }
            isOutlier -> measurementNoise * outlierNoiseFactor
            else -> measurementNoise
        }

        val gain = variance / (variance + r)
        estimate += gain * innovation
        variance *= (1 - gain)
        return estimate
    }

    fun reset() {
        estimate = Double.NaN
        variance = Double.NaN
        lastTimestampMs = 0
        consecutiveOutliers = 0
        lastOutlierSign = 0
    }

    private companion object {
        const val MIN_DT_SECONDS = 0.01
    }
}
