package io.localizator.core.signal

import kotlin.math.ln

/**
 * Intervalo entre pulsos de vibración según la distancia, interpolado en escala logarítmica entre
 * [nearMeters] → [minIntervalMs] y [farMeters] → [maxIntervalMs], con saturación fuera del rango.
 */
class ProximityFeedback(
    private val nearMeters: Double = 0.5,
    private val farMeters: Double = 10.0,
    private val minIntervalMs: Long = 150,
    private val maxIntervalMs: Long = 1_500,
) {
    init {
        require(nearMeters > 0 && farMeters > nearMeters) { "Se requiere 0 < nearMeters < farMeters" }
        require(minIntervalMs in 1..maxIntervalMs) { "Se requiere 0 < minIntervalMs ≤ maxIntervalMs" }
    }

    fun pulseIntervalMs(distanceMeters: Double): Long {
        if (distanceMeters.isNaN() || distanceMeters >= farMeters) return maxIntervalMs
        if (distanceMeters <= nearMeters) return minIntervalMs
        val t = ln(distanceMeters / nearMeters) / ln(farMeters / nearMeters)
        return (minIntervalMs + t * (maxIntervalMs - minIntervalMs)).toLong()
    }
}
