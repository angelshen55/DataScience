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
 * Repository for managing purchase sets collected for model training
 */
interface PurchaseSetRepository {
    /**
     * Add a purchase set to the repository
     */
    suspend fun add(purchaseSet: PurchaseSet): Int
    
    /**
     * Get all purchase sets that haven't been uploaded to the model yet
     */
    suspend fun getPendingUploadSets(): List<PurchaseSet>
    
    /**
     * Mark a purchase set as uploaded to the model
     */
    suspend fun markAsUploaded(purchaseSetId: Int)
    
    /**
     * Check if a purchase set with the given hash already exists
     * Used to avoid duplicate collection
     */
    suspend fun existsByHash(hash: String): Boolean
    
    /**
     * Get the timestamp of the last collection
     * Used to determine what data to collect next
     */
    suspend fun getLastCollectionTime(): Long?
    
    /**
     * Update the last collection time
     */
    suspend fun updateLastCollectionTime(timestamp: Long)
    
    /**
     * Get all purchase sets (for debugging/logging purposes)
     */
    suspend fun getAll(): List<PurchaseSet>
}

