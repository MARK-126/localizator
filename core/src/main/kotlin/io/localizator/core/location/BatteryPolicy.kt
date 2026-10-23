package io.localizator.core.location

/** Modo de energía del teléfono protegido según su batería. */
enum class PowerMode {
    /** GPS cada 30 s con alta precisión; faro equilibrado. */
    NORMAL,

    /** GPS cada 5 min con precisión equilibrada; faro equilibrado. */
    SAVING,

    /** GPS apagado (se anuncia la última posición); faro en bajo consumo. */
    CRITICAL,
}

/**
 * Umbrales de batería: [savingBelowPercent] (30 %) y [criticalBelowPercent] (15 %). Un dispositivo
 * cargando se trata siempre como [PowerMode.NORMAL].
 */
class BatteryPolicy(
    private val savingBelowPercent: Int = 30,
    private val criticalBelowPercent: Int = 15,
) {
    init {
        require(criticalBelowPercent < savingBelowPercent) { "El umbral crítico debe ser menor que el de ahorro" }
    }

    fun modeFor(levelPercent: Int, charging: Boolean): PowerMode = when {
        charging || levelPercent >= savingBelowPercent -> PowerMode.NORMAL
        levelPercent >= criticalBelowPercent -> PowerMode.SAVING
        else -> PowerMode.CRITICAL
    }

    companion object {
        fun gpsIntervalMs(mode: PowerMode): Long? = when (mode) {
            PowerMode.NORMAL -> 30_000
            PowerMode.SAVING -> 300_000
            PowerMode.CRITICAL -> null
        }
    }
}
