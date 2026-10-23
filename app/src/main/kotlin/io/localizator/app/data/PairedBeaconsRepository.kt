package io.localizator.app.data

import android.content.Context
import io.localizator.core.beacon.PairedBeacon
import io.localizator.core.beacon.PairingCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Teléfonos emparejados en este buscador, persistidos como códigos de emparejamiento en
 * `SharedPreferences` privadas. Un emparejamiento con el mismo ID reemplaza al anterior.
 */
class PairedBeaconsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutableBeacons = MutableStateFlow(read())

    /** Emparejados, ordenados por nombre. */
    val beacons: StateFlow<List<PairedBeacon>> = mutableBeacons.asStateFlow()

    fun add(beacon: PairedBeacon) = save(mutableBeacons.value.filterNot { it.id == beacon.id } + beacon)

    fun remove(id: String) = save(mutableBeacons.value.filterNot { it.id == id })

    private fun save(beacons: List<PairedBeacon>) {
        prefs.edit().putStringSet(KEY_CODES, beacons.map(PairingCode::format).toSet()).apply()
        mutableBeacons.value = beacons.sortedBy { it.name.lowercase() }
    }

    private fun read(): List<PairedBeacon> =
        prefs.getStringSet(KEY_CODES, emptySet()).orEmpty()
            .mapNotNull(PairingCode::parse)
            .sortedBy { it.name.lowercase() }

    private companion object {
        const val PREFS = "paired_beacons"
        const val KEY_CODES = "codes"
    }
}
