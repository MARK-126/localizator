package io.localizator.core.location

/**
 * Resultado de combinar el círculo GPS del protegido con el anillo BLE alrededor del buscador.
 *
 * @property estimate punto del anillo BLE (a la distancia estimada) en la dirección del centro GPS.
 * @property bearingDegrees rumbo del buscador hacia [estimate].
 * @property consistent `true` si el círculo GPS corta el anillo BLE `[bleMin, bleMax]`.
 * @property preferBluetooth `true` si la distancia BLE es menor que la precisión GPS del protegido:
 *   a esa escala el GPS no aporta dirección fiable y la estimación no debe mostrarse.
 */
data class FusedLocation(
    val estimate: GeoPoint,
    val bearingDegrees: Double,
    val consistent: Boolean,
    val preferBluetooth: Boolean,
)

/** Fusión de las dos restricciones de `docs/UBICACION.md`: círculo GPS y anillo BLE. */
object LocationFusion {

    fun fuse(
        seeker: GeoPoint,
        target: GeoPoint,
        targetAccuracyMeters: Double,
        bleDistanceMeters: Double,
        bleMinMeters: Double,
        bleMaxMeters: Double,
    ): FusedLocation {
        val centers = Geo.distanceMeters(seeker, target)
        val bearing = Geo.bearingDegrees(seeker, target)
        val consistent = centers - targetAccuracyMeters <= bleMaxMeters && centers + targetAccuracyMeters >= bleMinMeters
        return FusedLocation(
            estimate = Geo.destination(seeker, bearing, bleDistanceMeters),
            bearingDegrees = bearing,
            consistent = consistent,
            preferBluetooth = bleDistanceMeters < targetAccuracyMeters,
        )
    }
}
