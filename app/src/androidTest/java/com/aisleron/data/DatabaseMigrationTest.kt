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

package com.aisleron.data

import android.content.ContentValues
import android.database.Cursor
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQueryBuilder
import androidx.test.platform.app.InstrumentationRegistry
import com.aisleron.domain.FilterType
import com.aisleron.domain.location.LocationType
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.runBlocking
import kotlin.test.assertFalse
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals


class DatabaseMigrationTest {
    private val testDb = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AisleronDatabase::class.java
    )

    private fun populateV1Database(db: SupportSQLiteDatabase) {
        val locationValues = ContentValues()
        locationValues.put("type", LocationType.HOME.toString())
        locationValues.put("defaultFilter", FilterType.NEEDED.toString())
        locationValues.put("name", "Home")
        locationValues.put("pinned", false)

        // Database has schema version 1. Insert some data using SQL queries.
        // You can't use DAO classes because they expect the latest schema.
        val locationId = db.insert(
            "Location", android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL, locationValues
        )

        val aisleValues = ContentValues()
        aisleValues.put("name", "No Aisle")
        aisleValues.put("locationId", locationId)
        aisleValues.put("rank", 1)
        aisleValues.put("isDefault", true)

        db.insert("Aisle", android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL, aisleValues)

        val productValues = ContentValues()
        productValues.put("name", "Migration Test Product")
        productValues.put("inStock", true)

        db.insert("Product", android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL, productValues)

    }

    @Test
    @Throws(IOException::class)
    fun migrate1to2() {
        helper.createDatabase(testDb, 1).apply {
            populateV1Database(this)
            close()
        }

        // Re-open the database with version 2
        val db = helper.runMigrationsAndValidate(testDb, 2, true)

        // MigrationTestHelper automatically verifies the schema changes,
        // but you need to validate that the data was migrated properly.
        var showDefaultAisle: Int
        db.apply {
            val queryBuilder = SupportSQLiteQueryBuilder.builder("Location")
            val cursor: Cursor = query(queryBuilder.create())
            cursor.moveToFirst()
            showDefaultAisle = cursor.getInt(cursor.getColumnIndex("showDefaultAisle"))
            cursor.close()
            close()
        }

        assertEquals(1, showDefaultAisle)
    }

    @Test
    @Throws(IOException::class)
    fun migrate2to3() {
        helper.createDatabase(testDb, 2).apply {
            populateV1Database(this)
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 3, true)

        db.apply {
            val queryBuilder = SupportSQLiteQueryBuilder.builder("LoyaltyCard")
            val cursor: Cursor = query(queryBuilder.create())
            assertEquals(0, cursor.count)
            cursor.close()
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate3to4() {
        helper.createDatabase(testDb, 3).apply {
            populateV1Database(this)
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 4, true)
        var qtyNeeded = -1

        db.apply {
            val queryBuilder = SupportSQLiteQueryBuilder.builder("Product")
            val cursor: Cursor = query(queryBuilder.create())
            cursor.moveToFirst()
            qtyNeeded = cursor.getInt(cursor.getColumnIndex("qtyNeeded"))
            cursor.close()
            close()
        }
        assertEquals(0, qtyNeeded)
    }

    @Test
    @Throws(IOException::class)
    fun migrate4to5() {
        helper.createDatabase(testDb, 4).apply {
            insertProductV4(this)
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 5, true)

        db.apply {
            val cursor = query("SELECT price FROM Product WHERE name = ?", arrayOf(PRODUCT_NAME_V4))
            cursor.moveToFirst()
            assertEquals(0.0, cursor.getDouble(cursor.getColumnIndex("price")))
            cursor.close()
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate5to6() {
        helper.createDatabase(testDb, 5).apply {
            insertProductV5(this)
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 6, true)

        db.apply {
            val productCursor =
                query("SELECT id, isDeleted FROM Product WHERE name = ?", arrayOf(PRODUCT_NAME_V5))
            productCursor.moveToFirst()
            val productId = productCursor.getInt(productCursor.getColumnIndex("id"))
            assertEquals(0, productCursor.getInt(productCursor.getColumnIndex("isDeleted")))
            productCursor.close()

            val recordValues = ContentValues().apply {
                put("product_id", productId)
                put("date", System.currentTimeMillis())
                put("stock", 1)
                put("price", 1.99)
            }
            insert("Record", android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL, recordValues)

            val recordCursor =
                query("SELECT quantity, shop FROM Record WHERE product_id = ?", arrayOf(productId.toString()))
            recordCursor.moveToFirst()
            assertEquals(1, recordCursor.getInt(recordCursor.getColumnIndex("quantity")))
            assertEquals("shop1", recordCursor.getString(recordCursor.getColumnIndex("shop")))
            recordCursor.close()

            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrateAll() {
        helper.createDatabase(testDb, 1).apply {
            populateV1Database(this)
            close()
        }

        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AisleronDatabase::class.java,
            testDb
        ).build()

        // LoyaltyCard introduced in V3
        val loyaltyCards = runBlocking { db.loyaltyCardDao().getLoyaltyCards() }
        assertNotNull(loyaltyCards)

        // Product.qtyNeeded introduced in V4
        val product = runBlocking {
            db.productDao().getProductByName(LEGACY_PRODUCT_NAME)
        }
        assertNotNull(product)
        val migratedProduct = requireNotNull(product)
        assertEquals(0, migratedProduct.qtyNeeded)
        assertEquals(0.0, migratedProduct.price)
        assertFalse(migratedProduct.isDeleted)

        db.close()
    }

    private fun insertProductV4(db: SupportSQLiteDatabase) {
        val productValues = ContentValues().apply {
            put("name", PRODUCT_NAME_V4)
            put("inStock", true)
            put("qtyNeeded", 3)
        }
        db.insert("Product", android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL, productValues)
    }

    private fun insertProductV5(db: SupportSQLiteDatabase) {
        val productValues = ContentValues().apply {
            put("name", PRODUCT_NAME_V5)
            put("inStock", true)
            put("qtyNeeded", 5)
            put("price", 2.49)
        }
        db.insert("Product", android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL, productValues)
    }

    private companion object {
        private const val LEGACY_PRODUCT_NAME = "Migration Test Product"
        private const val PRODUCT_NAME_V4 = "Migration Test Product V4"
        private const val PRODUCT_NAME_V5 = "Migration Test Product V5"
    }
}