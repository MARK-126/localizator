package io.localizator.core.calibration

import io.localizator.core.signal.PathLossModel
import kotlin.math.log10

/**
 * Ajusta un [PathLossModel] a partir de muestras de RSSI tomadas a distancias conocidas.
 *
 * Cada distancia se resume por la mediana de sus muestras.
 * - 1 distancia: se ajusta P1m con `n = defaultExponent`.
 * - ≥ 2 distancias: regresión lineal de `RSSI = P1m − 10·n·log10(d)`. n se acota a
 *   [[MIN_EXPONENT], [MAX_EXPONENT]] y P1m se recalcula con el n acotado.
 *
 * No es thread-safe.
 */
class Calibrator(private val defaultExponent: Double = PathLossModel.DEFAULT_EXPONENT) {

    private val samplesByDistance = linkedMapOf<Double, MutableList<Double>>()

    fun addSample(distanceMeters: Double, rssi: Double) {
        require(distanceMeters > 0) { "distance debe ser > 0" }
        samplesByDistance.getOrPut(distanceMeters) { mutableListOf() }.add(rssi)
    }

    fun sampleCount(distanceMeters: Double): Int = samplesByDistance[distanceMeters]?.size ?: 0

    /** Mediana de las muestras a [distanceMeters], o `null` si no hay muestras. */
    fun medianRssi(distanceMeters: Double): Double? = samplesByDistance[distanceMeters]?.takeIf { it.isNotEmpty() }?.median()

    /** Distancia estimada por [model] con la mediana de cada distancia medida: distancia real → estimada. */
    fun evaluate(model: PathLossModel): Map<Double, Double> =
        samplesByDistance.filterValues { it.isNotEmpty() }.mapValues { (_, samples) -> model.distanceMeters(samples.median()) }

    /** Descarta las muestras a [distanceMeters]. */
    fun clear(distanceMeters: Double) {
        samplesByDistance.remove(distanceMeters)
    }

    fun clear() = samplesByDistance.clear()

    /**
     * Devuelve el modelo ajustado con las distancias que tienen al menos [minSamplesPerPoint]
     * muestras, o `null` si ninguna las tiene.
     */
    fun fit(minSamplesPerPoint: Int = MIN_SAMPLES_PER_POINT): PathLossModel? {
        val points = samplesByDistance
            .filterValues { it.size >= minSamplesPerPoint }
            .map { (d, samples) -> log10(d) to samples.median() }
        if (points.isEmpty()) return null

        if (points.size == 1) {
            val (x, rssi) = points.single()
            return PathLossModel(rssi + 10 * defaultExponent * x, defaultExponent)
        }

        val meanX = points.map { it.first }.average()
        val meanY = points.map { it.second }.average()
        val sxx = points.sumOf { (x, _) -> (x - meanX) * (x - meanX) }
        val sxy = points.sumOf { (x, y) -> (x - meanX) * (y - meanY) }
        val exponent = (-(sxy / sxx) / 10).coerceIn(MIN_EXPONENT, MAX_EXPONENT)
        val measuredPower = meanY + 10 * exponent * meanX
        return PathLossModel(measuredPower, exponent)
    }

    companion object {
        const val MIN_SAMPLES_PER_POINT = 10
        const val MIN_EXPONENT = 1.5
        const val MAX_EXPONENT = 4.5
    }
}

internal fun List<Double>.median(): Double {
    require(isNotEmpty())
    val sorted = sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
}
