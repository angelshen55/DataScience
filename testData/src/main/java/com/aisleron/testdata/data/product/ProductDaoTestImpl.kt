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

package com.aisleron.testdata.data.product

import com.aisleron.data.aisleproduct.AisleProductDao
import com.aisleron.data.product.ProductDao
import com.aisleron.data.product.ProductEntity

class ProductDaoTestImpl : ProductDao {

    private val productList = mutableListOf<ProductEntity>()

    override suspend fun upsert(vararg entity: ProductEntity): List<Long> {
        val result = mutableListOf<Long>()
        entity.forEach {
            val id: Int
            val existingEntity = getProduct(it.id)
            if (existingEntity == null) {
                id = (productList.maxOfOrNull { e -> e.id }?.toInt() ?: 0) + 1
            } else {
                id = existingEntity.id
                productList.removeAt(productList.indexOf(existingEntity))
            }

            val newEntity = ProductEntity(
                id = id,
                name = it.name,
                inStock = it.inStock,
                qtyNeeded = it.qtyNeeded,
                price = it.price,
                isDeleted = it.isDeleted
            )

            productList.add(newEntity)
            result.add(newEntity.id.toLong())
        }
        return result
    }

    override suspend fun delete(vararg entity: ProductEntity) {
        productList.removeIf { it in entity }
    }

    override suspend fun getProduct(productId: Int): ProductEntity? {
        return productList.find { it.id == productId }
    }

    override suspend fun getActiveProducts(): List<ProductEntity> {
        return productList.filter { !it.isDeleted }
    }

    override suspend fun getAllProductsIncludingDeleted(): List<ProductEntity> {
        return productList
    }

    override suspend fun softDelete(productId: Int) {
        val product = getProduct(productId)
        product?.let {
            val updatedProduct = it.copy(isDeleted = true)
            val index = productList.indexOf(it)
            if (index >= 0) {
                productList[index] = updatedProduct
            }
        }
    }

    override suspend fun remove(product: ProductEntity, aisleProductDao: AisleProductDao) {
        val aisleProducts = aisleProductDao.getAisleProductsByProduct(product.id)
        aisleProductDao.delete(*aisleProducts.map { it.aisleProduct }.toTypedArray())
        softDelete(product.id)
    }

    override suspend fun getProductByName(name: String): ProductEntity? {
        return productList.find { it.name.uppercase() == name.uppercase() }
    }

    override suspend fun getDeletedProductByName(name: String): ProductEntity? {
        return productList.find { it.name.equals(name, ignoreCase = true) && it.isDeleted }
    }

    override suspend fun restore(productId: Int) {
        val product = getProduct(productId) ?: return
        val updatedProduct = product.copy(isDeleted = false)
        val index = productList.indexOf(product)
        if (index >= 0) {
            productList[index] = updatedProduct
        }
    }

    override suspend fun getProducts(): List<ProductEntity> {
        return productList.toList()
    }
}