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

package com.aisleron.ui.product

import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Tracks recommendation dialog interactions for model feedback
 */
class RecommendationDialogTracker(
    private val context: android.content.Context
) {
    
    private var dialogStartTime: Long = 0
    private var addedProductsCount: Int = 0
    private var totalRecommendedProducts: Int = 0
    private var lastRetrainDecision: Boolean = false
    
    /**
     * Start tracking when dialog is shown
     */
    fun onDialogShown(totalRecommended: Int) {
        dialogStartTime = System.currentTimeMillis()
        addedProductsCount = 0
        totalRecommendedProducts = totalRecommended
        Log.i(TAG, "=== Recommendation dialog shown with $totalRecommended products ===")
        Log.d(TAG, "Dialog start time: $dialogStartTime")
    }
    
    /**
     * Track when a product is added
     */
    fun onProductAdded() {
        addedProductsCount++
        Log.i(TAG, "Product added! Current count: $addedProductsCount/$totalRecommendedProducts")
    }
    
    /**
     * End tracking when dialog is dismissed and log all metrics
     */
    fun onDialogDismissed() {
        Log.i(TAG, "=== Dialog dismissed - calculating metrics ===")
        
        // Safety check: ensure dialog was properly initialized
        if (dialogStartTime == 0L) {
            Log.w(TAG, "WARNING: onDialogDismissed() called before onDialogShown()!")
            return
        }
        
        val dialogDuration = System.currentTimeMillis() - dialogStartTime
        val durationSeconds = dialogDuration / 1000.0
        
        // Calculate metrics
        val addRate = if (totalRecommendedProducts > 0) {
            addedProductsCount.toDouble() / totalRecommendedProducts
        } else {
            0.0
        }
        
        // Calculate feedback signal
        // Rule 1: If less than 1 product added, need to retrain
        val needsRetrainByAddRate = addedProductsCount < 1
        
        // Rule 2: Consider time factor
        // - Very short time (< 3s) + low add rate = quick rejection, needs retrain
        // - Long time (> 10s) + low add rate = considered but rejected, might need retrain
        // - High add rate (>= 1/3) = recommendations are good, no retrain needed
        val needsRetrainByTime = when {
            durationSeconds < 5.0 && addRate <= 0.33 -> true  // Quick Decision
            addRate > 0.33 -> false // Good recommendations
            else -> false
        }
        
        // Combined feedback signal: retrain if either condition is met
        val shouldRetrain = needsRetrainByAddRate || needsRetrainByTime
        
        // Log all metrics
        Log.i(TAG, "=".repeat(60))
        Log.i(TAG, "RECOMMENDATION DIALOG METRICS")
        Log.i(TAG, "=".repeat(60))
        Log.i(TAG, "Dialog Duration: ${String.format("%.2f", durationSeconds)} seconds")
        Log.i(TAG, "Added Products: $addedProductsCount")
        Log.i(TAG, "Total Recommended: $totalRecommendedProducts")
        Log.i(TAG, "Add Rate: ${String.format("%.2f%%", addRate * 100)}")
        Log.i(TAG, "Needs Retrain (by add rate): $needsRetrainByAddRate")
        Log.i(TAG, "Needs Retrain (by time): $needsRetrainByTime")
        Log.i(TAG, "Final Decision: ${if (shouldRetrain) "RETRAIN MODEL" else "KEEP MODEL"}")
        Log.i(TAG, "=".repeat(60))
        
        // Store the decision
        lastRetrainDecision = shouldRetrain
        
        // Send to Firebase Analytics
        sendToFirebase(dialogDuration, addedProductsCount, totalRecommendedProducts, shouldRetrain)
    }
    
    /**
     * Get the retrain decision from the last dialog dismissal
     * This should be called after onDialogDismissed() to get the decision
     */
    fun getLastRetrainDecision(): Boolean {
        return lastRetrainDecision
    }
    
    /**
     * Send metrics to Firebase Analytics
     */
    private fun sendToFirebase(
        durationMs: Long,
        addedCount: Int,
        totalCount: Int,
        shouldRetrain: Boolean
    ) {
        try {
            val firebaseAnalytics = FirebaseAnalytics.getInstance(context)
            
            val bundle = Bundle().apply {
                putLong("dialog_duration_ms", durationMs)
                putInt("added_products", addedCount)
                putInt("total_recommended", totalCount)
                putDouble("add_rate", if (totalCount > 0) addedCount.toDouble() / totalCount else 0.0)
                putBoolean("should_retrain", shouldRetrain)
            }
            
            firebaseAnalytics.logEvent("recommendation_dialog_metrics", bundle)
            Log.d(TAG, "Sent metrics to Firebase Analytics")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send metrics to Firebase Analytics", e)
        }
    }
    
    companion object {
        private const val TAG = "RecommendationTracker"
    }
}

