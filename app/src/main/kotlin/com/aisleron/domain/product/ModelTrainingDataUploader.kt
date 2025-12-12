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

package com.aisleron.domain.product

import com.aisleron.data.api.ModelApiService
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileWriter
import java.util.UUID
import android.util.Log

/**
 * Interface for uploading purchase sets to the recommendation model for training
 */
interface ModelTrainingDataUploader {
    /**
     * Upload purchase sets to the model for training
     * @param purchaseSets List of purchase sets to upload
     * @return Success status
     */
    suspend fun uploadPurchaseSets(purchaseSets: List<PurchaseSet>): Boolean
}

/**
 * Implementation that uploads purchase sets to the model API
 */
class ModelTrainingDataUploaderImpl(
    private val productRepository: ProductRepository,
    private val apiService: ModelApiService,
    private val context: android.content.Context
) : ModelTrainingDataUploader {
    
    companion object {
        private const val TAG = "ModelTrainingDataUploader"
    }
    
    override suspend fun uploadPurchaseSets(purchaseSets: List<PurchaseSet>): Boolean {
        if (purchaseSets.isEmpty()) {
            Log.i(TAG, "No purchase sets to upload")
            return true
        }
        
        return withContext(Dispatchers.IO) {
            try {
                // Convert purchase sets to training data format
                // Format: { "Product": [["Product1", "Product2"], ["Product3", "Product4"]] }
                val productLists = mutableListOf<List<String>>()
                
                purchaseSets.forEach { set ->
                    // Convert product IDs to product names
                    val productNames = set.productIds.mapNotNull { productId ->
                        productRepository.get(productId)?.name
                    }
                    
                    // Only include sets with at least 2 products
                    if (productNames.size >= 2) {
                        productLists.add(productNames)
                        Log.d(TAG, "Prepared set: ${productNames.joinToString(", ")}")
                    }
                }
                
                if (productLists.isEmpty()) {
                    Log.w(TAG, "No valid purchase sets (need at least 2 products per set)")
                    return@withContext false
                }
                
                // Create JSON file
                val trainingData = mapOf("Product" to productLists)
                val jsonContent = Gson().toJson(trainingData)
                
                // Create temporary file in app's cache directory
                val tempDir = File(context.cacheDir, "training_data")
                if (!tempDir.exists()) {
                    tempDir.mkdirs()
                }
                
                val tempFile = File(tempDir, "training_data_${UUID.randomUUID()}.json")
                
                try {
                    FileWriter(tempFile).use { writer ->
                        writer.write(jsonContent)
                    }
                    
                    Log.i(TAG, "Created training data file: ${tempFile.absolutePath}")
                    Log.d(TAG, "Training data content: $jsonContent")
                    
                    // Prepare multipart request
                    val requestFile = tempFile.asRequestBody("application/json".toMediaType())
                    val filePart = MultipartBody.Part.createFormData("file", tempFile.name, requestFile)
                    val retrainPart = "true".toRequestBody("text/plain".toMediaType())
                    
                    // Upload to API
                    val response = apiService.retrainModel(retrainPart, filePart)
                    
                    if (response.isSuccessful) {
                        Log.i(TAG, "Successfully uploaded ${purchaseSets.size} purchase sets to model")
                        Log.d(TAG, "Response: ${response.body()?.message}")
                        true
                    } else {
                        Log.e(TAG, "Failed to upload training data: ${response.code()} - ${response.message()}")
                        Log.e(TAG, "Error body: ${response.errorBody()?.string()}")
                        false
                    }
                } finally {
                    // Clean up temporary file
                    if (tempFile.exists()) {
                        tempFile.delete()
                        Log.d(TAG, "Deleted temporary file: ${tempFile.absolutePath}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading purchase sets to model", e)
                false
            }
        }
    }
}

