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
        holder.sub.text = "Qty: ${r.quantity} | Shop: ${r.shop} | Total: $${String.format("%.2f", r.price * r.quantity)} | Stock: ${r.stock}"
        
        productRepository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val product = repo.get(r.productId)
                    withContext(Dispatchers.Main) {
                        val productName = product?.name ?: "Unknown Product"
                        holder.title.text = "$productName • ${df.format(r.date)}"
                        holder.sub.text = "Qty: ${r.quantity} | Shop: ${r.shop} | Total: $${String.format("%.2f", r.price * r.quantity)} | Stock: ${r.stock}"
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        holder.title.text = "Product #${r.productId} • ${df.format(r.date)}"
                        holder.sub.text = "Qty: ${r.quantity} | Shop: ${r.shop} | Total: $${String.format("%.2f", r.price * r.quantity)}"
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