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

package com.aisleron.data

import com.aisleron.data.product.ProductEntity
import com.aisleron.data.record.RecordEntity
import com.aisleron.domain.record.Record
import com.aisleron.domain.record.RecordRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Date

/**
 * test RecordDaoTestImpl and ProductDaoTestImpl
 */
class DataLayerTest {

    private lateinit var testData: TestDataManager

    @BeforeEach
    fun setUp() {
        testData = TestDataManager(addData = false)
    }

    @Test
    fun testProductDao_UpsertWithPriceAndIsDeleted() = runBlocking {
        val productDao = testData.productDao()
        
        // 测试创建带 price 和 isDeleted 的产品
        val product = ProductEntity(
            id = 0,
            name = "Test Product",
            inStock = true,
            qtyNeeded = 5,
            price = 10.99,
            isDeleted = false
        )
        
        val id = productDao.upsert(product)[0].toInt()
        val retrievedProduct = productDao.getProduct(id)
        
        assertNotNull(retrievedProduct)
        assertEquals("Test Product", retrievedProduct?.name)
        assertEquals(10.99, retrievedProduct?.price)
        assertEquals(false, retrievedProduct?.isDeleted)
    }

    @Test
    fun testProductDao_SoftDelete() = runBlocking {
        val productDao = testData.productDao()
        
        // 创建产品
        val product = ProductEntity(
            id = 0,
            name = "To Delete",
            inStock = true,
            qtyNeeded = 0,
            price = 5.0,
            isDeleted = false
        )
        
        val id = productDao.upsert(product)[0].toInt()
        
        // 软删除
        productDao.softDelete(id)
        
        // 验证软删除
        val activeProducts = productDao.getActiveProducts()
        val allProducts = productDao.getAllProductsIncludingDeleted()
        val deletedProduct = allProducts.find { it.id == id }
        
        assertFalse(activeProducts.any { it.id == id })
        assertNotNull(deletedProduct)
        assertTrue(deletedProduct?.isDeleted == true)
    }

    @Test
    fun testRecordDao_BasicOperations() = runBlocking {
        val recordDao = testData.recordDao()
        val productDao = testData.productDao()
        
        // 先创建一个产品
        val product = ProductEntity(
            id = 0,
            name = "Test Product",
            inStock = true,
            qtyNeeded = 0,
            price = 10.0,
            isDeleted = false
        )
        val productId = productDao.upsert(product)[0].toInt()
        
        // 创建记录
        val record = RecordEntity(
            id = 0,
            productId = productId,
            date = Date(),
            stock = true,
            price = 10.0,
            quantity = 2.0,
            shop = "Test Shop"
        )
        
        val recordId = recordDao.upsert(record)[0].toInt()
        val retrievedRecord = recordDao.getRecord(recordId)
        
        assertNotNull(retrievedRecord)
        assertEquals(productId, retrievedRecord?.productId)
        assertEquals(10.0, retrievedRecord?.price)
        assertEquals(2.0, retrievedRecord?.quantity)
        assertEquals("Test Shop", retrievedRecord?.shop)
    }

    @Test
    fun testRecordDao_GetRecordsByProduct() = runBlocking {
        val recordDao = testData.recordDao()
        val productDao = testData.productDao()
        
        // 创建产品
        val product = ProductEntity(
            id = 0,
            name = "Product A",
            inStock = true,
            qtyNeeded = 0,
            price = 10.0,
            isDeleted = false
        )
        val productId = productDao.upsert(product)[0].toInt()
        
        // 创建多条记录
        val record1 = RecordEntity(0, productId, Date(), true, 10.0, 1.0, "Shop 1")
        val record2 = RecordEntity(0, productId, Date(), true, 10.0, 2.0, "Shop 2")
        
        recordDao.upsert(record1, record2)
        
        val records = recordDao.getRecordsByProduct(productId)
        
        assertEquals(2, records.size)
        assertTrue(records.all { it.productId == productId })
    }

    @Test
    fun testRecordDao_GetHistoryUi() = runBlocking {
        val recordDao = testData.recordDao()
        val productDao = testData.productDao()
        
        // 创建产品
        val product = ProductEntity(
            id = 0,
            name = "Product B",
            inStock = true,
            qtyNeeded = 0,
            price = 15.0,
            isDeleted = false
        )
        val productId = productDao.upsert(product)[0].toInt()
        
        // 创建记录
        val record = RecordEntity(
            id = 0,
            productId = productId,
            date = Date(),
            stock = true,
            price = 15.0,
            quantity = 3.0,
            shop = "Shop X"
        )
        recordDao.upsert(record)
        
        val history = recordDao.getHistoryUi()
        
        assertFalse(history.isEmpty())
        val historyItem = history.first()
        assertEquals("Product B", historyItem.productName)
        assertEquals(15.0, historyItem.unitPrice)
        assertEquals(3.0, historyItem.quantity)
        assertEquals(45.0, historyItem.totalCost) // 15.0 * 3
        assertEquals("Shop X", historyItem.shop)
    }

    @Test
    fun testRecordRepository_Integration() = runBlocking {
        val recordRepository = testData.getRepository<RecordRepository>()
        val productDao = testData.productDao()
        
        // 创建产品
        val product = ProductEntity(
            id = 0,
            name = "Product C",
            inStock = true,
            qtyNeeded = 0,
            price = 20.0,
            isDeleted = false
        )
        val productId = productDao.upsert(product)[0].toInt()
        
        // 使用 Repository 添加记录
        val record = Record(
            productId = productId,
            date = Date(),
            stock = true,
            price = 20.0,
            quantity = 1.0,
            shop = "Test Shop"
        )
        
        val recordId = recordRepository.add(record)
        val retrievedRecord = recordRepository.get(recordId)
        
        assertNotNull(retrievedRecord)
        assertEquals(productId, retrievedRecord?.productId)
        assertEquals(20.0, retrievedRecord?.price)
    }
}
