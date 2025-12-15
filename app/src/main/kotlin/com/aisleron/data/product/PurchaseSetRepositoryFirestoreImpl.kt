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

package com.aisleron.data.product

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.aisleron.domain.product.PurchaseSet
import com.aisleron.domain.product.PurchaseSetRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Implementation of PurchaseSetRepository using Firestore for cloud storage
 * This provides automatic sync across devices and offline support
 */
class PurchaseSetRepositoryFirestoreImpl(
    private val context: Context
) : PurchaseSetRepository {
    
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val collectionName = "purchase_sets"
    
    // Use SharedPreferences for last collection time (lightweight, doesn't need cloud sync)
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    companion object {
        private const val TAG = "PurchaseSetRepoFirestore"
        private const val PREFS_NAME = "purchase_sets_prefs"
        private const val KEY_LAST_COLLECTION_TIME = "last_collection_time"
    }
    
    override suspend fun add(purchaseSet: PurchaseSet): Int = withContext(Dispatchers.IO) {
        try {
            val hash = purchaseSet.getUniqueHash()
            
            // Check if already exists
            if (existsByHash(hash)) {
                Log.d(TAG, "Purchase set already exists, skipping: $hash")
                return@withContext -1
            }
            
            // Generate ID (use timestamp-based ID for Firestore)
            val newId = System.currentTimeMillis().toInt() // Simple ID generation
            val newPurchaseSet = purchaseSet.copy(id = newId)
            
            // Convert to Firestore document
            val documentData = purchaseSetToMap(newPurchaseSet).toMutableMap()
            documentData["hash"] = hash // Store hash for duplicate checking
            
            // Add to Firestore
            firestore.collection(collectionName)
                .document(newId.toString())
                .set(documentData, SetOptions.merge())
                .await()
            
            Log.i(TAG, "Added purchase set to Firestore: ID=$newId, Products=${purchaseSet.productIds.size}, Hash=$hash")
            newId
        } catch (e: Exception) {
            Log.e(TAG, "Error adding purchase set to Firestore", e)
            -1
        }
    }
    
    override suspend fun getPendingUploadSets(): List<PurchaseSet> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection(collectionName)
                .whereEqualTo("uploadedToModel", false)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                try {
                    mapToPurchaseSet(doc.data ?: emptyMap(), doc.id.toIntOrNull() ?: 0)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing purchase set document: ${doc.id}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending upload sets from Firestore", e)
            emptyList()
        }
    }
    
    override suspend fun markAsUploaded(purchaseSetId: Int): Unit = withContext(Dispatchers.IO) {
        try {
            firestore.collection(collectionName)
                .document(purchaseSetId.toString())
                .update("uploadedToModel", true)
                .await()
            
            Log.i(TAG, "Marked purchase set $purchaseSetId as uploaded in Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Error marking purchase set as uploaded in Firestore", e)
        }
    }
    
    override suspend fun existsByHash(hash: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection(collectionName)
                .whereEqualTo("hash", hash)
                .limit(1)
                .get()
                .await()
            
            !snapshot.isEmpty
        } catch (e: Exception) {
            Log.e(TAG, "Error checking hash existence in Firestore", e)
            false
        }
    }
    
    override suspend fun getLastCollectionTime(): Long? = withContext(Dispatchers.IO) {
        val timestamp = prefs.getLong(KEY_LAST_COLLECTION_TIME, 0L)
        if (timestamp > 0) timestamp else null
    }
    
    override suspend fun updateLastCollectionTime(timestamp: Long) = withContext(Dispatchers.IO) {
        prefs.edit().putLong(KEY_LAST_COLLECTION_TIME, timestamp).apply()
    }
    
    override suspend fun getAll(): List<PurchaseSet> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection(collectionName)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                try {
                    mapToPurchaseSet(doc.data ?: emptyMap(), doc.id.toIntOrNull() ?: 0)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing purchase set document: ${doc.id}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all purchase sets from Firestore", e)
            emptyList()
        }
    }
    
    /**
     * Convert PurchaseSet to Firestore document map
     */
    private fun purchaseSetToMap(purchaseSet: PurchaseSet): Map<String, Any> {
        return mapOf(
            "id" to purchaseSet.id,
            "productIds" to purchaseSet.productIds.toList(),
            "startTime" to purchaseSet.startTime.time,
            "endTime" to purchaseSet.endTime.time,
            "collectedAt" to purchaseSet.collectedAt.time,
            "uploadedToModel" to purchaseSet.uploadedToModel
        )
    }
    
    /**
     * Convert Firestore document map to PurchaseSet
     */
    private fun mapToPurchaseSet(data: Map<String, Any>, defaultId: Int): PurchaseSet {
        val productIdsList = data["productIds"] as? List<*> ?: emptyList<Int>()
        val productIds = productIdsList.mapNotNull { 
            when (it) {
                is Int -> it
                is Long -> it.toInt()
                is Number -> it.toInt()
                else -> null
            }
        }.toSet()
        
        return PurchaseSet(
            id = (data["id"] as? Number)?.toInt() ?: defaultId,
            productIds = productIds,
            startTime = java.util.Date((data["startTime"] as? Number)?.toLong() ?: 0L),
            endTime = java.util.Date((data["endTime"] as? Number)?.toLong() ?: 0L),
            collectedAt = java.util.Date((data["collectedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()),
            uploadedToModel = (data["uploadedToModel"] as? Boolean) ?: false
        )
    }
}

