package io.localizator.app

import android.app.Application
import android.content.Context
import io.localizator.app.data.PairedBeaconsRepository
import io.localizator.app.data.ProtectionRepository

/** Aplicación: contenedor de los repositorios compartidos. */
class LocalizatorApp : Application() {
    val protection: ProtectionRepository by lazy { ProtectionRepository(this) }
    val pairedBeacons: PairedBeaconsRepository by lazy { PairedBeaconsRepository(this) }
}

val Context.localizatorApp: LocalizatorApp get() = applicationContext as LocalizatorApp
