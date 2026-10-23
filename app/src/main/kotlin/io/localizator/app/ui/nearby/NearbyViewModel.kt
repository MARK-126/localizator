package io.localizator.app.ui.nearby

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.localizator.app.ble.BleScanner
import io.localizator.core.tracking.DeviceEstimate
import io.localizator.core.tracking.DeviceTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lista de dispositivos cercanos ordenada por distancia.
 *
 * Mientras [devices] tiene suscriptores, escanea y publica `DeviceTracker.snapshot` cada
 * [REFRESH_INTERVAL_MS]. El escaneo se detiene [STOP_TIMEOUT_MS] después del último suscriptor.
 * Si el escaneo falla, se reintenta cada [RETRY_DELAY_MS] (por encima del límite de Android de
 * 5 inicios cada 30 s).
 */
class NearbyViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = BleScanner(application)
    private val tracker = DeviceTracker()

    val devices: StateFlow<List<DeviceEstimate>> = channelFlow {
        launch {
            scanner.scan()
                .retryWhen { _, _ ->
                    delay(RETRY_DELAY_MS)
                    true
                }
                .collect { tracker.onAdvertisement(it) }
        }
        while (isActive) {
            send(tracker.snapshot(SystemClock.elapsedRealtime()))
            delay(REFRESH_INTERVAL_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private companion object {
        const val REFRESH_INTERVAL_MS = 500L
        const val RETRY_DELAY_MS = 6_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
