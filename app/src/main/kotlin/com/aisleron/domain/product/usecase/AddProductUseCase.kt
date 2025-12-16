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
import com.aisleron.domain.aisle.AisleRepository
import com.aisleron.domain.aisle.usecase.AddAisleUseCase
import com.aisleron.domain.aisle.usecase.GetDefaultAislesUseCase
import com.aisleron.domain.aisleproduct.AisleProduct
import com.aisleron.domain.aisleproduct.usecase.AddAisleProductsUseCase
import com.aisleron.domain.aisleproduct.usecase.GetAisleMaxRankUseCase
import com.aisleron.domain.base.AisleronException
import com.aisleron.domain.location.LocationType
import com.aisleron.domain.location.usecase.GetHomeLocationUseCase
import com.aisleron.domain.location.usecase.GetLocationUseCase
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
    private val getAisleMaxRankUseCase: GetAisleMaxRankUseCase,
    private val getLocationUseCase: GetLocationUseCase,
    private val getHomeLocationUseCase: GetHomeLocationUseCase,
    private val addAisleUseCase: AddAisleUseCase,
    private val aisleRepository: AisleRepository

) : AddProductUseCase {
    override suspend operator fun invoke(product: Product, targetAisle: Aisle?): Int {

        productRepository.getDeletedByName(product.name)?.let { deleted ->
            val revived = product.copy(id = deleted.id)
            productRepository.restore(revived)
            productRepository.update(revived)

            // Determine shop name from aisle -> locationId -> location.name
            val revivedAisle = targetAisle ?: getDefaultAislesUseCase().firstOrNull()
            val revivedShopName = revivedAisle?.let { aisle ->
                runCatching { getLocationUseCase(aisle.locationId)?.name }.getOrNull()
            }

            recordRepository.add(
                Record(
                    productId = revived.id,
                    date = Date(),
                    stock = revived.inStock,
                    price = revived.price,
                    quantity = if (revived.qtyNeeded > 0) revived.qtyNeeded.toDouble() else 1.0,
                    shop = revivedShopName ?: "None"
                )
            )

            val aislesToAdd = targetAisle?.let { listOf(it) } ?: getDefaultAislesUseCase().toMutableList()
            addAisleProductsUseCase(
                aislesToAdd.map {
                    AisleProduct(
                        aisleId = it.id,
                        product = revived,
                        rank = getAisleMaxRankUseCase(it) + 1,
                        id = 0
                    )
                }
            )

            // Mirror into HOME same-named aisle ONLY when adding to a specific SHOP aisle
            if (targetAisle != null) {
                val loc = getLocationUseCase(targetAisle.locationId)
                if (loc?.type == LocationType.SHOP) {
                    val home = getHomeLocationUseCase()
                    val homeAisles = aisleRepository.getForLocation(home.id)
                    val targetHomeAisle = homeAisles.firstOrNull { it.name.equals(targetAisle.name, ignoreCase = true) } ?: run {
                        val newId = addAisleUseCase(
                            Aisle(
                                id = 0,
                                name = targetAisle.name,
                                locationId = home.id,
                                rank = 1000,
                                isDefault = false,
                                products = emptyList(),
                                expanded = true
                            )
                        )
                        aisleRepository.get(newId)!!
                    }

                    addAisleProductsUseCase(
                        listOf(
                            AisleProduct(
                                aisleId = targetHomeAisle.id,
                                product = revived,
                                rank = getAisleMaxRankUseCase(targetHomeAisle) + 1,
                                id = 0
                            )
                        )
                    )
                }
            }
            return revived.id
        }

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

        // Determine shop name from aisle -> locationId -> location.name
        val selectedAisle = targetAisle ?: defaultAisles.firstOrNull()
        val shopName = selectedAisle?.let { aisle ->
            runCatching { getLocationUseCase(aisle.locationId)?.name }.getOrNull()
        }

        recordRepository.add(
            Record(
                productId = newProduct.id,
                date = Date(),
                stock = newProduct.inStock,
                price = newProduct.price,
                quantity = if (newProduct.qtyNeeded > 0) newProduct.qtyNeeded.toDouble() else 1.0,
                shop = shopName ?: "None"
            )
        )

        val aislesToAdd = targetAisle?.let { listOf(it) } ?: defaultAisles

        addAisleProductsUseCase(
            aislesToAdd.map {
                AisleProduct(
                    aisleId = it.id,
                    product = newProduct,
                    rank = getAisleMaxRankUseCase(it) + 1,
                    id = 0
                )
            }
        )

        // Mirror into HOME same-named aisle ONLY when adding to a specific SHOP aisle
        if (targetAisle != null) {
            val loc = getLocationUseCase(targetAisle.locationId)
            if (loc?.type == LocationType.SHOP) {
                val home = getHomeLocationUseCase()
                val homeAisles = aisleRepository.getForLocation(home.id)
                val targetHomeAisle = homeAisles.firstOrNull { it.name.equals(targetAisle.name, ignoreCase = true) } ?: run {
                    val newId = addAisleUseCase(
                        Aisle(
                            id = 0,
                            name = targetAisle.name,
                            locationId = home.id,
                            rank = 1000,
                            isDefault = false,
                            products = emptyList(),
                            expanded = true
                        )
                    )
                    aisleRepository.get(newId)!!
                }

                addAisleProductsUseCase(
                    listOf(
                        AisleProduct(
                            aisleId = targetHomeAisle.id,
                            product = newProduct,
                            rank = getAisleMaxRankUseCase(targetHomeAisle) + 1,
                            id = 0
                        )
                    )
                )
            }
        }

        return newProduct.id
    }
}
