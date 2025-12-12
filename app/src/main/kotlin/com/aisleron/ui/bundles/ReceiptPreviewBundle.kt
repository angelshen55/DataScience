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


package com.aisleron.ui.bundles

import android.os.Parcelable
import com.aisleron.domain.receipt.ReceiptItem
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
data class ReceiptPreviewBundle(
    val items: List<ReceiptItemParcelable>
) : Parcelable {
    fun toReceiptItems(): List<ReceiptItem> {
        return items.map { it.toReceiptItem() }
    }

    companion object {
        fun fromReceiptItems(receiptItems: List<ReceiptItem>): ReceiptPreviewBundle {
            return ReceiptPreviewBundle(
                items = receiptItems.map { ReceiptItemParcelable.fromReceiptItem(it) }
            )
        }
    }
}

@Parcelize
data class ReceiptItemParcelable(
    val name: String,
    val unitPrice: String, // BigDecimal as String
    val quantity: Double = 0.0
) : Parcelable {
    fun toReceiptItem(): ReceiptItem {
        return ReceiptItem(
            name = name,
            unitPrice = BigDecimal(unitPrice),
            quantity = quantity
        )
    }

    companion object {
        fun fromReceiptItem(item: ReceiptItem): ReceiptItemParcelable {
            return ReceiptItemParcelable(
                name = item.name,
                unitPrice = item.unitPrice.toPlainString(),
                quantity = item.quantity
            )
        }
    }
}
