package io.localizator.core.tracking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ManufacturersTest {
    @Test
    fun `resuelve fabricantes conocidos`() {
        assertEquals("Samsung", Manufacturers.nameOf(0x0075))
        assertEquals("Apple", Manufacturers.nameOf(0x004C))
    }

    @Test
    fun `desconocido o nulo devuelve null`() {
        assertNull(Manufacturers.nameOf(0xFFFF))
        assertNull(Manufacturers.nameOf(null))
    }

    @Test
    fun `etiqueta hexadecimal para desconocidos`() {
        assertEquals("0x01AB", Manufacturers.labelOf(0x01AB))
        assertEquals("Google", Manufacturers.labelOf(0x00E0))
    }
}
