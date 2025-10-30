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

import com.aisleron.domain.aisle.Aisle
import com.aisleron.domain.aisle.usecase.GetDefaultAislesUseCase
import com.aisleron.domain.aisleproduct.AisleProduct
import com.aisleron.domain.aisleproduct.usecase.AddAisleProductsUseCase
import com.aisleron.domain.aisleproduct.usecase.GetAisleMaxRankUseCase
import com.aisleron.domain.base.AisleronException
import com.aisleron.domain.product.Product
import com.aisleron.domain.product.ProductRepository
import com.aisleron.domain.record.RecordRepository
import com.aisleron.domain.record.Record
import java.util.Date

interface AddProductUseCase {
    suspend operator fun invoke(product: Product, targetAisle: Aisle?): Int
}

class AddProductUseCaseImpl(
    private val productRepository: ProductRepository,
    private val recordRepository: RecordRepository,
    private val getDefaultAislesUseCase: GetDefaultAislesUseCase,
    private val addAisleProductsUseCase: AddAisleProductsUseCase,
    private val isProductNameUniqueUseCase: IsProductNameUniqueUseCase,
    private val isPricePositiveUseCase: IsPricePositiveUseCase,
    private val getAisleMaxRankUseCase: GetAisleMaxRankUseCase

) : AddProductUseCase {
    override suspend operator fun invoke(product: Product, targetAisle: Aisle?): Int {

        if (!isProductNameUniqueUseCase(product)) {
            throw AisleronException.DuplicateProductNameException("Product Name must be unique")
        }

        if (!isPricePositiveUseCase(product)) {
            throw AisleronException.NegativePriceException("Price must be positive")
        }

        if (productRepository.get(product.id) != null) {
            throw AisleronException.DuplicateProductException("Cannot add a duplicate of an existing Product")
        }

        val newProduct = product.copy(id = productRepository.add(product))
        val defaultAisles = getDefaultAislesUseCase().toMutableList()

        recordRepository.add(Record(
            productId = newProduct.id,
            date = Date(),
            stock = newProduct.inStock,
            price = newProduct.price
        ))

        val aislesToAdd = targetAisle?.let { listOf(it) } ?: defaultAisles

        addAisleProductsUseCase(aislesToAdd.map {
            AisleProduct(
                aisleId = it.id,
                product = newProduct,
                rank = getAisleMaxRankUseCase(it) + 1,
                id = 0
            )
        })

        return newProduct.id
    }
}