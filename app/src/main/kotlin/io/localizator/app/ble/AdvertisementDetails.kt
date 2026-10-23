package io.localizator.app.ble

import android.os.ParcelUuid
import io.localizator.core.tracking.Advertisement
import java.util.Locale

/**
 * Contenido en crudo de un anuncio BLE, para diagnóstico.
 *
 * @property manufacturerData Company ID → datos en hexadecimal.
 * @property serviceUuids UUIDs de servicio anunciados, en forma corta (`0xFEF3`) si son de 16 bits.
 * @property serviceData UUID de servicio → datos en hexadecimal.
 * @property connectable si el anuncio es conectable.
 * @property rawBytes anuncio completo (AD structures) en hexadecimal.
 */
data class AdvertisementDetails(
    val manufacturerData: Map<Int, String>,
    val serviceUuids: List<String>,
    val serviceData: Map<String, String>,
    val connectable: Boolean,
    val rawBytes: String,
)

/**
 * Resultado de escaneo: anuncio para el dominio, contenido en crudo y, si existe, el Service Data
 * del faro Localizator.
 */
class ScanSample(
    val advertisement: Advertisement,
    val details: AdvertisementDetails,
    val beaconPayload: ByteArray?,
)

private const val BASE_UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb"

/** UUID en forma corta `0xXXXX` si deriva del Bluetooth Base UUID con 16 bits, o completo en otro caso. */
internal fun ParcelUuid.shortForm(): String {
    val text = uuid.toString()
    return if (text.startsWith("0000") && text.endsWith(BASE_UUID_SUFFIX)) {
        "0x" + text.substring(4, 8).uppercase()
    } else {
        text
    }
}

internal fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(Locale.US, it) }
