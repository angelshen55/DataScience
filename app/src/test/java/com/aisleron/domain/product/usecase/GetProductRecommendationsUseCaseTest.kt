package com.aisleron.domain.product.usecase

import com.aisleron.data.TestDataManager
import com.aisleron.domain.product.Product
import com.aisleron.domain.product.ProductRepository
import com.aisleron.domain.record.Record
import com.aisleron.domain.record.RecordRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Date

class GetProductRecommendationsUseCaseTest {
    private lateinit var testData: TestDataManager
    private lateinit var productRepository: ProductRepository
    private lateinit var recordRepository: RecordRepository
    private lateinit var getProductRecommendationsUseCase: GetProductRecommendationsUseCase

    @BeforeEach
    fun setUp() {
        testData = TestDataManager(addData = false)
        productRepository = testData.getRepository()
        recordRepository = testData.getRepository()
        getProductRecommendationsUseCase = GetProductRecommendationsUseCase(productRepository, recordRepository)
    }

    @Test
    fun getRecommendations_DeletedProductsWithPurchases_ReturnsSortedRecommendations() {
        val prodAId: Int
        val prodBId: Int
        val prodCId: Int
        runBlocking {
            prodAId = productRepository.add(Product(id = 0, name = "Rec-A", inStock = false, qtyNeeded = 0, price = 0.0))
            prodBId = productRepository.add(Product(id = 0, name = "Rec-B", inStock = false, qtyNeeded = 0, price = 0.0))
            prodCId = productRepository.add(Product(id = 0, name = "Active-C", inStock = false, qtyNeeded = 0, price = 0.0))

            productRepository.remove(productRepository.get(prodAId)!!)
            productRepository.remove(productRepository.get(prodBId)!!)

            val now = System.currentTimeMillis()
            recordRepository.add(Record(productId = prodAId, date = Date(now - 90L * 24 * 3600 * 1000), stock = true, price = 10.0, quantity = 1.0, shop = "S1"))
            recordRepository.add(Record(productId = prodAId, date = Date(now - 60L * 24 * 3600 * 1000), stock = true, price = 10.0, quantity = 1.0, shop = "S1"))
            recordRepository.add(Record(productId = prodAId, date = Date(now - 30L * 24 * 3600 * 1000), stock = true, price = 10.0, quantity = 1.0, shop = "S1"))

            recordRepository.add(Record(productId = prodBId, date = Date(now - 300L * 24 * 3600 * 1000), stock = true, price = 20.0, quantity = 1.0, shop = "S2"))
            recordRepository.add(Record(productId = prodBId, date = Date(now - 200L * 24 * 3600 * 1000), stock = true, price = 20.0, quantity = 1.0, shop = "S2"))

            recordRepository.add(Record(productId = prodCId, date = Date(now - 10L * 24 * 3600 * 1000), stock = true, price = 5.0, quantity = 1.0, shop = "S3"))
        }

        val recs = runBlocking { getProductRecommendationsUseCase.invoke(10) }
        assertEquals(2, recs.size)
        val names = recs.map { it.product.name }
        assertTrue(names.contains("Rec-A"))
        assertTrue(names.contains("Rec-B"))
        assertEquals("Rec-A", recs.first().product.name)
    }
}

