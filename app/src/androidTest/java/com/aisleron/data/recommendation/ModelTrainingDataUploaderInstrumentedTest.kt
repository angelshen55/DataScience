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

package com.aisleron.recommendation

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.aisleron.data.api.GenerateRequest
import com.aisleron.data.api.GenerateResponse
import com.aisleron.data.api.HealthResponse
import com.aisleron.data.api.ModelApiService
import com.aisleron.data.api.RetrainResponse
import com.aisleron.domain.product.ModelTrainingDataUploaderImpl
import com.aisleron.domain.product.Product
import com.aisleron.domain.product.ProductRepository
import com.aisleron.domain.product.PurchaseSet
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.Test
import retrofit2.Response
import java.io.File
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelTrainingDataUploaderInstrumentedTest {

    private class FakeProductRepository(
        private val products: Map<Int, Product>
    ) : ProductRepository {
        override suspend fun get(id: Int): Product? = products[id]
        override suspend fun getAll(): List<Product> = products.values.toList()
        override suspend fun add(item: Product): Int = item.id
        override suspend fun add(items: List<Product>): List<Int> = items.map { it.id }
        override suspend fun update(item: Product) {}
        override suspend fun update(items: List<Product>) {}
        override suspend fun remove(item: Product) {}
        override suspend fun restore(item: Product) {}
        override suspend fun getByName(name: String): Product? = products.values.firstOrNull { it.name == name }
        override suspend fun getAllIncludingDeleted(): List<Product> = products.values.toList()
        override suspend fun getDeletedByName(name: String): Product? = null
    }

    private class FakeModelApiService : ModelApiService {
        override suspend fun generateRecommendations(request: GenerateRequest): Response<GenerateResponse> {
            return Response.success(GenerateResponse(prediction = "[]"))
        }

        override suspend fun retrainModel(
            retrain: RequestBody,
            file: MultipartBody.Part
        ): Response<RetrainResponse> {
            return Response.success(RetrainResponse(message = "retrained"))
        }

        override suspend fun checkHealth(): Response<HealthResponse> {
            return Response.success(HealthResponse(status = "ok", device = "cpu", model = "mock", adapter = "none"))
        }
    }

    @Test
    fun uploadPurchaseSets_Success_TemporaryJsonDeleted() = kotlinx.coroutines.test.runTest {
        val context: Context = getInstrumentation().targetContext

        val productRepo = FakeProductRepository(
            mapOf(
                1 to Product(id = 1, name = "Milk", inStock = true, qtyNeeded = 0, price = 0.0),
                2 to Product(id = 2, name = "Bread", inStock = true, qtyNeeded = 0, price = 0.0),
                3 to Product(id = 3, name = "Eggs", inStock = true, qtyNeeded = 0, price = 0.0)
            )
        )
        val apiService = FakeModelApiService()

        val uploader = ModelTrainingDataUploaderImpl(
            productRepository = productRepo,
            apiService = apiService,
            context = context
        )

        val now = Date()
        val validSet = PurchaseSet(
            productIds = setOf(1, 2),
            startTime = now,
            endTime = Date(now.time + 60_000)
        )
        val invalidSet = PurchaseSet(
            productIds = setOf(3),
            startTime = now,
            endTime = Date(now.time + 60_000)
        )

        val success = uploader.uploadPurchaseSets(listOf(validSet, invalidSet))
        assertTrue(success)

        val tmpDir = File(context.cacheDir, "training_data")
        val leftoverFiles = tmpDir.listFiles { _, name -> name.startsWith("training_data_") && name.endsWith(".json") }
        assertEquals(0, leftoverFiles?.size ?: 0)
    }
}
