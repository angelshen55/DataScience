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

/**
 * Interface for uploading purchase sets to the recommendation model for training
 * This is a placeholder interface - implementation will be added later
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
 * Default implementation that logs the data (placeholder for future implementation)
 */
class ModelTrainingDataUploaderImpl(
    private val productRepository: ProductRepository
) : ModelTrainingDataUploader {
    override suspend fun uploadPurchaseSets(purchaseSets: List<PurchaseSet>): Boolean {
        // TODO: Implement actual upload to model API
        // For now, just log the data with product names
        android.util.Log.i("ModelUploader", "Would upload ${purchaseSets.size} purchase sets to model")
        
        purchaseSets.forEachIndexed { index, set ->
            // Convert product IDs to product names
            val productNames = set.productIds.mapNotNull { productId ->
                productRepository.get(productId)?.name
            }
            
            // Format as ['Product1', 'Product2', 'Product3']
            val formattedSet = set.formatProductNames(productNames)
            
            android.util.Log.i("ModelUploader", "Set $index: $formattedSet (Time: ${set.startTime} to ${set.endTime})")
            
            // TODO: When implementing actual API upload, use formattedSet or productNames
            // Example: api.uploadTrainingData(formattedSet) or api.uploadTrainingData(productNames)
        }
        
        return true
    }
}

