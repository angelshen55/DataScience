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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Implementation of PurchaseSetRepository using SharedPreferences for persistence
 * This is a simple implementation that stores purchase sets as JSON
 * For production, consider using Room database for better performance
 */
class PurchaseSetRepositoryImpl(
    private val context: Context
) : PurchaseSetRepository {
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val purchaseSets: MutableList<PurchaseSet> = mutableListOf()
    private val collectedHashes: MutableSet<String> = mutableSetOf()
    
    init {
        loadFromPreferences()
    }
    
    override suspend fun add(purchaseSet: PurchaseSet): Int = withContext(Dispatchers.IO) {
        val hash = purchaseSet.getUniqueHash()
        
        // Check if already exists
        if (collectedHashes.contains(hash)) {
            Log.d(TAG, "Purchase set already exists, skipping: $hash")
            return@withContext -1
        }
        
        // Generate ID
        val newId = (purchaseSets.maxOfOrNull { it.id } ?: 0) + 1
        val newPurchaseSet = purchaseSet.copy(id = newId)
        
        purchaseSets.add(newPurchaseSet)
        collectedHashes.add(hash)
        
        saveToPreferences()
        
        Log.i(TAG, "Added purchase set: ID=$newId, Products=${purchaseSet.productIds.size}, Hash=$hash")
        newId
    }
    
    override suspend fun getPendingUploadSets(): List<PurchaseSet> = withContext(Dispatchers.IO) {
        purchaseSets.filter { !it.uploadedToModel }
    }
    
    override suspend fun markAsUploaded(purchaseSetId: Int) = withContext(Dispatchers.IO) {
        val index = purchaseSets.indexOfFirst { it.id == purchaseSetId }
        if (index >= 0) {
            purchaseSets[index] = purchaseSets[index].copy(uploadedToModel = true)
            saveToPreferences()
            Log.i(TAG, "Marked purchase set $purchaseSetId as uploaded")
        }
    }
    
    override suspend fun existsByHash(hash: String): Boolean = withContext(Dispatchers.IO) {
        collectedHashes.contains(hash)
    }
    
    override suspend fun getLastCollectionTime(): Long? = withContext(Dispatchers.IO) {
        val timestamp = prefs.getLong(KEY_LAST_COLLECTION_TIME, 0L)
        if (timestamp > 0) timestamp else null
    }
    
    override suspend fun updateLastCollectionTime(timestamp: Long) = withContext(Dispatchers.IO) {
        prefs.edit().putLong(KEY_LAST_COLLECTION_TIME, timestamp).apply()
    }
    
    override suspend fun getAll(): List<PurchaseSet> = withContext(Dispatchers.IO) {
        purchaseSets.toList()
    }
    
    private fun loadFromPreferences() {
        try {
            val jsonArray = JSONArray(prefs.getString(KEY_PURCHASE_SETS, "[]") ?: "[]")
            purchaseSets.clear()
            collectedHashes.clear()
            
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val purchaseSet = parsePurchaseSet(json)
                purchaseSets.add(purchaseSet)
                collectedHashes.add(purchaseSet.getUniqueHash())
            }
            
            Log.d(TAG, "Loaded ${purchaseSets.size} purchase sets from preferences")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading purchase sets from preferences", e)
        }
    }
    
    private fun saveToPreferences() {
        try {
            val jsonArray = JSONArray()
            purchaseSets.forEach { purchaseSet ->
                jsonArray.put(toJson(purchaseSet))
            }
            
            prefs.edit()
                .putString(KEY_PURCHASE_SETS, jsonArray.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving purchase sets to preferences", e)
        }
    }
    
    private fun toJson(purchaseSet: PurchaseSet): JSONObject {
        return JSONObject().apply {
            put("id", purchaseSet.id)
            put("productIds", JSONArray(purchaseSet.productIds.toList()))
            put("startTime", purchaseSet.startTime.time)
            put("endTime", purchaseSet.endTime.time)
            put("collectedAt", purchaseSet.collectedAt.time)
            put("uploadedToModel", purchaseSet.uploadedToModel)
        }
    }
    
    private fun parsePurchaseSet(json: JSONObject): PurchaseSet {
        val productIdsArray = json.getJSONArray("productIds")
        val productIds = mutableSetOf<Int>()
        for (i in 0 until productIdsArray.length()) {
            productIds.add(productIdsArray.getInt(i))
        }
        
        return PurchaseSet(
            id = json.getInt("id"),
            productIds = productIds,
            startTime = java.util.Date(json.getLong("startTime")),
            endTime = java.util.Date(json.getLong("endTime")),
            collectedAt = java.util.Date(json.getLong("collectedAt")),
            uploadedToModel = json.getBoolean("uploadedToModel")
        )
    }
    
    companion object {
        private const val TAG = "PurchaseSetRepo"
        private const val PREFS_NAME = "purchase_sets_prefs"
        private const val KEY_PURCHASE_SETS = "purchase_sets"
        private const val KEY_LAST_COLLECTION_TIME = "last_collection_time"
    }
}

