package io.localizator.core.tracking

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceCategoryTest {
    @Test
    fun `clase desconocida o miscelanea es UNKNOWN`() {
        assertEquals(DeviceCategory.UNKNOWN, DeviceCategory.fromMajorDeviceClass(null))
        assertEquals(DeviceCategory.UNKNOWN, DeviceCategory.fromMajorDeviceClass(0x0000))
        assertEquals(DeviceCategory.UNKNOWN, DeviceCategory.fromMajorDeviceClass(0x1F00))
    }

    @Test
    fun `telefono y otras clases`() {
        assertEquals(DeviceCategory.PHONE, DeviceCategory.fromMajorDeviceClass(0x0200))
        assertEquals(DeviceCategory.OTHER, DeviceCategory.fromMajorDeviceClass(0x0400))
    }

    @Test
    fun `samsung sin clase conocida pasa el filtro android`() {
        val adv = Advertisement(
            id = "75:5D:98:49:4C:1D",
            name = null,
            rssi = -29,
            timestampMs = 0,
            manufacturerId = Manufacturers.SAMSUNG,
            category = DeviceCategory.fromMajorDeviceClass(0x0000),
        )
        val estimate = DeviceTracker().onAdvertisement(adv)
        assertEquals(true, estimate.isLikelyAndroidPhone())
    }
}
