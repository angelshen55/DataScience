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

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Manual Room migrations.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `Location` ADD COLUMN `showDefaultAisle` INTEGER NOT NULL DEFAULT 1"
        )
        db.execSQL(
            "ALTER TABLE `Aisle` ADD COLUMN `expanded` INTEGER NOT NULL DEFAULT 1"
        )
    }
}

val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create LoyaltyCard table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `LoyaltyCard` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `provider` TEXT NOT NULL,
                `intent` TEXT NOT NULL
            )
            """.trimIndent()
        )

        // Create LocationLoyaltyCard table with foreign keys
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `LocationLoyaltyCard` (
                `locationId` INTEGER NOT NULL,
                `loyaltyCardId` INTEGER NOT NULL,
                PRIMARY KEY(`locationId`),
                FOREIGN KEY(`locationId`) REFERENCES `Location`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`loyaltyCardId`) REFERENCES `LoyaltyCard`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `Product` ADD COLUMN `qtyNeeded` INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "ALTER TABLE `Product` ADD COLUMN `price` REAL NOT NULL DEFAULT 0.0"
        )
    }
}

val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        
    }
}

val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create Record table with quantity and shop columns
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `Record` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `product_id` INTEGER NOT NULL,
                `date` INTEGER NOT NULL,
                `stock` INTEGER NOT NULL,
                `price` REAL NOT NULL,
                `quantity` INTEGER NOT NULL DEFAULT 1,
                `shop` TEXT NOT NULL DEFAULT 'shop1'
            )
            """.trimIndent()
        )

        // Add isDeleted column to Product table for soft delete
        db.execSQL(
            "ALTER TABLE `Product` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `Record_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `product_id` INTEGER NOT NULL,
                `date` INTEGER NOT NULL,
                `stock` INTEGER NOT NULL,
                `price` REAL NOT NULL,
                `quantity` REAL NOT NULL DEFAULT 0.0,
                `shop` TEXT NOT NULL DEFAULT 'shop1'
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO `Record_new` (id, product_id, date, stock, price, quantity, shop)
            SELECT id, product_id, date, stock, price, CAST(quantity AS REAL), shop FROM `Record`
            """.trimIndent()
        )

        db.execSQL("DROP TABLE `Record`")
        db.execSQL("ALTER TABLE `Record_new` RENAME TO `Record`")
    }
}
