package io.localizator.app.ble

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import io.localizator.core.tracking.Advertisement
import io.localizator.core.tracking.DeviceCategory
import io.localizator.core.tracking.DeviceNames
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Escaneo BLE continuo como [Flow] de [ScanSample].
 *
 * - `SCAN_MODE_LOW_LATENCY`, sin agrupar resultados (`reportDelay = 0`).
 * - El escaneo empieza al colectar y se detiene al cancelar la colección.
 * - El flujo termina con [ScanFailedException] si el sistema rechaza el escaneo y con
 *   [SecurityException] si faltan permisos o [IllegalStateException] si Bluetooth no está disponible.
 *
 * Android limita a 5 inicios de escaneo por aplicación cada 30 s; los reintentos deben espaciarse.
 */
class BleScanner(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun scan(): Flow<ScanSample> = callbackFlow {
        if (!BluetoothPermissions.allGranted(context)) {
            throw SecurityException("Permisos de Bluetooth no concedidos")
        }
        val scanner = context.bluetoothAdapter()?.takeIf { it.isEnabled }?.bluetoothLeScanner
            ?: throw IllegalStateException("Bluetooth no disponible")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result.toSample())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { trySend(it.toSample()) }
            }

            override fun onScanFailed(errorCode: Int) {
                close(ScanFailedException(errorCode))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        scanner.startScan(null, settings, callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }
}

/** El sistema rechazó el escaneo; [errorCode] es una constante `ScanCallback.SCAN_FAILED_*`. */
class ScanFailedException(val errorCode: Int) : Exception("Escaneo BLE fallido (código $errorCode)")

@SuppressLint("MissingPermission")
private fun ScanResult.toSample(): ScanSample {
    val record = scanRecord
    val manufacturerData = record?.manufacturerSpecificData
    val manufacturerIds = manufacturerData?.let { data -> List(data.size()) { data.keyAt(it) } }.orEmpty()
    val advertisement = Advertisement(
        id = device.address,
        name = DeviceNames.sanitize(record?.deviceName ?: cachedDeviceName()),
        rssi = rssi,
        txPower = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE },
        timestampMs = timestampNanos / 1_000_000,
        manufacturerId = manufacturerIds.firstOrNull(),
        category = deviceCategory(),
        manufacturerIds = manufacturerIds,
    )
    val details = AdvertisementDetails(
        manufacturerData = manufacturerIds.associateWith { id -> manufacturerData?.get(id)?.toHex().orEmpty() },
        serviceUuids = record?.serviceUuids.orEmpty().map { it.shortForm() },
        serviceData = record?.serviceData.orEmpty().entries.associate { (uuid, data) -> uuid.shortForm() to data.toHex() },
        connectable = isConnectable,
        rawBytes = record?.bytes?.toHex().orEmpty(),
    )
    return ScanSample(advertisement, details)
}

@SuppressLint("MissingPermission")
private fun ScanResult.cachedDeviceName(): String? = runCatching { device.name }.getOrNull()

@SuppressLint("MissingPermission")
private fun ScanResult.deviceCategory(): DeviceCategory =
    DeviceCategory.fromMajorDeviceClass(runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull())
