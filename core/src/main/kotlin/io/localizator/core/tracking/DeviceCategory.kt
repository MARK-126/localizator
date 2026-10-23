package io.localizator.core.tracking

/** Tipo de dispositivo según la *Major Device Class* de Bluetooth. */
enum class DeviceCategory {
    /** Major class Phone (`0x0200`). */
    PHONE,

    /** Cualquier otra major class conocida (ordenador, audio, wearable…). */
    OTHER,

    /** Sin Class of Device útil: habitual en dispositivos solo-BLE no emparejados. */
    UNKNOWN;

    companion object {
        const val MAJOR_MISC = 0x0000
        const val MAJOR_PHONE = 0x0200
        const val MAJOR_UNCATEGORIZED = 0x1F00

        /**
         * Categoría para una *Major Device Class*. `null`, [MAJOR_MISC] y [MAJOR_UNCATEGORIZED] dan
         * [UNKNOWN]: Android devuelve `0x0000` cuando no conoce la clase del dispositivo.
         */
        fun fromMajorDeviceClass(major: Int?): DeviceCategory = when (major) {
            null, MAJOR_MISC, MAJOR_UNCATEGORIZED -> UNKNOWN
            MAJOR_PHONE -> PHONE
            else -> OTHER
        }
    }
}
