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
import com.aisleron.domain.aisle.usecase.GetDefaultAislesUseCase
import com.aisleron.domain.location.LocationRepository
import com.aisleron.domain.location.usecase.GetLocationUseCase
import com.aisleron.domain.product.Product
import com.aisleron.domain.product.ProductRepository
import com.aisleron.domain.record.Record
import com.aisleron.domain.record.RecordRepository
import com.aisleron.domain.record.ProductPurchaseCount
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.Date
import java.util.stream.Stream

class UpdateProductPriceUseCaseTest {

    private lateinit var testData: TestDataManager
    private lateinit var updateProductPriceUseCase: UpdateProductPriceUseCase
    private lateinit var existingProduct: Product

    @BeforeEach
    fun setUp() {
        testData = TestDataManager()
        val productRepository = testData.getRepository<ProductRepository>()
        val aisleRepository = testData.getRepository<AisleRepository>()
        val locationRepository = testData.getRepository<LocationRepository>()
        existingProduct = runBlocking { productRepository.get(1)!! }
        
        // Create a stub RecordRepository for testing
        val recordRepository = object : RecordRepository {
            override suspend fun get(id: Int) = null
            override suspend fun getAll() = emptyList<Record>()
            override suspend fun add(item: Record) = 0
            override suspend fun add(items: List<Record>) = emptyList<Int>()
            override suspend fun update(item: Record) {}
            override suspend fun update(items: List<Record>) {}
            override suspend fun remove(item: Record) {}
            override suspend fun getRecordsByProduct(productId: Int) = emptyList<Record>()
            override suspend fun getRecordsByDateRange(startDate: Long, endDate: Long) = emptyList<Record>()
            override suspend fun getProductPurchaseCounts(): List<ProductPurchaseCount> = emptyList()
            override suspend fun getPurchaseDatesForProduct(productId: Int): List<Date> = emptyList()
        }
        
        val getProductUseCase = GetProductUseCase(productRepository)
        val updateProductUseCase = UpdateProductUseCase(
            productRepository,
            recordRepository,
            IsProductNameUniqueUseCase(productRepository),
            GetDefaultAislesUseCase(aisleRepository),
            GetLocationUseCase(locationRepository)
        )
        
        updateProductPriceUseCase = UpdateProductPriceUseCaseImpl(
            getProductUseCase,
            updateProductUseCase
        )
    }

    @Test
    fun updateProductPrice_ProductExists_PriceUpdated() {
        val newPrice = 15.99
        val updatedProduct: Product?
        
        runBlocking {
            updatedProduct = updateProductPriceUseCase(existingProduct.id, newPrice)
        }
        
        assertNotNull(updatedProduct)
        assertEquals(existingProduct.id, updatedProduct?.id)
        assertEquals(existingProduct.name, updatedProduct?.name)
        assertEquals(existingProduct.inStock, updatedProduct?.inStock)
        assertEquals(existingProduct.qtyNeeded, updatedProduct?.qtyNeeded)
        assertEquals(newPrice, updatedProduct?.price)
    }

    @Test
    fun updateProductPrice_ProductDoesNotExist_ReturnsNull() {
        val nonExistentProductId = 99999
        val newPrice = 10.50
        val updatedProduct: Product?
        
        runBlocking {
            updatedProduct = updateProductPriceUseCase(nonExistentProductId, newPrice)
        }
        
        assertNull(updatedProduct)
    }

    @Test
    fun updateProductPrice_NegativePrice_ThrowsIllegalArgumentException() {
        val negativePrice = -5.0
        
        assertThrows<IllegalArgumentException> {
            runBlocking {
                updateProductPriceUseCase(existingProduct.id, negativePrice)
            }
        }
    }

    @ParameterizedTest(name = "Test price value: {0}")
    @MethodSource("validPriceArguments")
    fun updateProductPrice_ValidPrices_PriceUpdated(price: Double) {
        val updatedProduct: Product?
        
        runBlocking {
            updatedProduct = updateProductPriceUseCase(existingProduct.id, price)
        }
        
        assertNotNull(updatedProduct)
        assertEquals(price, updatedProduct?.price)
    }

    @Test
    fun updateProductPrice_ZeroPrice_PriceUpdated() {
        val zeroPrice = 0.0
        val updatedProduct: Product?
        
        runBlocking {
            updatedProduct = updateProductPriceUseCase(existingProduct.id, zeroPrice)
        }
        
        assertNotNull(updatedProduct)
        assertEquals(zeroPrice, updatedProduct?.price)
    }

    @Test
    fun updateProductPrice_LargePrice_PriceUpdated() {
        val largePrice = 999999.99
        val updatedProduct: Product?
        
        runBlocking {
            updatedProduct = updateProductPriceUseCase(existingProduct.id, largePrice)
        }
        
        assertNotNull(updatedProduct)
        assertEquals(largePrice, updatedProduct?.price)
    }

    @Test
    fun updateProductPrice_PriceWithManyDecimals_PriceUpdated() {
        val precisePrice = 12.3456789
        val updatedProduct: Product?
        
        runBlocking {
            updatedProduct = updateProductPriceUseCase(existingProduct.id, precisePrice)
        }
        
        assertNotNull(updatedProduct)
        assertEquals(precisePrice, updatedProduct?.price)
    }

    private companion object {
        @JvmStatic
        fun validPriceArguments(): Stream<Arguments> = Stream.of(
            Arguments.of(0.0),
            Arguments.of(0.01),
            Arguments.of(1.0),
            Arguments.of(10.50),
            Arguments.of(99.99),
            Arguments.of(100.0),
            Arguments.of(1000.00)
        )
    }
}
