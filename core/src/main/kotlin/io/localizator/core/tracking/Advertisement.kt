package io.localizator.core.tracking

/**
 * Anuncio BLE recibido, independiente de la API de Android.
 *
 * @property id identificador del emisor: dirección MAC o ID de faro.
 * @property rssi intensidad recibida (dBm).
 * @property txPower TX Power Level anunciado (dBm a 0 m), si existe.
 * @property timestampMs instante de recepción (ms, reloj monotónico).
 * @property manufacturerId Company Identifier del Bluetooth SIG, si existe.
 */
data class Advertisement(
    val id: String,
    val name: String?,
    val rssi: Int,
    val txPower: Int? = null,
    val timestampMs: Long,
    val manufacturerId: Int? = null,
)
