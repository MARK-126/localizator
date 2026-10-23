package io.localizator.core.signal

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Suavizado exponencial de un rumbo (grados) sobre el vector unitario `(cos θ, sin θ)`, de modo
 * que el cruce 359° → 0° no produce saltos. No es thread-safe.
 *
 * @param alpha peso de cada lectura nueva, en (0, 1]. 1 = sin suavizado.
 */
class HeadingFilter(private val alpha: Double = 0.15) {
    init {
        require(alpha > 0 && alpha <= 1) { "alpha debe estar en (0, 1]" }
    }

    private var x = Double.NaN
    private var y = Double.NaN

    /** Incorpora [degrees] y devuelve el rumbo suavizado en [0, 360). */
    fun update(degrees: Double): Double {
        val radians = Math.toRadians(degrees)
        if (x.isNaN()) {
            x = cos(radians)
            y = sin(radians)
        } else {
            x += alpha * (cos(radians) - x)
            y += alpha * (sin(radians) - y)
        }
        return normalize(Math.toDegrees(atan2(y, x)))
    }

    fun reset() {
        x = Double.NaN
        y = Double.NaN
    }

    companion object {
        /** [degrees] en [0, 360). */
        fun normalize(degrees: Double): Double = ((degrees % 360) + 360) % 360
    }
}
