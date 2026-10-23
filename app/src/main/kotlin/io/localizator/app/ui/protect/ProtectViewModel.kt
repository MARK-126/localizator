package io.localizator.app.ui.protect

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.localizator.app.data.ProtectionState
import io.localizator.app.localizatorApp
import io.localizator.app.service.ProtectionService
import io.localizator.core.beacon.PairingCode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Estado de [ProtectScreen].
 *
 * @property pairingCode contenido del QR de emparejamiento.
 */
data class ProtectUiState(
    val name: String,
    val enabled: Boolean,
    val consentGiven: Boolean,
    val pairingCode: String,
)

/** Modo protegido de este teléfono: nombre, consentimiento e interruptor del faro. */
class ProtectViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = application.localizatorApp.protection

    val uiState: StateFlow<ProtectUiState> = repository.state
        .map { it.toUi() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.state.value.toUi())

    fun setName(name: String) = repository.setName(name)

    fun giveConsent() = repository.giveConsent()

    /** Activa o desactiva el faro. Activarlo requiere consentimiento previo. */
    fun setEnabled(enabled: Boolean) {
        val app = getApplication<Application>()
        if (enabled && !repository.state.value.consentGiven) return
        repository.setEnabled(enabled)
        if (enabled) ProtectionService.start(app) else ProtectionService.stop(app)
    }

    private fun ProtectionState.toUi() = ProtectUiState(
        name = beacon.name,
        enabled = enabled,
        consentGiven = consentGiven,
        pairingCode = PairingCode.format(beacon),
    )
}
