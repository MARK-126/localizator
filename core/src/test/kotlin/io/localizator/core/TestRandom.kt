package io.localizator.core

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/** Muestra de N(0, 1) por Box–Muller. */
fun Random.nextGaussian(): Double {
    val u1 = nextDouble().coerceAtLeast(1e-12)
    val u2 = nextDouble()
    return sqrt(-2 * ln(u1)) * cos(2 * Math.PI * u2)
}
