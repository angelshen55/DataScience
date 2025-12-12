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

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aisleron.domain.receipt.ReceiptItem
import com.aisleron.R
import com.aisleron.utils.PriceInputUtils

class ReceiptPreviewAdapter(
    private val onDelete: (Int) -> Unit,
    private val onUpdate: (Int, ReceiptItem) -> Unit,
    private val onSelectAisle: (Int) -> Unit,
    private val getAisleName: (Int) -> String? = { null }
) : ListAdapter<ReceiptItem, ReceiptPreviewAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_receipt_row, parent, false)
        return VH(view, onDelete, onUpdate, onSelectAisle, getAisleName)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), position)
    }

    class VH(
        itemView: View,
        private val onDelete: (Int) -> Unit,
        private val onUpdate: (Int, ReceiptItem) -> Unit,
        private val onSelectAisle: (Int) -> Unit,
        private val getAisleName: (Int) -> String?
    ) : RecyclerView.ViewHolder(itemView) {
        val etName: EditText = itemView.findViewById(R.id.et_name)
        val etPrice: EditText = itemView.findViewById(R.id.et_price)
        val etQty: EditText = itemView.findViewById(R.id.et_qty)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete)
        val tvAisle: TextView = itemView.findViewById(R.id.tv_aisle)

        private var nameWatcher: TextWatcher? = null
        private var priceWatcher: TextWatcher? = null
        private var qtyWatcher: TextWatcher? = null
        private var isUpdating = false
        private var currentItem: ReceiptItem? = null

        fun bind(item: ReceiptItem, position: Int) {
            // 移除旧的监听器
            nameWatcher?.let { etName.removeTextChangedListener(it) }
            priceWatcher?.let { etPrice.removeTextChangedListener(it) }
            qtyWatcher?.let { etQty.removeTextChangedListener(it) }

            // 设置删除按钮
            btnDelete.setOnClickListener {
                onDelete(bindingAdapterPosition)
            }

            isUpdating = true

            // 保存当前焦点和光标位置
            val nameHasFocus = etName.hasFocus()
            val priceHasFocus = etPrice.hasFocus()
            val qtyHasFocus = etQty.hasFocus()

            val nameSelection = if (nameHasFocus) etName.selectionEnd else -1
            val priceSelection = if (priceHasFocus) etPrice.selectionEnd else -1
            val qtySelection = if (qtyHasFocus) etQty.selectionEnd else -1

            // 只在文本不同且该字段没有焦点时才更新
            // 如果字段有焦点，说明用户正在编辑，不应该被覆盖
            val currentName = etName.text.toString()
            if (currentName != item.name && !nameHasFocus) {
                etName.setText(item.name)
            }

            if (!priceHasFocus) {
                PriceInputUtils.setPriceText(etPrice, item.unitPrice.toDouble())
            }

            val qtyText = item.quantity.toString()
            val currentQty = etQty.text.toString()
            if (currentQty != qtyText && !qtyHasFocus) {
                etQty.setText(qtyText)
            }

            // 恢复焦点和光标位置（使用 post 确保在 UI 更新后执行）
            etName.post {
                if (nameHasFocus && nameSelection >= 0) {
                    val newSelection = minOf(nameSelection, etName.text.length)
                    if (newSelection >= 0) {
                        etName.setSelection(newSelection)
                        etName.requestFocus()
                    }
                }
            }

            etPrice.post {
                if (priceHasFocus && priceSelection >= 0) {
                    val newSelection = minOf(priceSelection, etPrice.text.length)
                    if (newSelection >= 0) {
                        etPrice.setSelection(newSelection)
                        etPrice.requestFocus()
                    }
                }
            }

            etQty.post {
                if (qtyHasFocus && qtySelection >= 0) {
                    val newSelection = minOf(qtySelection, etQty.text.length)
                    if (newSelection >= 0) {
                        etQty.setSelection(newSelection)
                        etQty.requestFocus()
                    }
                }
            }

            // 更新 currentItem 以反映最新的绑定值
            currentItem = item
            isUpdating = false

            // 设置 aisle 选择监听器
            tvAisle.setOnClickListener {
                onSelectAisle(bindingAdapterPosition)
            }

            // 更新 aisle 显示
            val aisleName = getAisleName(bindingAdapterPosition)
            tvAisle.text = if (aisleName != null) "Aisle: $aisleName" else "Aisle: Not selected"

            // 文本变化仅更新本地状态，不触发列表刷新，避免光标抖动
            nameWatcher = createTextWatcher { text ->
                if (!isUpdating) {
                    val current = currentItem ?: return@createTextWatcher
                    currentItem = current.copy(name = text)
                }
            }
            etName.addTextChangedListener(nameWatcher)

            priceWatcher = createTextWatcher { _ ->
                if (!isUpdating) {
                    // 仅更新本地 currentItem 的价格文本由提交动作处理
                }
            }
            etPrice.addTextChangedListener(priceWatcher)

            qtyWatcher = createTextWatcher { text ->
                if (!isUpdating) {
                    val current = currentItem ?: return@createTextWatcher
                    val qty = text.toDoubleOrNull() ?: current.quantity
                    currentItem = current.copy(quantity = qty)
                }
            }
            etQty.addTextChangedListener(qtyWatcher)

            // 回车/IME 提交：价格使用 PriceInputUtils，名称与数量使用类似的提交策略
            PriceInputUtils.setupPriceEnterListenerWithIme(etPrice) {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    val current = currentItem ?: item
                    val priceTextNow = etPrice.text.toString()
                    val price = priceTextNow.toDoubleOrNull() ?: current.unitPrice.toDouble()
                    val newItem = ReceiptItem(
                        name = etName.text.toString(),
                        unitPrice = price.toBigDecimal(),
                        quantity = etQty.text.toString().toDoubleOrNull() ?: current.quantity
                    )
                    onUpdate(bindingAdapterPosition, newItem)
                }
            }

            etName.setOnEditorActionListener { _, actionId, _ ->
                val commit = actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                        actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
                if (commit && bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    val current = currentItem ?: item
                    val newItem = current.copy(
                        name = etName.text.toString(),
                        unitPrice = (etPrice.text.toString().toDoubleOrNull() ?: current.unitPrice.toDouble()).toBigDecimal(),
                        quantity = etQty.text.toString().toDoubleOrNull() ?: current.quantity
                    )
                    onUpdate(bindingAdapterPosition, newItem)
                    true
                } else false
            }

            etQty.setOnEditorActionListener { _, actionId, _ ->
                val commit = actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                        actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
                if (commit && bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    val current = currentItem ?: item
                    val newItem = current.copy(
                        name = etName.text.toString(),
                        unitPrice = (etPrice.text.toString().toDoubleOrNull() ?: current.unitPrice.toDouble()).toBigDecimal(),
                        quantity = etQty.text.toString().toDoubleOrNull() ?: current.quantity
                    )
                    onUpdate(bindingAdapterPosition, newItem)
                    true
                } else false
            }
        }

        private fun createTextWatcher(onChange: (String) -> Unit): TextWatcher {
            return object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    onChange(s?.toString().orEmpty())
                }
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ReceiptItem>() {
            // 使用一个更稳定的标识符来判断是否是同一个 item
            // 这里我们使用位置索引，但由于 ListAdapter 的特性，我们使用内容比较
            override fun areItemsTheSame(oldItem: ReceiptItem, newItem: ReceiptItem): Boolean {
                // 对于编辑场景，如果 name 和 price 都相同，认为是同一个 item
                // 但这可能导致问题，所以我们简化：始终返回 true 让 DiffUtil 使用 areContentsTheSame
                // 实际上，由于我们每次都在更新整个列表，位置就是标识符
                return oldItem.name == newItem.name && oldItem.unitPrice == newItem.unitPrice
            }

            override fun areContentsTheSame(oldItem: ReceiptItem, newItem: ReceiptItem): Boolean =
                oldItem == newItem
        }
    }
}
