package io.localizator.core.tracking

import io.localizator.core.signal.KalmanFilter
import io.localizator.core.signal.PathLossModel
import io.localizator.core.signal.TrendDetector
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Estado por dispositivo (filtro, tendencia, calibración) y cálculo de [DeviceEstimate].
 *
 * Selección del modelo, por prioridad: calibración registrada → TX Power anunciado → [defaultModel].
 *
 * Incertidumbre: `σ = √(P + σ_modelo²)`, con P la varianza del filtro y `σ_modelo` de
 * 2 / 4 / 6 dB según [ModelSource]. El intervalo es `[d(RSSI + σ), d(RSSI − σ)]`.
 *
 * Los campos `name`, `txPower` y `manufacturerId` ausentes en un anuncio conservan el último
 * valor conocido. Thread-safe.
 *
 * @param staleAfterMs antigüedad (ms) a partir de la cual [snapshot] descarta un dispositivo.
 */
class DeviceTracker(
    private val defaultModel: PathLossModel = PathLossModel(),
    private val staleAfterMs: Long = 10_000,
    private val filterFactory: () -> KalmanFilter = { KalmanFilter() },
    private val trendFactory: () -> TrendDetector = { TrendDetector() },
) {
    private class State(
        val filter: KalmanFilter,
        val trend: TrendDetector,
        var last: Advertisement,
        var sampleCount: Int = 0,
    )

    private val states = linkedMapOf<String, State>()
    private val calibrations = mutableMapOf<String, PathLossModel>()

    /** Incorpora un anuncio y devuelve la estimación actualizada de su dispositivo. */
    @Synchronized
    fun onAdvertisement(adv: Advertisement): DeviceEstimate {
        val state = states.getOrPut(adv.id) { State(filterFactory(), trendFactory(), adv) }
        state.last = adv.copy(
            name = adv.name ?: state.last.name,
            txPower = adv.txPower ?: state.last.txPower,
            manufacturerId = adv.manufacturerId ?: state.last.manufacturerId,
        )
        state.sampleCount++
        val filtered = state.filter.update(adv.rssi.toDouble(), adv.timestampMs)
        state.trend.add(adv.timestampMs, filtered)
        return estimate(adv.id, state)
    }

    /** Elimina los dispositivos obsoletos y devuelve el resto ordenado por distancia ascendente. */
    @Synchronized
    fun snapshot(nowMs: Long): List<DeviceEstimate> {
        states.entries.removeAll { nowMs - it.value.last.timestampMs > staleAfterMs }
        return states.map { (id, s) -> estimate(id, s) }.sortedBy { it.distanceMeters }
    }

    @Synchronized
    fun estimateFor(id: String): DeviceEstimate? = states[id]?.let { estimate(id, it) }

    @Synchronized
    fun setCalibration(id: String, model: PathLossModel) {
        calibrations[id] = model
    }

    @Synchronized
    fun clearCalibration(id: String) {
        calibrations.remove(id)
    }

    /** Modelo aplicable a [id] y su origen. */
    @Synchronized
    fun modelFor(id: String, txPower: Int?): Pair<PathLossModel, ModelSource> {
        calibrations[id]?.let { return it to ModelSource.CALIBRATED }
        if (txPower != null) {
            return PathLossModel.fromAdvertisedTxPower(txPower, defaultModel.pathLossExponent) to
                ModelSource.ADVERTISED_TX_POWER
        }
        return defaultModel to ModelSource.DEFAULT
    }

    private fun estimate(id: String, s: State): DeviceEstimate {
        val (model, source) = modelFor(id, s.last.txPower)
        val rssi = s.filter.estimate
        val distance = model.distanceMeters(rssi)
        val sigmaDb = sqrt(s.filter.variance + modelErrorDb(source).let { it * it })
        val d1 = model.distanceMeters(rssi + sigmaDb)
        val d2 = model.distanceMeters(rssi - sigmaDb)

        return DeviceEstimate(
            id = id,
            name = s.last.name,
            manufacturerId = s.last.manufacturerId,
            rawRssi = s.last.rssi,
            filteredRssi = rssi,
            distanceMeters = distance,
            distanceMinMeters = min(d1, d2),
            distanceMaxMeters = max(d1, d2),
            zone = ProximityZone.fromDistance(distance),
            trend = s.trend.trend(),
            modelSource = source,
            sampleCount = s.sampleCount,
            lastSeenMs = s.last.timestampMs,
        )
    }

    private fun modelErrorDb(source: ModelSource): Double = when (source) {
        ModelSource.CALIBRATED -> 2.0
        ModelSource.ADVERTISED_TX_POWER -> 4.0
        ModelSource.DEFAULT -> 6.0
    }
}
