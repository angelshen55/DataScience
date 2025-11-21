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

import com.aisleron.domain.product.Product
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class IsPricePositiveUseCaseTest {

    private lateinit var isPricePositiveUseCase: IsPricePositiveUseCase

    @BeforeEach
    fun setUp() {
        isPricePositiveUseCase = IsPricePositiveUseCase()
    }

    @Test
    fun isPricePositive_ZeroPrice_ReturnsTrue() = runTest {
        assertTrue(isPricePositiveUseCase(createProduct(price = 0.0)))
    }

    @Test
    fun isPricePositive_PositivePrice_ReturnsTrue() = runTest {
        assertTrue(isPricePositiveUseCase(createProduct(price = 10.50)))
    }

    @Test
    fun isPricePositive_LargePositivePrice_ReturnsTrue() = runTest {
        assertTrue(isPricePositiveUseCase(createProduct(price = 999_999.99)))
    }

    @Test
    fun isPricePositive_SmallPositivePrice_ReturnsTrue() = runTest {
        assertTrue(isPricePositiveUseCase(createProduct(price = 0.01)))
    }


    @Test
    fun isPricePositive_NegativePrice_ReturnsFalse() = runTest {
        assertFalse(isPricePositiveUseCase(createProduct(price = -5.0)))
    }

    @Test
    fun isPricePositive_LargeNegativePrice_ReturnsFalse() = runTest {
        assertFalse(isPricePositiveUseCase(createProduct(price = -100.50)))
    }

    @ParameterizedTest(name = "Test price value: {0}, expected: {1}")
    @CsvSource(
        "0.0, true",
        "0.01, true",
        "1.0, true",
        "10.5, true",
        "99.99, true",
        "100.0, true",
        "-0.01, false",
        "-1.0, false",
        "-10.5, false",
        "-99.99, false"
    )
    fun isPricePositive_VariousPrices_ReturnsExpected(price: Double, expected: Boolean) = runTest {
        assertEquals(expected, isPricePositiveUseCase(createProduct(price = price)))
    }

    @Test
    fun isPricePositive_PriceWithManyDecimals_ReturnsTrue() = runTest {
        assertTrue(isPricePositiveUseCase(createProduct(price = 12.3456789)))
    }

    private fun createProduct(price: Double) = Product(
        id = 1,
        name = "Test Product",
        inStock = true,
        qtyNeeded = 0,
        price = price
    )
}
