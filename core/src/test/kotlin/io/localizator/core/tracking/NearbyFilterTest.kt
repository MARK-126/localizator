package io.localizator.core.tracking

import io.localizator.core.signal.Trend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NearbyFilterTest {
    @Test
    fun `descarta dispositivos mas alla de la distancia maxima`() {
        val devices = listOf(estimate("cerca", 2.0), estimate("lejos", 120.0))
        assertEquals(listOf("cerca"), NearbyFilter().apply(devices).map { it.id })
        assertEquals(listOf("cerca", "lejos"), NearbyFilter(maxDistanceMeters = 200.0).apply(devices).map { it.id })
    }

    @Test
    fun `solo telefonos android aplica la heuristica`() {
        val devices = listOf(
            estimate("samsung", 1.0, manufacturerId = Manufacturers.SAMSUNG),
            estimate("apple", 1.0, manufacturerId = Manufacturers.APPLE),
            estimate("sin-fabricante", 1.0),
            estimate("telefono", 1.0, category = DeviceCategory.PHONE),
        )
        val visible = NearbyFilter(androidPhonesOnly = true).apply(devices).map { it.id }
        assertEquals(listOf("samsung", "telefono"), visible)
    }

    @Test
    fun `heuristica de telefono android`() {
        assertTrue(estimate("a", 1.0, manufacturerId = Manufacturers.GOOGLE).isLikelyAndroidPhone())
        assertTrue(estimate("b", 1.0, category = DeviceCategory.PHONE).isLikelyAndroidPhone())
        assertFalse(
            estimate("c", 1.0, category = DeviceCategory.PHONE, manufacturerId = Manufacturers.APPLE).isLikelyAndroidPhone(),
        )
        assertFalse(
            estimate("d", 1.0, category = DeviceCategory.OTHER, manufacturerId = Manufacturers.SAMSUNG).isLikelyAndroidPhone(),
        )
        assertFalse(estimate("e", 1.0, manufacturerId = 0x0006).isLikelyAndroidPhone())
    }

    @Test
    fun `considera todos los fabricantes del anuncio, no solo el primero`() {
        val est = estimate("f", 1.0, manufacturerIds = listOf(0x0006, Manufacturers.SAMSUNG))
        assertTrue(est.isLikelyAndroidPhone())
    }

    private fun estimate(
        id: String,
        distance: Double,
        manufacturerId: Int? = null,
        category: DeviceCategory = DeviceCategory.UNKNOWN,
        manufacturerIds: List<Int> = listOfNotNull(manufacturerId),
    ) = DeviceEstimate(
        id = id,
        name = null,
        manufacturerId = manufacturerIds.firstOrNull(),
        manufacturerIds = manufacturerIds,
        category = category,
        rawRssi = -60,
        filteredRssi = -60.0,
        distanceMeters = distance,
        distanceMinMeters = distance,
        distanceMaxMeters = distance,
        zone = ProximityZone.fromDistance(distance),
        trend = Trend.UNKNOWN,
        modelSource = ModelSource.DEFAULT,
        sampleCount = 1,
        lastSeenMs = 0,
    )
}
