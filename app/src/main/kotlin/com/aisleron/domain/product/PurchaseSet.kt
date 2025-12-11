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

import java.util.Date

/**
 * Represents a set of products purchased within a 2-hour time window.
 * This is used as training data for the recommendation model.
 * 
 * @param id Unique identifier for this purchase set
 * @param productIds Set of product IDs purchased together (within 2 hours)
 * @param startTime The start time of the 2-hour window
 * @param endTime The end time of the 2-hour window
 * @param collectedAt When this set was collected for training
 * @param uploadedToModel Whether this set has been uploaded to the model
 */
data class PurchaseSet(
    val id: Int = 0,
    val productIds: Set<Int>,
    val startTime: Date,
    val endTime: Date,
    val collectedAt: Date = Date(),
    val uploadedToModel: Boolean = false
) {
    /**
     * Returns the product IDs as a sorted list for consistent representation
     */
    fun getProductIdsAsList(): List<Int> = productIds.sorted()
    
    /**
     * Returns a unique hash for this purchase set based on product IDs and time window
     * Used to avoid duplicate collection
     */
    fun getUniqueHash(): String {
        val productHash = productIds.sorted().joinToString(",")
        val timeHash = "${startTime.time}_${endTime.time}"
        return "${productHash}_$timeHash".hashCode().toString()
    }
    
    /**
     * Converts product IDs to a formatted string list for model upload
     * Format: ['Product1', 'Product2', 'Product3']
     * Note: This requires product names to be resolved from IDs externally
     */
    fun formatProductNames(productNames: List<String>): String {
        val sortedNames = productNames.sorted()
        val formattedNames = sortedNames.joinToString(", ") { "'$it'" }
        return "[$formattedNames]"
    }
}

