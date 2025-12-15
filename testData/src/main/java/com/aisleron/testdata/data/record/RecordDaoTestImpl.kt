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

package com.aisleron.testdata.data.record

import com.aisleron.data.record.RecordDao
import com.aisleron.data.record.RecordEntity
import com.aisleron.data.record.RecordWithProductUi
import com.aisleron.data.record.ProductPurchaseCount
import com.aisleron.testdata.data.product.ProductDaoTestImpl
import java.util.Date

class RecordDaoTestImpl(private val productDao: ProductDaoTestImpl) : RecordDao {

    private val recordList = mutableListOf<RecordEntity>()

    override suspend fun upsert(vararg entity: RecordEntity): List<Long> {
        val result = mutableListOf<Long>()
        entity.forEach {
            val id: Int
            val existingEntity = getRecord(it.id)
            if (existingEntity == null) {
                id = (recordList.maxOfOrNull { e -> e.id }?.toInt() ?: 0) + 1
            } else {
                id = existingEntity.id
                recordList.removeAt(recordList.indexOf(existingEntity))
            }

            val newEntity = RecordEntity(
                id = id,
                productId = it.productId,
                date = it.date,
                stock = it.stock,
                price = it.price,
                quantity = it.quantity,
                shop = it.shop
            )

            recordList.add(newEntity)
            result.add(newEntity.id.toLong())
        }
        return result
    }

    override suspend fun delete(vararg entity: RecordEntity) {
        recordList.removeIf { it in entity }
    }

    override suspend fun getRecord(recordId: Int): RecordEntity? {
        return recordList.find { it.id == recordId }
    }

    override suspend fun getRecords(): List<RecordEntity> {
        return recordList
    }

    override suspend fun getRecordsByProduct(productId: Int): List<RecordEntity> {
        return recordList.filter { it.productId == productId }
    }

    override suspend fun getRecordsByDateRange(startDate: Long, endDate: Long): List<RecordEntity> {
        return recordList.filter {
            it.date.time >= startDate && it.date.time <= endDate
        }
    }

    override suspend fun getHistoryUi(): List<RecordWithProductUi> {
        return recordList.map { record ->
            val product = productDao.getProduct(record.productId)
            RecordWithProductUi(
                recordId = record.id,
                productId = record.productId,
                productName = product?.name ?: "Unknown Product",
                unitPrice = record.price,
                quantity = record.quantity,
                shop = record.shop,
                totalCost = record.price * record.quantity,
                date = record.date
            )
        }.sortedByDescending { it.date }
    }

    override suspend fun getHistoryUiFiltered(
        name: String?,
        shop: String?,
        startMillis: Long?,
        endMillis: Long?
    ): List<RecordWithProductUi> {
        return getHistoryUi().filter { record ->
            val matchesName = name.isNullOrBlank() || record.productName.contains(name, ignoreCase = true)
            val matchesShop = shop.isNullOrBlank() || record.shop == shop
            val matchesStart = startMillis == null || record.date.time >= startMillis
            val matchesEnd = endMillis == null || record.date.time <= endMillis
            matchesName && matchesShop && matchesStart && matchesEnd
        }
    }

    override suspend fun getPurchaseDatesForProduct(productId: Int): List<Date> {
        return recordList
            .filter { it.productId == productId }
            .map { it.date }
            .sortedBy { it.time }
    }

    override suspend fun getProductPurchaseCounts(): List<ProductPurchaseCount> {
        return recordList
            .groupBy { it.productId }
            .map { (pid, records) ->
                val name = productDao.getProduct(pid)?.name ?: "Unknown Product"
                ProductPurchaseCount(
                    productId = pid,
                    productName = name,
                    purchaseCount = records.size
                )
            }
            .sortedByDescending { it.purchaseCount }
    }
}
