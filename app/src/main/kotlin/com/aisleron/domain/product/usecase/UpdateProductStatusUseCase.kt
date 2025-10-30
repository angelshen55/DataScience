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
import com.aisleron.domain.aisle.usecase.GetAisleUseCase
import com.aisleron.domain.aisleproduct.AisleProduct
import com.aisleron.domain.aisleproduct.AisleProductRepository
import com.aisleron.domain.aisleproduct.usecase.AddAisleProductsUseCase
import com.aisleron.domain.aisleproduct.usecase.GetAisleMaxRankUseCase
import com.aisleron.domain.location.LocationType
import com.aisleron.domain.location.usecase.GetHomeLocationUseCase
import com.aisleron.domain.location.usecase.GetLocationUseCase
import com.aisleron.domain.product.Product

interface UpdateProductStatusUseCase {
    suspend operator fun invoke(id: Int, inStock: Boolean): Product?
}

class UpdateProductStatusUseCaseImpl(
    private val getProductUseCase: GetProductUseCase,
    private val updateProductUseCase: UpdateProductUseCase,
    private val getHomeLocationUseCase: GetHomeLocationUseCase,
    private val getAisleUseCase: GetAisleUseCase,
    private val getLocationUseCase: GetLocationUseCase,
    private val aisleRepository: AisleRepository,
    private val addAisleUseCase: AddAisleUseCase,
    private val aisleProductRepository: AisleProductRepository,
    private val addAisleProductsUseCase: AddAisleProductsUseCase,
    private val getAisleMaxRankUseCase: GetAisleMaxRankUseCase
) : UpdateProductStatusUseCase {

    override suspend operator fun invoke(id: Int, inStock: Boolean): Product? {
        val updated = getProductUseCase(id)?.copy(inStock = inStock) ?: return null
        updateProductUseCase(updated)

        if (inStock) {
            // 当商品变为 in-stock，把它镜像到 HOME 中与其对应 Shop 过道同名的过道（若无则创建）
            val home = getHomeLocationUseCase()
            val productAisles = aisleProductRepository.getProductAisles(updated.id)

            // 收集该商品当前关联的“SHOP 过道名”
            val shopAisleNames = productAisles.mapNotNull { ap ->
                val aisle = getAisleUseCase(ap.aisleId) ?: return@mapNotNull null
                val loc = getLocationUseCase(aisle.locationId) ?: return@mapNotNull null
                if (loc.type == LocationType.SHOP) aisle.name else null
            }.distinct()

            if (shopAisleNames.isNotEmpty()) {
                val homeAisles = aisleRepository.getForLocation(home.id)
                for (name in shopAisleNames) {
                    val homeAisle = homeAisles.firstOrNull { it.name.equals(name, ignoreCase = true) }
                        ?: run {
                            val newId = addAisleUseCase(
                                Aisle(
                                    id = 0,
                                    name = name,
                                    locationId = home.id,
                                    rank = 1000,
                                    isDefault = false, // 与“ No Aisle ”区分
                                    products = emptyList(),
                                    expanded = true
                                )
                            )
                            aisleRepository.get(newId)!!
                        }

                    val alreadyLinked = productAisles.any { it.aisleId == homeAisle.id }
                    if (!alreadyLinked) {
                        addAisleProductsUseCase(
                            listOf(
                                AisleProduct(
                                    aisleId = homeAisle.id,
                                    product = updated,
                                    rank = getAisleMaxRankUseCase(homeAisle) + 1,
                                    id = 0
                                )
                            )
                        )
                    }
                }
            }
        }

        return updated
    }
}