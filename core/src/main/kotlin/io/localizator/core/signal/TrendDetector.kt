package io.localizator.core.signal

/** Sentido de la variación del RSSI filtrado. */
enum class Trend { APPROACHING, STEADY, MOVING_AWAY, UNKNOWN }

/**
 * Tendencia del RSSI filtrado por regresión lineal sobre una ventana deslizante.
 *
 * No es thread-safe.
 *
 * @param windowMs duración de la ventana (ms).
 * @param thresholdDbPerSecond pendiente mínima (dB/s) para clasificar como [Trend.APPROACHING] o
 *   [Trend.MOVING_AWAY].
 * @param minSamples muestras mínimas en la ventana; por debajo, [Trend.UNKNOWN].
 */
class TrendDetector(
    private val windowMs: Long = 3_000,
    private val thresholdDbPerSecond: Double = 0.8,
    private val minSamples: Int = 5,
) {
    private val samples = ArrayDeque<Pair<Long, Double>>()

    /** Añade una muestra y descarta las anteriores a `timestampMs − windowMs`. */
    fun add(timestampMs: Long, filteredRssi: Double) {
        samples.addLast(timestampMs to filteredRssi)
        while (samples.isNotEmpty() && timestampMs - samples.first().first > windowMs) {
            samples.removeFirst()
        }
    }

    /** Pendiente (dB/s) por mínimos cuadrados; `null` con menos de `minSamples` o varianza temporal nula. */
    fun slopeDbPerSecond(): Double? {
        if (samples.size < minSamples) return null
        val t0 = samples.first().first
        val xs = samples.map { (it.first - t0) / 1000.0 }
        val ys = samples.map { it.second }
        val meanX = xs.average()
        val meanY = ys.average()
        var sxx = 0.0
        var sxy = 0.0
        for (i in xs.indices) {
            sxx += (xs[i] - meanX) * (xs[i] - meanX)
            sxy += (xs[i] - meanX) * (ys[i] - meanY)
        }
        if (sxx == 0.0) return null
        return sxy / sxx
    }

    fun trend(): Trend {
        val slope = slopeDbPerSecond() ?: return Trend.UNKNOWN
        return when {
            slope > thresholdDbPerSecond -> Trend.APPROACHING
            slope < -thresholdDbPerSecond -> Trend.MOVING_AWAY
            else -> Trend.STEADY
        }
    }

    fun reset() = samples.clear()
}
