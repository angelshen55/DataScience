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
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class IsPricePositiveUseCaseTest {

    private lateinit var isPricePositiveUseCase: IsPricePositiveUseCase

    @BeforeEach
    fun setUp() {
        isPricePositiveUseCase = IsPricePositiveUseCase()
    }

    @Test
    fun isPricePositive_ZeroPrice_ReturnsTrue() {
        val product = Product(
            id = 1,
            name = "Test Product",
            inStock = true,
            qtyNeeded = 0,
            price = 0.0
        )

        val result = runBlocking {
            isPricePositiveUseCase(product)
        }

        assertTrue(result)
    }

    @Test
    fun isPricePositive_PositivePrice_ReturnsTrue() {
        val product = Product(
            id = 1,
            name = "Test Product",
            inStock = true,
            qtyNeeded = 0,
            price = 10.50
        )

        val result = runBlocking {
            isPricePositiveUseCase(product)
        }

        assertTrue(result)
    }

    @Test
    fun isPricePositive_LargePositivePrice_ReturnsTrue() {
        val product = Product(
            id = 1,
            name = "Test Product",
            inStock = true,
            qtyNeeded = 0,
            price = 999999.99
        )

        val result = runBlocking {
            isPricePositiveUseCase(product)
        }

        assertTrue(result)
    }

    @Test
    fun isPricePositive_SmallPositivePrice_ReturnsTrue() {
        val product = Product(
            id = 1,
            name = "Test Product",
            inStock = true,
            qtyNeeded = 0,
            price = 0.01
        )

        val result = runBlocking {
            isPricePositiveUseCase(product)
        }

        assertTrue(result)
    }


    @Test
    fun isPricePositive_NegativePrice_ReturnsFalse() {
        val product = Product(
            id = 1,
            name = "Test Product",
            inStock = true,
            qtyNeeded = 0,
            price = -5.0
        )

        val result = runBlocking {
            isPricePositiveUseCase(product)
        }

        assertFalse(result)
    }

    @Test
    fun isPricePositive_LargeNegativePrice_ReturnsFalse() {
        val product = Product(
            id = 1,
            name = "Test Product",
            inStock = true,
            qtyNeeded = 0,
            price = -100.50
        )

        val result = runBlocking {
            isPricePositiveUseCase(product)
        }

        assertFalse(result)
    }

    @ParameterizedTest(name = "Test price value: {0}, expected: {1}")
    @MethodSource("priceTestArguments")
    fun isPricePositive_VariousPrices_ReturnsExpected(price: Double, expected: Boolean) {
        val product = Product(
            id = 1,
            name = "Test Product",
            inStock = true,
            qtyNeeded = 0,
            price = price
        )

        val result = runBlocking {
            isPricePositiveUseCase(product)
        }

        assertEquals(expected, result)
    }

    @Test
    fun isPricePositive_PriceWithManyDecimals_ReturnsTrue() {
        val product = Product(
            id = 1,
            name = "Test Product",
            inStock = true,
            qtyNeeded = 0,
            price = 12.3456789
        )

        val result = runBlocking {
            isPricePositiveUseCase(product)
        }

        assertTrue(result)
    }

    private companion object {
        @JvmStatic
        fun priceTestArguments(): Stream<Arguments> = Stream.of(
            Arguments.of(0.0, true),
            Arguments.of(0.01, true),
            Arguments.of(1.0, true),
            Arguments.of(10.50, true),
            Arguments.of(99.99, true),
            Arguments.of(100.0, true),
            Arguments.of(-0.01, false),
            Arguments.of(-1.0, false),
            Arguments.of(-10.50, false),
            Arguments.of(-99.99, false)
        )
    }
}
