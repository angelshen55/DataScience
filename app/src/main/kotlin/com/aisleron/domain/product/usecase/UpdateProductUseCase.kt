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

import com.aisleron.domain.base.AisleronException
import com.aisleron.domain.aisle.usecase.GetDefaultAislesUseCase
import com.aisleron.domain.location.usecase.GetLocationUseCase
import com.aisleron.domain.product.Product
import com.aisleron.domain.product.ProductRepository
import com.aisleron.domain.record.RecordRepository
import com.aisleron.domain.record.Record
import java.util.Date

class UpdateProductUseCase(
    private val productRepository: ProductRepository,
    private val recordRepository: RecordRepository,
    private val isProductNameUniqueUseCase: IsProductNameUniqueUseCase,
    private val getDefaultAislesUseCase: GetDefaultAislesUseCase,
    private val getLocationUseCase: GetLocationUseCase
) {
    suspend operator fun invoke(product: Product) {

        val oldProduct = productRepository.get(product.id)

        if (!isProductNameUniqueUseCase(product)) {
            throw AisleronException.DuplicateProductNameException("Product Name must be unique")
        }

        productRepository.update(product)

        if (oldProduct != null && 
            (oldProduct.price != product.price || oldProduct.inStock != product.inStock)) {
            val defaultAisles = getDefaultAislesUseCase()
            val selectedAisle = defaultAisles.firstOrNull()
            val shopName = selectedAisle?.let { aisle ->
                runCatching { getLocationUseCase(aisle.locationId)?.name }.getOrNull()
            }

            recordRepository.add(
                Record(
                    productId = product.id,
                    date = Date(),
                    stock = product.inStock,
                    price = product.price,
                    quantity = product.qtyNeeded,
                    shop = shopName ?: "None"
                )
            )
        }
    }
}
