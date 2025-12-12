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

package com.aisleron.data.record

import androidx.room.Dao
import androidx.room.Query
import com.aisleron.data.base.BaseDao
import java.util.Date

data class RecordWithProductUi(
    val recordId: Int,
    val productId: Int,
    val productName: String,
    val unitPrice: Double,
    val quantity: Double,
    val shop: String,
    val totalCost: Double,
    val date: Date
)

// New data class for purchase history analysis
data class ProductPurchaseHistory(
    val productId: Int,
    val productName: String,
    val purchaseDates: List<Date>
)

@Dao
interface RecordDao : BaseDao<RecordEntity> {
    /**
     * Record
     */
    @Query("SELECT * FROM Record WHERE id = :recordId")
    suspend fun getRecord(recordId: Int): RecordEntity?

    @Query("SELECT * FROM Record")
    suspend fun getRecords(): List<RecordEntity>

    @Query("SELECT * FROM Record WHERE product_id = :productId")
    suspend fun getRecordsByProduct(productId: Int): List<RecordEntity>

    @Query("SELECT * FROM Record WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getRecordsByDateRange(startDate: Long, endDate: Long): List<RecordEntity>

    @Query("""
    SELECT
        r.id AS recordId,
        r.product_id AS productId,
        COALESCE(p.name, 'Unknown Product') AS productName,
        r.price AS unitPrice,
        r.quantity AS quantity,
        r.shop AS shop,
        (r.quantity * r.price) AS totalCost,
        r.date AS date
    FROM Record r
    LEFT JOIN Product p ON p.id = r.product_id
    ORDER BY r.date DESC
    """)
    suspend fun getHistoryUi(): List<RecordWithProductUi>

    @Query(
        """
        SELECT
            r.id AS recordId,
            r.product_id AS productId,
            COALESCE(p.name, 'Unknown Product') AS productName,
            r.price AS unitPrice,
            r.quantity AS quantity,
            r.shop AS shop,
            (r.quantity * r.price) AS totalCost,
            r.date AS date
        FROM Record r
        LEFT JOIN Product p ON p.id = r.product_id
        WHERE (:name IS NULL OR p.name LIKE '%' || :name || '%')
          AND (:shop IS NULL OR r.shop = :shop)
          AND (:startMillis IS NULL OR r.date >= :startMillis)
          AND (:endMillis IS NULL OR r.date <= :endMillis)
        ORDER BY r.date DESC
        """
    )
    suspend fun getHistoryUiFiltered(
        name: String?,
        shop: String?,
        startMillis: Long?,
        endMillis: Long?
    ): List<RecordWithProductUi>

    // New method for getting purchase history for a specific product
    @Query("""
        SELECT r.date
        FROM Record r
        WHERE r.product_id = :productId
        ORDER BY r.date ASC
    """)
    suspend fun getPurchaseDatesForProduct(productId: Int): List<Date>
    
    // New method for getting all products with their purchase counts
    @Query("""
        SELECT 
            r.product_id AS productId,
            COALESCE(p.name, 'Unknown Product') AS productName,
            COUNT(r.id) as purchaseCount
        FROM Record r
        LEFT JOIN Product p ON p.id = r.product_id
        GROUP BY r.product_id, p.name
        ORDER BY purchaseCount DESC
    """)
    suspend fun getProductPurchaseCounts(): List<ProductPurchaseCount>

}

// New data class for product purchase counts
data class ProductPurchaseCount(
    val productId: Int,
    val productName: String,
    val purchaseCount: Int
)
