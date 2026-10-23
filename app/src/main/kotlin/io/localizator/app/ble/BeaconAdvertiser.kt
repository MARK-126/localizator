package io.localizator.app.ble

import android.annotation.SuppressLint
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import io.localizator.core.beacon.BeaconProtocol
import io.localizator.core.location.LocationPayload

/**
 * Emisor del faro: anuncio legacy, no conectable, `ADVERTISE_TX_POWER_MEDIUM`, sin límite de
 * tiempo y con un único Service Data ([BeaconProtocol.SERVICE_UUID] → payload). Opcionalmente,
 * respuesta de escaneo con la ubicación cifrada ([LocationPayload.SERVICE_UUID] → payload), lo que
 * hace el anuncio escaneable.
 *
 * @param onFailure se invoca con la constante `AdvertiseCallback.ADVERTISE_FAILED_*` si el sistema
 *   rechaza el anuncio.
 */
class BeaconAdvertiser(private val context: Context, private val onFailure: (Int) -> Unit) {

    private val callback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) = onFailure(errorCode)
    }

    /**
     * Detiene el anuncio anterior y empieza uno con [payload] y, si no es `null`, la respuesta de
     * escaneo [locationPayload]. [lowPower] usa `ADVERTISE_MODE_LOW_POWER` en lugar de
     * `ADVERTISE_MODE_BALANCED`. Devuelve `false` si no hay emisor BLE.
     */
    @SuppressLint("MissingPermission")
    fun start(payload: ByteArray, locationPayload: ByteArray? = null, lowPower: Boolean = false): Boolean {
        if (!BluetoothPermissions.allGranted(context)) return false
        val advertiser = context.bluetoothAdapter()?.takeIf { it.isEnabled }?.bluetoothLeAdvertiser ?: return false
        runCatching { advertiser.stopAdvertising(callback) }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(
                if (lowPower) AdvertiseSettings.ADVERTISE_MODE_LOW_POWER else AdvertiseSettings.ADVERTISE_MODE_BALANCED,
            )
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceData(ParcelUuid(BeaconProtocol.SERVICE_UUID), payload)
            .build()
        val scanResponse = locationPayload?.let {
            AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceData(ParcelUuid(LocationPayload.SERVICE_UUID), it)
                .build()
        }
        return runCatching { advertiser.startAdvertising(settings, data, scanResponse, callback) }.isSuccess
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val advertiser = context.bluetoothAdapter()?.bluetoothLeAdvertiser ?: return
        runCatching { advertiser.stopAdvertising(callback) }
    }
}
