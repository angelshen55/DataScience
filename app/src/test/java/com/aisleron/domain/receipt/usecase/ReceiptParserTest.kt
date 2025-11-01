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

package com.aisleron.domain.receipt.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import com.aisleron.domain.receipt.ReceiptParser

class ReceiptParserTest {

    @Test
    fun parse_simple_en_receipt() {
        val text = """
            Walmart Store 123
            Apple Gala 1kg     $3.49
            Banana x2          $1.20
            SUBTOTAL           $4.69
            TAX                $0.00
            TOTAL              $4.69
        """.trimIndent()

        val result = ReceiptParser.parse(text, Locale.US)

        assertEquals(2, result.items.size)
        assertTrue(result.errors.isEmpty())
        assertTrue(result.items.any { it.name.contains("Apple") && it.unitPrice.toPlainString() == "3.49" })
        assertTrue(result.items.any { it.name.contains("Banana") && it.unitPrice.toPlainString() == "1.20" })
    }

    @Test
    fun parse_multiline_price_merge() {
        val text = """
            牛奶 1L
            ¥ 12,90
            面包 500g  ¥ 8,50
            合计 ¥ 21,40
        """.trimIndent()

        val result = ReceiptParser.parse(text, Locale.CHINA)
        assertEquals(2, result.items.size)
        assertTrue(result.items.any { it.name.contains("牛奶") })
        assertTrue(result.items.any { it.name.contains("面包") })
    }

    @Test
    fun ignore_totals_and_tax() {
        val text = """
            Water 1.5L     1,29€
            TOTAL          1,29€
            Change         0,00€
        """.trimIndent()

        val result = ReceiptParser.parse(text, Locale.GERMANY)
        assertEquals(1, result.items.size)
        assertTrue(result.ignoredLines.any { it.contains("TOTAL", ignoreCase = true) })
    }
}


