package io.localizator.core.tracking

import io.localizator.core.signal.Trend

/** Zona de proximidad: IMMEDIATE < 0,5 m ≤ NEAR < 2,5 m ≤ FAR. UNKNOWN si la distancia es `NaN`. */
enum class ProximityZone {
    IMMEDIATE, NEAR, FAR, UNKNOWN;

    companion object {
        fun fromDistance(meters: Double): ProximityZone = when {
            meters.isNaN() -> UNKNOWN
            meters < 0.5 -> IMMEDIATE
            meters < 2.5 -> NEAR
            else -> FAR
        }
    }
}

/** Origen del P1m usado en la estimación, de mayor a menor fiabilidad. */
enum class ModelSource { CALIBRATED, ADVERTISED_TX_POWER, DEFAULT }

/**
 * Estimación de posición relativa de un dispositivo.
 *
 * @property rawRssi último RSSI recibido (dBm).
 * @property filteredRssi RSSI filtrado (dBm).
 * @property distanceMeters distancia estimada (m).
 * @property distanceMinMeters límite inferior del intervalo ±1σ (m).
 * @property distanceMaxMeters límite superior del intervalo ±1σ (m).
 * @property sampleCount anuncios recibidos desde que se empezó a seguir.
 * @property lastSeenMs instante del último anuncio (ms).
 */
data class DeviceEstimate(
    val id: String,
    val name: String?,
    val manufacturerId: Int?,
    val category: DeviceCategory,
    val rawRssi: Int,
    val filteredRssi: Double,
    val distanceMeters: Double,
    val distanceMinMeters: Double,
    val distanceMaxMeters: Double,
    val zone: ProximityZone,
    val trend: Trend,
    val modelSource: ModelSource,
    val sampleCount: Int,
    val lastSeenMs: Long,
)
