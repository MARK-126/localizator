package io.localizator.core.tracking

/** Nombres de fabricantes por Company Identifier del Bluetooth SIG (Assigned Numbers). */
object Manufacturers {

    private val names = mapOf(
        0x0006 to "Microsoft",
        0x004C to "Apple",
        0x0075 to "Samsung",
        0x0087 to "Garmin",
        0x009E to "Bose",
        0x00E0 to "Google",
        0x012D to "Sony",
    )

    /** Nombre del fabricante, o `null` si [companyId] es `null` o no está en la tabla. */
    fun nameOf(companyId: Int?): String? = companyId?.let(names::get)

    /** Nombre del fabricante o, si no está en la tabla, el identificador en hexadecimal (`0x01AB`). */
    fun labelOf(companyId: Int): String = nameOf(companyId) ?: "0x%04X".format(companyId)
}
