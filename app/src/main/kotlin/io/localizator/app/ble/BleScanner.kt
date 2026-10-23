package io.localizator.app.ble

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import io.localizator.core.tracking.Advertisement
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Escaneo BLE continuo como [Flow] de [Advertisement].
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
    fun scan(): Flow<Advertisement> = callbackFlow {
        if (!BluetoothPermissions.allGranted(context)) {
            throw SecurityException("Permisos de Bluetooth no concedidos")
        }
        val scanner = context.bluetoothAdapter()?.takeIf { it.isEnabled }?.bluetoothLeScanner
            ?: throw IllegalStateException("Bluetooth no disponible")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result.toAdvertisement())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { trySend(it.toAdvertisement()) }
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
private fun ScanResult.toAdvertisement(): Advertisement {
    val record = scanRecord
    val manufacturerData = record?.manufacturerSpecificData
    return Advertisement(
        id = device.address,
        name = record?.deviceName ?: runCatching { device.name }.getOrNull(),
        rssi = rssi,
        txPower = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE },
        timestampMs = timestampNanos / 1_000_000,
        manufacturerId = manufacturerData?.takeIf { it.size() > 0 }?.keyAt(0),
    )
}
