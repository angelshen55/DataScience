package com.aisleron.domain.aisle.usecase

import com.aisleron.data.TestDataManager
import com.aisleron.domain.aisle.Aisle
import com.aisleron.domain.aisle.AisleRepository
import com.aisleron.domain.FilterType
import com.aisleron.domain.location.Location
import com.aisleron.domain.location.LocationRepository
import com.aisleron.domain.location.LocationType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetDefaultAisleForLocationUseCaseTest {
    private lateinit var testData: TestDataManager
    private lateinit var aisleRepository: AisleRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var getDefaultAisleForLocationUseCase: GetDefaultAisleForLocationUseCase

    @BeforeEach
    fun setUp() {
        testData = TestDataManager(addData = false)
        aisleRepository = testData.getRepository()
        locationRepository = testData.getRepository()
        getDefaultAisleForLocationUseCase = GetDefaultAisleForLocationUseCase(aisleRepository)
    }

    @Test
    fun getDefaultAisle_WithDefault_ReturnsDefaultAisle() {
        val locationId = runBlocking {
            locationRepository.add(
                Location(
                    id = 0,
                    type = LocationType.SHOP,
                    defaultFilter = FilterType.NEEDED,
                    name = "Shop-Test",
                    pinned = false,
                    aisles = emptyList(),
                    showDefaultAisle = true
                )
            )
        }
        val defaultAisleId = runBlocking {
            aisleRepository.add(
                Aisle(
                    id = 0,
                    name = "Default",
                    products = emptyList(),
                    locationId = locationId,
                    rank = 1000,
                    isDefault = true,
                    expanded = true
                )
            )
        }
        val defaultAisle = runBlocking { getDefaultAisleForLocationUseCase(locationId) }
        assertEquals(defaultAisleId, defaultAisle?.id)
    }

    @Test
    fun getDefaultAisle_WithoutDefault_ReturnsNull() {
        val locationId = runBlocking {
            locationRepository.add(
                Location(
                    id = 0,
                    type = LocationType.SHOP,
                    defaultFilter = FilterType.NEEDED,
                    name = "Shop-NoDefault",
                    pinned = false,
                    aisles = emptyList(),
                    showDefaultAisle = false
                )
            )
        }
        val defaultAisle = runBlocking { getDefaultAisleForLocationUseCase(locationId) }
        assertNull(defaultAisle)
    }
}
