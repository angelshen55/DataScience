/*
 * Copyright (C) 2025 aisleron.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.aisleron.domain.product.usecase

import com.aisleron.data.TestDataManager
import com.aisleron.domain.aisle.AisleRepository
import com.aisleron.domain.aisle.usecase.AddAisleUseCaseImpl
import com.aisleron.domain.aisle.usecase.GetAisleUseCaseImpl
import com.aisleron.domain.aisle.usecase.GetDefaultAislesUseCase
import com.aisleron.domain.aisle.usecase.IsAisleNameUniqueUseCase
import com.aisleron.domain.aisleproduct.AisleProductRepository
import com.aisleron.domain.aisleproduct.usecase.AddAisleProductsUseCase
import com.aisleron.domain.aisleproduct.usecase.GetAisleMaxRankUseCase
import com.aisleron.domain.location.LocationRepository
import com.aisleron.domain.location.usecase.GetHomeLocationUseCase
import com.aisleron.domain.location.usecase.GetLocationUseCase
import com.aisleron.domain.product.Product
import com.aisleron.domain.product.ProductRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class UpdateProductStatusUseCaseTest {

    private lateinit var testData: TestDataManager
    private lateinit var updateProductStatusUseCase: UpdateProductStatusUseCase

    @BeforeEach
    fun setUp() {
        testData = TestDataManager()
        val productRepository = testData.getRepository<ProductRepository>()
        val aisleRepository = testData.getRepository<AisleRepository>()
        val locationRepository = testData.getRepository<LocationRepository>()
        val aisleProductRepository = testData.getRepository<AisleProductRepository>()
        val getLocationUseCase = GetLocationUseCase(locationRepository)
        val addAisleUseCase = AddAisleUseCaseImpl(
            aisleRepository,
            getLocationUseCase,
            IsAisleNameUniqueUseCase(aisleRepository)
        )
        val addAisleProductsUseCase = AddAisleProductsUseCase(aisleProductRepository)
        updateProductStatusUseCase = UpdateProductStatusUseCaseImpl(
            GetProductUseCase(productRepository),
            UpdateProductUseCase(
                productRepository,
                testData.getRepository<com.aisleron.domain.record.RecordRepository>(),
                IsProductNameUniqueUseCase(productRepository),
                GetDefaultAislesUseCase(aisleRepository),
                getLocationUseCase
            ),
            GetHomeLocationUseCase(locationRepository),
            GetAisleUseCaseImpl(aisleRepository),
            getLocationUseCase,
            aisleRepository,
            addAisleUseCase,
            aisleProductRepository,
            addAisleProductsUseCase,
            GetAisleMaxRankUseCase(aisleProductRepository)
        )
    }

    @ParameterizedTest(name = "Test when inStock Status is {0}")
    @MethodSource("inStockArguments")
    fun updateProductStatus_ProductExists_StatusUpdated(inStock: Boolean) {
        val existingProduct: Product
        val updatedProduct = runBlocking {
            existingProduct = testData.getRepository<ProductRepository>().getAll().first()
            updateProductStatusUseCase(existingProduct.id, inStock)
        }

        assertNotNull(updatedProduct)
        assertEquals(existingProduct.id, updatedProduct?.id)
        assertEquals(existingProduct.name, updatedProduct?.name)
        assertEquals(inStock, updatedProduct?.inStock)
    }

    @Test
    fun updateProductStatus_ProductDoesNotExist_ReturnNull() {
        val updatedProduct = runBlocking {
            updateProductStatusUseCase(1001, true)
        }

        assertNull(updatedProduct)
    }

    private companion object {
        @JvmStatic
        fun inStockArguments(): Stream<Arguments> = Stream.of(
            Arguments.of(true),
            Arguments.of(false)
        )
    }
}