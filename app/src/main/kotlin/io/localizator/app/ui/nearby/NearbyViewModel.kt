package io.localizator.app.ui.nearby

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.localizator.app.ble.AdvertisementDetails
import io.localizator.app.ble.BleScanner
import io.localizator.core.tracking.DeviceEstimate
import io.localizator.core.tracking.DeviceTracker
import io.localizator.core.tracking.NearbyFilter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Estado de [NearbyScreen].
 *
 * @property devices dispositivos que pasan [filter], ordenados por distancia ascendente.
 * @property totalCount dispositivos detectados antes de aplicar [filter].
 * @property selected dispositivo abierto en el detalle; `null` si no hay ninguno o dejó de detectarse.
 */
data class NearbyUiState(
    val devices: List<DeviceEstimate> = emptyList(),
    val totalCount: Int = 0,
    val filter: NearbyFilter = NearbyFilter(),
    val selected: SelectedDevice? = null,
)

/** Dispositivo abierto en el detalle: estimación actual y último anuncio en crudo. */
data class SelectedDevice(val estimate: DeviceEstimate, val details: AdvertisementDetails?)

/**
 * Dispositivos cercanos filtrados.
 *
 * Mientras [uiState] tiene suscriptores, escanea y publica `DeviceTracker.snapshot` cada
 * [REFRESH_INTERVAL_MS]. El escaneo se detiene [STOP_TIMEOUT_MS] después del último suscriptor.
 * Si el escaneo falla, se reintenta cada [RETRY_DELAY_MS] (por encima del límite de Android de
 * 5 inicios cada 30 s).
 */
class NearbyViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = BleScanner(application)
    private val tracker = DeviceTracker()
    private val filter = MutableStateFlow(NearbyFilter())
    private val selectedId = MutableStateFlow<String?>(null)
    private val details = ConcurrentHashMap<String, AdvertisementDetails>()

    private val snapshots = channelFlow {
        launch {
            scanner.scan()
                .retryWhen { _, _ ->
                    delay(RETRY_DELAY_MS)
                    true
                }
                .collect { sample ->
                    details[sample.advertisement.id] = sample.details
                    tracker.onAdvertisement(sample.advertisement)
                }
        }
        while (isActive) {
            val snapshot = tracker.snapshot(SystemClock.elapsedRealtime())
            details.keys.retainAll(snapshot.map { it.id }.toSet())
            send(snapshot)
            delay(REFRESH_INTERVAL_MS)
        }
    }

    val uiState: StateFlow<NearbyUiState> = combine(snapshots, filter, selectedId) { devices, currentFilter, currentSelection ->
        NearbyUiState(
            devices = currentFilter.apply(devices),
            totalCount = devices.size,
            filter = currentFilter,
            selected = devices.firstOrNull { it.id == currentSelection }?.let { SelectedDevice(it, details[it.id]) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NearbyUiState())

    fun setMaxDistance(meters: Double) = filter.update { it.copy(maxDistanceMeters = meters) }

    fun setAndroidPhonesOnly(enabled: Boolean) = filter.update { it.copy(androidPhonesOnly = enabled) }

    /** Abre el detalle de [id], o lo cierra con `null`. */
    fun select(id: String?) {
        selectedId.value = id
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 500L
        const val RETRY_DELAY_MS = 6_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
