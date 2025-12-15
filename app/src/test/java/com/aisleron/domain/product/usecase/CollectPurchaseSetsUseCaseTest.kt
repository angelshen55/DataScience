package com.aisleron.domain.product.usecase

import com.aisleron.data.TestDataManager
import com.aisleron.domain.product.Product
import com.aisleron.domain.product.ProductRepository
import com.aisleron.domain.product.PurchaseSet
import com.aisleron.domain.product.PurchaseSetRepository
import com.aisleron.domain.record.Record
import com.aisleron.domain.record.RecordRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Date

class CollectPurchaseSetsUseCaseTest {
    private lateinit var testData: TestDataManager
    private lateinit var productRepository: ProductRepository
    private lateinit var recordRepository: RecordRepository
    private lateinit var purchaseSetRepository: PurchaseSetRepository
    private lateinit var collectPurchaseSetsUseCase: CollectPurchaseSetsUseCase

    private class FakePurchaseSetRepository : PurchaseSetRepository {
        private val sets = mutableListOf<PurchaseSet>()
        private val hashes = mutableSetOf<String>()
        private var lastCollectionTime: Long? = null

        override suspend fun add(purchaseSet: PurchaseSet): Int {
            val hash = purchaseSet.getUniqueHash()
            if (hashes.contains(hash)) return -1
            val newId = (sets.maxOfOrNull { it.id } ?: 0) + 1
            val newSet = purchaseSet.copy(id = newId)
            sets.add(newSet)
            hashes.add(hash)
            return newId
        }

        override suspend fun getPendingUploadSets(): List<PurchaseSet> {
            return sets.filter { !it.uploadedToModel }
        }

        override suspend fun markAsUploaded(purchaseSetId: Int) {
            val idx = sets.indexOfFirst { it.id == purchaseSetId }
            if (idx >= 0) {
                sets[idx] = sets[idx].copy(uploadedToModel = true)
            }
        }

        override suspend fun existsByHash(hash: String): Boolean {
            return hashes.contains(hash)
        }

        override suspend fun getLastCollectionTime(): Long? {
            return lastCollectionTime
        }

        override suspend fun updateLastCollectionTime(timestamp: Long) {
            lastCollectionTime = timestamp
        }

        override suspend fun getAll(): List<PurchaseSet> {
            return sets.toList()
        }
    }

    @BeforeEach
    fun setUp() {
        testData = TestDataManager(addData = false)
        productRepository = testData.getRepository()
        recordRepository = testData.getRepository()
        purchaseSetRepository = FakePurchaseSetRepository()
        collectPurchaseSetsUseCase = CollectPurchaseSetsUseCaseImpl(recordRepository, purchaseSetRepository)
    }

    @Test
    fun collectPurchaseSets_TimeWindows_ReturnsTwoSetsAndUpdatesTimestamp() {
        val p1Id: Int
        val p2Id: Int
        runBlocking {
            p1Id = productRepository.add(Product(id = 0, name = "P1", inStock = true, qtyNeeded = 0, price = 1.0))
            p2Id = productRepository.add(Product(id = 0, name = "P2", inStock = true, qtyNeeded = 0, price = 2.0))

            val now = System.currentTimeMillis()
            recordRepository.add(Record(productId = p1Id, date = Date(now - 5 * 60 * 1000), stock = true, price = 1.0, quantity = 1.0, shop = "S1"))
            recordRepository.add(Record(productId = p2Id, date = Date(now - 60 * 60 * 1000), stock = true, price = 2.0, quantity = 1.0, shop = "S1"))
            recordRepository.add(Record(productId = p1Id, date = Date(now - 3 * 60 * 60 * 1000), stock = true, price = 1.0, quantity = 1.0, shop = "S1"))
        }

        val count = runBlocking { collectPurchaseSetsUseCase.invoke(7) }
        assertEquals(2, count)
        val allSets = runBlocking { purchaseSetRepository.getAll() }
        assertEquals(2, allSets.size)
        val ts = runBlocking { purchaseSetRepository.getLastCollectionTime() }
        assertEquals(true, ts != null && ts > 0L)
    }
}

