package io.localizator.app.data

import android.content.Context
import io.localizator.core.signal.PathLossModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Calibraciones por teléfono emparejado, persistidas en `SharedPreferences` privadas como
 * `P1m;n` con clave el ID de emparejamiento.
 */
class CalibrationRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutableModels = MutableStateFlow(read())

    val models: StateFlow<Map<String, PathLossModel>> = mutableModels.asStateFlow()

    fun save(id: String, model: PathLossModel) {
        val value = String.format(Locale.US, "%f;%f", model.measuredPowerAt1m, model.pathLossExponent)
        prefs.edit().putString(id, value).apply()
        mutableModels.value = mutableModels.value + (id to model)
    }

    fun remove(id: String) {
        prefs.edit().remove(id).apply()
        mutableModels.value = mutableModels.value - id
    }

    private fun read(): Map<String, PathLossModel> = prefs.all.mapNotNull { (id, value) ->
        val parts = (value as? String)?.split(";") ?: return@mapNotNull null
        val p1m = parts.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
        val n = parts.getOrNull(1)?.toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        id to PathLossModel(p1m, n)
    }.toMap()

    private companion object {
        const val PREFS = "calibration"
    }
}
