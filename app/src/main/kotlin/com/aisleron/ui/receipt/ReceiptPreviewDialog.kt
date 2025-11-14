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

package com.aisleron.ui.receipt

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import com.aisleron.domain.receipt.ReceiptItem
import com.aisleron.R
import java.math.BigDecimal

class ReceiptPreviewDialog(
    private val initialItems: List<ReceiptItem>,
    private val onCancelImport: () -> Unit,
    private val onConfirmImport: (List<ReceiptItem>) -> Unit
) : DialogFragment() {

    private lateinit var adapter: ReceiptPreviewAdapter

    private var tvSummary: TextView? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_receipt_preview, null)
        val rv = view.findViewById<RecyclerView>(R.id.rv_items)
        tvSummary = view.findViewById(R.id.tv_summary)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)
        val btnImport = view.findViewById<Button>(R.id.btn_import)

        adapter = ReceiptPreviewAdapter(
            onDelete = { index ->
                val list = adapter.currentList.toMutableList()
                if (index in list.indices) {
                    list.removeAt(index)
                    adapter.submitList(list)
                    updateSummary()
                }
            },
            onUpdate = { index, item ->
                val list = adapter.currentList.toMutableList()
                if (index in list.indices) {
                    list[index] = item
                    adapter.submitList(list)
                    updateSummary()
                }
            },
            onSelectAisle = { index ->
                // Dialog version doesn't support aisle selection
            }
        )
        rv.adapter = adapter
        adapter.submitList(initialItems)
        updateSummary()

        btnCancel.setOnClickListener {
            onCancelImport()
            dismiss()
        }

        btnImport.setOnClickListener {
            onConfirmImport(adapter.currentList)
            dismiss()
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
    }

    private fun updateSummary() {
        val items = adapter.currentList
        val total = items.fold(BigDecimal.ZERO) { acc, it ->
            acc + (it.unitPrice.multiply(BigDecimal(it.quantity)))
        }
        tvSummary?.text = "Items: ${items.size}   Total: ${total.toPlainString()}"
    }
}