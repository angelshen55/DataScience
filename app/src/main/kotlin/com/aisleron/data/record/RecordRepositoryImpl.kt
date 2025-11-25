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

package com.aisleron.data.record

import com.aisleron.domain.record.Record
import com.aisleron.domain.record.RecordRepository
import java.util.Date

class RecordRepositoryImpl(
    private val recordDao: RecordDao,
    private val recordMapper: RecordMapper
) : RecordRepository {
    override suspend fun get(id: Int): Record? {
        return recordDao.getRecord(id)?.let { recordMapper.toModel(it) }
    }

    override suspend fun getAll(): List<Record> {
        return recordMapper.toModelList(recordDao.getRecords())
    }

    override suspend fun add(item: Record): Int {
        return recordDao.upsert(recordMapper.fromModel(item))[0].toInt()
    }

    override suspend fun add(items: List<Record>): List<Int> {
        return recordDao.upsert(*recordMapper.fromModelList(items).toTypedArray()).map { it.toInt() }
    }

    override suspend fun update(item: Record) { /* no-op or throw UnsupportedOperationException() */ }

    override suspend fun update(items: List<Record>) { /* no-op or throw */ }

    override suspend fun remove(item: Record) { /* no-op or throw */ }

    override suspend fun getRecordsByProduct(productId: Int): List<Record> {
        return recordMapper.toModelList(recordDao.getRecordsByProduct(productId))
    }

    override suspend fun getRecordsByDateRange(startDate: Long, endDate: Long): List<Record> {
        return recordMapper.toModelList(recordDao.getRecordsByDateRange(startDate, endDate))
    }
    
    // New methods for recommendation feature
    override suspend fun getProductPurchaseCounts(): List<com.aisleron.domain.record.ProductPurchaseCount> {
        return recordDao.getProductPurchaseCounts().map { 
            com.aisleron.domain.record.ProductPurchaseCount(
                productId = it.productId,
                productName = it.productName,
                purchaseCount = it.purchaseCount
            )
        }
    }
    
    override suspend fun getPurchaseDatesForProduct(productId: Int): List<Date> {
        return recordDao.getPurchaseDatesForProduct(productId)
    }
}