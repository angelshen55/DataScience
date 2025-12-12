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

import com.aisleron.domain.product.Product
import com.aisleron.domain.product.ProductRecommendation
import com.aisleron.domain.product.ProductRepository
import com.aisleron.domain.record.RecordRepository
import java.util.Calendar
import java.util.Date
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class GetProductRecommendationsUseCase(
    private val productRepository: ProductRepository,
    private val recordRepository: RecordRepository
) {
    // Weights for the three factors
    private val FREQUENCY_WEIGHT = 0.8
    private val TIME_DECAY_WEIGHT = 0.15
    private val PERIODICITY_WEIGHT = 0.05
    
    suspend operator fun invoke(limit: Int = 10): List<ProductRecommendation> {
        // Get all products and their purchase counts
        val productPurchaseCounts = recordRepository.getProductPurchaseCounts()
        
        // Fetch products
        val activeProducts = productRepository.getAll()
        val allProducts = productRepository.getAllIncludingDeleted()
        
        // Convert to map for easier lookup
        val productMap = allProducts.associateBy { it.id }
        val activeProductIds = activeProducts.map { it.id }.toSet()
        
        // Calculate recommendations for each product
        val recommendations = productPurchaseCounts.mapNotNull { purchaseCount ->
            val product = productMap[purchaseCount.productId] ?: return@mapNotNull null
            
            // Skip products that are already tracked (present in needed or stock lists)
            if (activeProductIds.contains(product.id)) return@mapNotNull null
            
            // Get purchase dates for this product
            val purchaseDates = recordRepository.getPurchaseDatesForProduct(product.id)
            
            // Need at least 2 purchases to calculate periodicity
            if (purchaseDates.size < 2) return@mapNotNull null
            
            // Calculate scores
            val frequencyScore = calculateFrequencyScore(purchaseCount.purchaseCount)
            val timeDecayScore = calculateTimeDecayScore(purchaseDates.lastOrNull())
            val periodicityScore = calculatePeriodicityScore(purchaseDates)
            
            // Calculate weighted composite score
            val compositeScore = (frequencyScore * FREQUENCY_WEIGHT) +
                               (timeDecayScore * TIME_DECAY_WEIGHT) +
                               (periodicityScore * PERIODICITY_WEIGHT)
            
            // Calculate additional metrics for display
            val daysSinceLastPurchase = calculateDaysSinceLastPurchase(purchaseDates.lastOrNull())
            val averagePurchaseInterval = calculateAveragePurchaseInterval(purchaseDates)
            
            ProductRecommendation(
                product = product,
                score = compositeScore,
                purchaseCount = purchaseCount.purchaseCount,
                frequencyScore = frequencyScore,
                timeDecayScore = timeDecayScore,
                periodicityScore = periodicityScore,
                daysSinceLastPurchase = daysSinceLastPurchase,
                averagePurchaseInterval = averagePurchaseInterval
            )
        }
        
        // Sort by score descending and limit results
        return recommendations.sortedByDescending { it.score }.take(limit)
    }
    
    private fun calculateFrequencyScore(purchaseCount: Int): Double {
        // Simple linear scaling - more purchases = higher score
        // Cap at 100 purchases for normalization
        return min(purchaseCount.toDouble() / 100.0, 1.0)
    }
    
    private fun calculateTimeDecayScore(lastPurchaseDate: Date?): Double {
        if (lastPurchaseDate == null) return 0.0
        
        val daysSinceLastPurchase = calculateDaysSinceLastPurchase(lastPurchaseDate)
        
        // Exponential decay - more recent purchases get lower scores
        // But we invert this since we want to recommend items that were purchased long ago
        // Max score for items not purchased in 365 days
        return min(daysSinceLastPurchase.toDouble() / 365.0, 1.0)
    }
    
    private fun calculatePeriodicityScore(purchaseDates: List<Date>): Double {
        if (purchaseDates.size < 2) return 0.0
        
        // Calculate intervals between purchases
        val intervals = mutableListOf<Long>()
        for (i in 1 until purchaseDates.size) {
            val interval = (purchaseDates[i].time - purchaseDates[i-1].time) / (1000 * 60 * 60 * 24)
            intervals.add(interval)
        }
        
        // Calculate average interval
        val averageInterval = intervals.average()
        
        // Calculate variance
        val variance = intervals.map { (it - averageInterval) * (it - averageInterval) }.average()
        val stdDeviation = kotlin.math.sqrt(variance)
        
        // Lower variance (more regular purchasing pattern) gets higher score
        // Convert standard deviation to a 0-1 score (lower std deviation = higher score)
        return if (stdDeviation == 0.0) 1.0 else min(1.0 / stdDeviation, 1.0)
    }
    
    private fun calculateDaysSinceLastPurchase(lastPurchaseDate: Date?): Long {
        if (lastPurchaseDate == null) return 0
        
        val now = Date()
        val diff = now.time - lastPurchaseDate.time
        return diff / (1000 * 60 * 60 * 24)
    }
    
    private fun calculateAveragePurchaseInterval(purchaseDates: List<Date>): Double {
        if (purchaseDates.size < 2) return 0.0
        
        val intervals = mutableListOf<Long>()
        for (i in 1 until purchaseDates.size) {
            val interval = (purchaseDates[i].time - purchaseDates[i-1].time) / (1000 * 60 * 60 * 24)
            intervals.add(interval)
        }
        
        return intervals.average()
    }
}