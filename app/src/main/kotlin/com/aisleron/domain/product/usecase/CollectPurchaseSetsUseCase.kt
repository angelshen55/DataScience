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

package com.aisleron.domain.product.usecase

import com.aisleron.domain.product.PurchaseSet
import com.aisleron.domain.product.PurchaseSetRepository
import com.aisleron.domain.record.Record
import com.aisleron.domain.record.RecordRepository
import java.util.Date

/**
 * Use case for collecting purchase sets from purchase history
 * Groups products purchased within 2 hours into sets for model training
 */
interface CollectPurchaseSetsUseCase {
    /**
     * Collect purchase sets from recent purchase history
     * @param daysBack Number of days to look back (default: 7 days)
     * @return Number of new purchase sets collected
     */
    suspend operator fun invoke(daysBack: Int = 7): Int
}

class CollectPurchaseSetsUseCaseImpl(
    private val recordRepository: RecordRepository,
    private val purchaseSetRepository: PurchaseSetRepository
) : CollectPurchaseSetsUseCase {
    
    companion object {
        private const val TAG = "CollectPurchaseSets"
        private const val TWO_HOURS_MS = 2 * 60 * 60 * 1000L // 2 hours in milliseconds
    }
    
    override suspend operator fun invoke(daysBack: Int): Int {
        println("[$TAG] Starting purchase set collection (looking back $daysBack days)")
        
        // Get the last collection time to avoid re-collecting old data
        val lastCollectionTime = purchaseSetRepository.getLastCollectionTime()
        val now = System.currentTimeMillis()
        val startTime = if (lastCollectionTime != null) {
            // Only collect data since last collection
            lastCollectionTime
        } else {
            // First time collection: look back specified days
            now - (daysBack * 24 * 60 * 60 * 1000L)
        }
        
        // Log time range for debugging
        val startDateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(startTime))
        val endDateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(now))
        println("[$TAG] Querying records from $startDateStr to $endDateStr")
        
        // Get all purchase records since startTime
        val allRecords = recordRepository.getRecordsByDateRange(startTime, now)
        println("[$TAG] Found ${allRecords.size} total records in time range")
        
        // Filter for purchased items (stock = true means inStock, i.e., purchased)
        val records = allRecords
            .filter { it.stock } // Only stock = true (purchased items)
            .sortedBy { it.date.time }
        
        println("[$TAG] After filtering for stock=true: ${records.size} purchase records")
        
        // Log sample records for debugging
        if (allRecords.isNotEmpty() && records.isEmpty()) {
            println("[$TAG] WARN: All records have stock=false. Sample records:")
            allRecords.take(5).forEach { record ->
                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(record.date)
                println("[$TAG] WARN:   Record: productId=${record.productId}, date=$dateStr, stock=${record.stock}")
            }
        }
        
        if (records.isEmpty()) {
            println("[$TAG] No purchase records found in the specified time range")
            purchaseSetRepository.updateLastCollectionTime(now)
            return 0
        }
        
        println("[$TAG] Processing ${records.size} purchase records")
        
        // Group records into 2-hour windows
        val purchaseSets = groupRecordsIntoPurchaseSets(records)
        
        // Add new purchase sets to repository (avoiding duplicates)
        var newSetsCount = 0
        purchaseSets.forEach { purchaseSet ->
            val hash = purchaseSet.getUniqueHash()
            if (!purchaseSetRepository.existsByHash(hash)) {
                val id = purchaseSetRepository.add(purchaseSet)
                if (id > 0) {
                    newSetsCount++
                }
            }
        }
        
        // Update last collection time
        purchaseSetRepository.updateLastCollectionTime(now)
        
        println("[$TAG] Collection complete: $newSetsCount new purchase sets collected from ${purchaseSets.size} total sets")
        return newSetsCount
    }
    
    /**
     * Groups purchase records into sets based on 2-hour time windows
     */
    private fun groupRecordsIntoPurchaseSets(records: List<Record>): List<PurchaseSet> {
        if (records.isEmpty()) return emptyList()
        
        val purchaseSets = mutableListOf<PurchaseSet>()
        var currentWindowStart = records.first().date.time
        var currentWindowEnd = currentWindowStart + TWO_HOURS_MS
        val currentProductIds = mutableSetOf<Int>()
        
        records.forEach { record ->
            val recordTime = record.date.time
            
            // If record is within current 2-hour window, add to current set
            if (recordTime <= currentWindowEnd) {
                currentProductIds.add(record.productId)
            } else {
                // Start a new window
                // First, save the previous set if it has products
                if (currentProductIds.isNotEmpty()) {
                    purchaseSets.add(
                        PurchaseSet(
                            productIds = currentProductIds.toSet(),
                            startTime = Date(currentWindowStart),
                            endTime = Date(currentWindowEnd)
                        )
                    )
                }
                
                // Start new window
                currentWindowStart = recordTime
                currentWindowEnd = currentWindowStart + TWO_HOURS_MS
                currentProductIds.clear()
                currentProductIds.add(record.productId)
            }
        }
        
        // Don't forget the last set
        if (currentProductIds.isNotEmpty()) {
            purchaseSets.add(
                PurchaseSet(
                    productIds = currentProductIds.toSet(),
                    startTime = Date(currentWindowStart),
                    endTime = Date(currentWindowEnd)
                )
            )
        }
        
        return purchaseSets
    }
}
