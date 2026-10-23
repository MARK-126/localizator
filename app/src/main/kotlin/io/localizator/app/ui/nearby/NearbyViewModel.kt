package io.localizator.app.ui.nearby

import android.app.Application
import android.location.Location
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.localizator.app.ble.AdvertisementDetails
import io.localizator.app.ble.BleScanner
import io.localizator.app.ble.ScanSample
import io.localizator.app.localizatorApp
import io.localizator.app.location.LocationSource
import io.localizator.app.sensors.CompassProvider
import io.localizator.core.beacon.BeaconCodec
import io.localizator.core.beacon.BeaconMatcher
import io.localizator.core.beacon.PairedBeacon
import io.localizator.core.beacon.PairingCode
import io.localizator.core.location.LocationPayload
import io.localizator.core.location.SharedLocation
import io.localizator.core.signal.HeadingFilter
import io.localizator.core.signal.PathLossModel
import io.localizator.core.tracking.Advertisement
import io.localizator.core.tracking.DeviceEstimate
import io.localizator.core.tracking.DeviceTracker
import io.localizator.core.tracking.NearbyFilter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Estado de [NearbyScreen].
 *
 * @property myPhones teléfonos emparejados, detectados o no; no les afecta [filter].
 * @property devices resto de dispositivos que pasan [filter], ordenados por distancia ascendente.
 * @property totalCount dispositivos no emparejados detectados antes de aplicar [filter].
 * @property selected dispositivo abierto en el detalle; `null` si no hay ninguno o dejó de detectarse.
 */
data class NearbyUiState(
    val myPhones: List<MyPhone> = emptyList(),
    val devices: List<DeviceEstimate> = emptyList(),
    val totalCount: Int = 0,
    val filter: NearbyFilter = NearbyFilter(),
    val selected: SelectedDevice? = null,
)

/**
 * Teléfono emparejado, su estimación actual (`null` si no se detecta) y la última ubicación GPS que
 * compartió (`null` si no la comparte o aún no se recibió).
 */
data class MyPhone(val beacon: PairedBeacon, val estimate: DeviceEstimate?, val location: ReceivedLocation? = null)

/**
 * Ubicación compartida por un teléfono emparejado.
 *
 * @property receivedAtMs instante de recepción (`SystemClock.elapsedRealtime`).
 */
data class ReceivedLocation(val location: SharedLocation, val receivedAtMs: Long) {
    /** Antigüedad total (s) en [nowMs]: la anunciada más el tiempo desde la recepción. */
    fun ageSeconds(nowMs: Long): Long = location.ageSeconds + (nowMs - receivedAtMs) / 1000
}

/** Dispositivo abierto en el detalle: estimación actual y último anuncio en crudo. */
data class SelectedDevice(val estimate: DeviceEstimate, val details: AdvertisementDetails?)

/**
 * Dispositivos cercanos y teléfonos emparejados.
 *
 * Mientras [uiState] tiene suscriptores, escanea y publica `DeviceTracker.snapshot` cada
 * [REFRESH_INTERVAL_MS]. El escaneo se detiene [STOP_TIMEOUT_MS] después del último suscriptor.
 * Si el escaneo falla, se reintenta cada [RETRY_DELAY_MS] (por encima del límite de Android de
 * 5 inicios cada 30 s).
 *
 * Un anuncio con payload de faro que coincide con un teléfono emparejado se registra con el ID
 * estable del emparejamiento ([PairedBeacon.id]) y su nombre, en lugar de la MAC. Si el payload
 * trae P1m, se usa como calibración de ese teléfono. Si el mismo resultado trae la respuesta de
 * escaneo con ubicación, se descifra con el secreto del emparejamiento y el ID rotativo del anuncio.
 */
class NearbyViewModel(application: Application) : AndroidViewModel(application) {

    private val pairedRepository = application.localizatorApp.pairedBeacons
    private val scanner = BleScanner(application)
    private val tracker = DeviceTracker()
    private val filter = MutableStateFlow(NearbyFilter())
    private val selectedId = MutableStateFlow<String?>(null)
    private val details = ConcurrentHashMap<String, AdvertisementDetails>()
    private val locations = ConcurrentHashMap<String, ReceivedLocation>()
    private val locationSource = LocationSource(application)

    private val snapshots = channelFlow {
        launch {
            scanner.scan()
                .retryWhen { _, _ ->
                    delay(RETRY_DELAY_MS)
                    true
                }
                .collect { sample ->
                    val advertisement = resolve(sample)
                    details[advertisement.id] = sample.details
                    tracker.onAdvertisement(advertisement)
                }
        }
        while (isActive) {
            val snapshot = tracker.snapshot(SystemClock.elapsedRealtime())
            details.keys.retainAll(snapshot.map { it.id }.toSet())
            send(snapshot)
            delay(REFRESH_INTERVAL_MS)
        }
    }

    val uiState: StateFlow<NearbyUiState> = combine(
        snapshots,
        filter,
        selectedId,
        pairedRepository.beacons,
    ) { devices, currentFilter, currentSelection, paired ->
        val byId = devices.associateBy { it.id }
        val pairedIds = paired.map { it.id }.toSet()
        val others = devices.filterNot { it.id in pairedIds }
        NearbyUiState(
            myPhones = paired.map { MyPhone(it, byId[it.id], locations[it.id]) },
            devices = currentFilter.apply(others),
            totalCount = others.size,
            filter = currentFilter,
            selected = byId[currentSelection]?.let { SelectedDevice(it, details[it.id]) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NearbyUiState())

    private val compass = CompassProvider(application)

    /** `true` si el dispositivo tiene sensor de rotación para la brújula. */
    val compassAvailable: Boolean = compass.isAvailable

    /** Rumbo suavizado con [HeadingFilter] (grados, [0, 360)), o `null` hasta la primera lectura. */
    val heading: StateFlow<Double?> = flow {
        val filter = HeadingFilter()
        emitAll(compass.azimuthDegrees().map { filter.update(it) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    @Volatile
    private var matcher = BeaconMatcher(pairedRepository.beacons.value)

    init {
        viewModelScope.launch {
            pairedRepository.beacons.collect { matcher = BeaconMatcher(it) }
        }
    }

    fun setMaxDistance(meters: Double) = filter.update { it.copy(maxDistanceMeters = meters) }

    fun setAndroidPhonesOnly(enabled: Boolean) = filter.update { it.copy(androidPhonesOnly = enabled) }

    /** Abre el detalle de [id], o lo cierra con `null`. */
    fun select(id: String?) {
        selectedId.value = id
    }

    /** Empareja el teléfono del código [text]. Devuelve el emparejado, o `null` si el código no es válido. */
    fun pair(text: String): PairedBeacon? = PairingCode.parse(text)?.also(pairedRepository::add)

    /** Olvida el teléfono emparejado [id] y cierra su detalle. */
    fun forget(id: String) {
        pairedRepository.remove(id)
        tracker.clearCalibration(id)
        locations.remove(id)
        select(null)
    }

    /** `true` si hay permiso de ubicación para mostrar la posición propia en el mapa. */
    fun hasLocationPermission(): Boolean = locationSource.hasPermission()

    /** Posiciones propias cada [OWN_LOCATION_INTERVAL_MS] con alta precisión; requiere permiso de ubicación. */
    fun ownLocationUpdates(): Flow<Location> = locationSource.updates(OWN_LOCATION_INTERVAL_MS, highAccuracy = true)

    private fun resolve(sample: ScanSample): Advertisement {
        val payload = sample.beaconPayload?.let(BeaconCodec::decode) ?: return sample.advertisement
        val beacon = matcher.match(payload, System.currentTimeMillis()) ?: return sample.advertisement
        payload.measuredPowerAt1m?.let { tracker.setCalibration(beacon.id, PathLossModel(it.toDouble())) }
        sample.locationPayload
            ?.let { LocationPayload.decode(it, beacon.secret, payload.rotatingId) }
            ?.let { locations[beacon.id] = ReceivedLocation(it, SystemClock.elapsedRealtime()) }
        return sample.advertisement.copy(id = beacon.id, name = beacon.name)
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 500L
        const val RETRY_DELAY_MS = 6_000L
        const val STOP_TIMEOUT_MS = 5_000L
        const val OWN_LOCATION_INTERVAL_MS = 5_000L
    }
}
