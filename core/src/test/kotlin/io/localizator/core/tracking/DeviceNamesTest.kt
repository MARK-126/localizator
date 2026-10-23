package io.localizator.core.tracking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceNamesTest {
    @Test
    fun `conserva nombres validos sin espacios en los extremos`() {
        assertEquals("Galaxy S23", DeviceNames.sanitize("  Galaxy S23 "))
    }

    @Test
    fun `descarta nombres vacios o nulos`() {
        assertNull(DeviceNames.sanitize(null))
        assertNull(DeviceNames.sanitize("   "))
    }

    @Test
    fun `descarta nombres con bytes no decodificables o de control`() {
        assertNull(DeviceNames.sanitize("C145E07404089268268�C"))
        assertNull(DeviceNames.sanitize("abc\u0001"))
    }
}
