package io.localizator.core.tracking

/** Normalización de nombres anunciados por BLE. */
object DeviceNames {

    /**
     * Devuelve [raw] sin espacios en los extremos, o `null` si queda vacío o contiene caracteres de
     * control o `U+FFFD` (bytes no decodificables como UTF-8).
     */
    fun sanitize(raw: String?): String? {
        val name = raw?.trim().orEmpty()
        if (name.isEmpty()) return null
        if (name.any { it == '�' || it.isISOControl() }) return null
        return name
    }
}
