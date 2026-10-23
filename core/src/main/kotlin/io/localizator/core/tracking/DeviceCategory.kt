package io.localizator.core.tracking

/** Tipo de dispositivo según la *Major Device Class* de Bluetooth. */
enum class DeviceCategory {
    /** Major class Phone (`0x0200`). */
    PHONE,

    /** Cualquier otra major class conocida (ordenador, audio, wearable…). */
    OTHER,

    /** Sin Class of Device: habitual en dispositivos solo-BLE no emparejados. */
    UNKNOWN,
}
