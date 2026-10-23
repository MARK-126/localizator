package io.localizator.app.data

import android.content.Context
import android.os.Build
import io.localizator.core.beacon.PairedBeacon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Base64

/**
 * Estado del modo protegido de este teléfono.
 *
 * @property beacon secreto y nombre propios.
 * @property enabled interruptor del faro.
 * @property consentGiven el usuario aceptó la explicación del modo protegido.
 * @property shareLocation compartir la ubicación aproximada cifrada en la respuesta de escaneo.
 */
data class ProtectionState(
    val beacon: PairedBeacon,
    val enabled: Boolean,
    val consentGiven: Boolean,
    val shareLocation: Boolean,
)

/**
 * Persistencia del modo protegido en `SharedPreferences` privadas. El secreto se genera en el primer
 * acceso y no cambia. El nombre por defecto es `Build.MODEL`.
 */
class ProtectionRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(read())

    val state: StateFlow<ProtectionState> = mutableState.asStateFlow()

    fun setName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        prefs.edit().putString(KEY_NAME, trimmed).apply()
        mutableState.update { it.copy(beacon = PairedBeacon(it.beacon.secret, trimmed)) }
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        mutableState.update { it.copy(enabled = enabled) }
    }

    fun setShareLocation(share: Boolean) {
        prefs.edit().putBoolean(KEY_SHARE_LOCATION, share).apply()
        mutableState.update { it.copy(shareLocation = share) }
    }

    fun giveConsent() {
        prefs.edit().putBoolean(KEY_CONSENT, true).apply()
        mutableState.update { it.copy(consentGiven = true) }
    }

    private fun read(): ProtectionState {
        val secret = prefs.getString(KEY_SECRET, null)?.let { Base64.getDecoder().decode(it) }
            ?: PairedBeacon.newSecret().also {
                prefs.edit().putString(KEY_SECRET, Base64.getEncoder().encodeToString(it)).apply()
            }
        val name = prefs.getString(KEY_NAME, null) ?: Build.MODEL
        return ProtectionState(
            beacon = PairedBeacon(secret, name),
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            consentGiven = prefs.getBoolean(KEY_CONSENT, false),
            shareLocation = prefs.getBoolean(KEY_SHARE_LOCATION, false),
        )
    }

    private companion object {
        const val PREFS = "protection"
        const val KEY_SECRET = "secret"
        const val KEY_NAME = "name"
        const val KEY_ENABLED = "enabled"
        const val KEY_CONSENT = "consent"
        const val KEY_SHARE_LOCATION = "share_location"
    }
}
