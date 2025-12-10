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
package com.aisleron.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aisleron.R
import com.aisleron.domain.record.Record
import com.aisleron.domain.product.ProductRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class SimpleRecordAdapter : RecyclerView.Adapter<SimpleRecordAdapter.VH>() {
    private val items = mutableListOf<Record>()
    private val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private var productRepository: ProductRepository? = null

    fun submit(list: List<Record>, productRepo: ProductRepository) {
        items.clear()
        items.addAll(list.sortedByDescending { it.date })
        productRepository = productRepo
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_record, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]

        holder.title.text = "Loading... • ${df.format(r.date)}"
        holder.sub.text = "Qty: ${String.format("%.2f", r.quantity)} | Shop: ${r.shop} | Total: $${String.format("%.2f", r.price * r.quantity)} | Stock: ${r.stock}"
        
        productRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val product = repo.get(r.productId)
                    withContext(Dispatchers.Main) {
                        val productName = product?.name ?: "Unknown Product"
                        holder.title.text = "$productName • ${df.format(r.date)}"
                        holder.sub.text = "Qty: ${String.format("%.2f", r.quantity)} | Shop: ${r.shop} | Total: $${String.format("%.2f", r.price * r.quantity)} | Stock: ${r.stock}"
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        holder.title.text = "Product #${r.productId} • ${df.format(r.date)}"
                        holder.sub.text = "Qty: ${String.format("%.2f", r.quantity)} | Shop: ${r.shop} | Total: $${String.format("%.2f", r.price * r.quantity)}"
                    }  
                }
            }
        }
    }

    override fun getItemCount() = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.txt_title)
        val sub: TextView = view.findViewById(R.id.txt_sub)
    }
}
