package io.localizator.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import io.localizator.core.beacon.BeaconProtocol
import io.localizator.core.location.LocationPayload
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
 * - El flujo termina con [ScanFailedException] si el sistema rechaza el escaneo, con
 *   [SecurityException] si faltan permisos y con [IllegalStateException] si Bluetooth no está
 *   disponible o se apaga durante el escaneo (Android detiene el escaneo sin notificarlo).
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

        val bluetoothOff = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
                    close(IllegalStateException("Bluetooth apagado"))
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            bluetoothOff,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        scanner.startScan(null, settings, callback)
        awaitClose {
            context.unregisterReceiver(bluetoothOff)
            runCatching { scanner.stopScan(callback) }
        }
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
    val beaconPayload = record?.getServiceData(ParcelUuid(BeaconProtocol.SERVICE_UUID))
    val locationPayload = record?.getServiceData(ParcelUuid(LocationPayload.SERVICE_UUID))
    return ScanSample(advertisement, details, beaconPayload, locationPayload)
}

@SuppressLint("MissingPermission")
private fun ScanResult.cachedDeviceName(): String? = runCatching { device.name }.getOrNull()

@SuppressLint("MissingPermission")
private fun ScanResult.deviceCategory(): DeviceCategory =
    DeviceCategory.fromMajorDeviceClass(runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull())
