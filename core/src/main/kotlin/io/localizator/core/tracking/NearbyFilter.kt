package io.localizator.core.tracking

/**
 * Filtro de la lista de dispositivos cercanos.
 *
 * @property maxDistanceMeters distancia estimada máxima (m) de los dispositivos visibles.
 * @property androidPhonesOnly si es `true`, solo deja los que cumplen [isLikelyAndroidPhone].
 */
data class NearbyFilter(
    val maxDistanceMeters: Double = DEFAULT_MAX_DISTANCE_METERS,
    val androidPhonesOnly: Boolean = false,
) {
    fun apply(devices: List<DeviceEstimate>): List<DeviceEstimate> = devices.filter {
        it.distanceMeters <= maxDistanceMeters && (!androidPhonesOnly || it.isLikelyAndroidPhone())
    }

    companion object {
        const val DEFAULT_MAX_DISTANCE_METERS = 30.0
    }
}

/** Fabricantes cuyo Company ID, sin Class of Device, se considera indicio de teléfono Android. */
val ANDROID_PHONE_MANUFACTURERS: Set<Int> = setOf(Manufacturers.SAMSUNG, Manufacturers.GOOGLE)

/**
 * Heurística de teléfono Android, sobre todos los fabricantes del anuncio:
 * - [DeviceCategory.PHONE] y ninguno es Apple, o
 * - [DeviceCategory.UNKNOWN] y alguno está en [ANDROID_PHONE_MANUFACTURERS].
 *
 * Admite falsos positivos (otros dispositivos Samsung/Google) y falsos negativos (teléfonos que
 * no anuncian fabricante).
 */
fun DeviceEstimate.isLikelyAndroidPhone(): Boolean = when (category) {
    DeviceCategory.PHONE -> Manufacturers.APPLE !in manufacturerIds
    DeviceCategory.OTHER -> false
    DeviceCategory.UNKNOWN -> manufacturerIds.any { it in ANDROID_PHONE_MANUFACTURERS }
}
