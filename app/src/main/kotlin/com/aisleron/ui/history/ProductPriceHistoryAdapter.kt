package com.aisleron.ui.history

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aisleron.R
import com.aisleron.domain.product.Product
import com.aisleron.domain.record.Record
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Expandable product-price adapter
 */
class ProductPriceHistoryAdapter : RecyclerView.Adapter<ProductPriceHistoryAdapter.VH>() {
    
    private val items = mutableListOf<ExpandableProductItem>()
    
    private var onItemClickListener: ((Product) -> Unit)? = null

    /**
     * submit data list
     * @param products 
     * @param recordsMap 
     */
    fun submit(products: List<Product>, recordsMap: Map<Int, List<Record>> = emptyMap()) {
        items.clear()
        items.addAll(products.sortedBy { it.name }.map { product ->
            ExpandableProductItem(
                product = product,
                records = recordsMap[product.id] ?: emptyList()
            )
        })
        notifyDataSetChanged()
    }

    /**
     * @param listener 
     */
    fun setOnItemClickListener(listener: (Product) -> Unit) {
        onItemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_product_price_history, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        
        holder.name.text = item.product.name
        holder.price.text = "$${String.format("%.2f", item.product.price)}"
        
        // expand state set
        updateExpandState(holder, item)
        
        if (item.records.isNotEmpty()) {
            setupChart(holder.lineChart, item.records, item.product.name)
            holder.chartTitle.text = "${item.product.name} Price History"
        }
        
        holder.itemView.setOnClickListener {
            toggleExpand(position)
        }
    }

    override fun getItemCount() = items.size

    /**
     * expand state change
     * @param position 
     */
    private fun toggleExpand(position: Int) {
        val item = items[position]
        item.isExpanded = !item.isExpanded
        
        notifyItemChanged(position)
    }

    /**
     * @param holder ViewHolder
     * @param item 
     */
    private fun updateExpandState(holder: VH, item: ExpandableProductItem) {
        if (item.isExpanded) {
            holder.chartContainer.visibility = View.VISIBLE
            holder.expandIndicator.text = "▲"
        } else {
            holder.chartContainer.visibility = View.GONE
            holder.expandIndicator.text = "▼"
        }
    }

    /**
     * @param chart 
     * @param records 
     * @param productName 
     */
    private fun setupChart(chart: LineChart, records: List<Record>, productName: String) {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)

        if (records.isNotEmpty()) {
            val sortedRecords = records.sortedBy { it.date }

            // 创建均匀分布的索引点（最多10个）
            val step = (sortedRecords.size - 1).coerceAtLeast(1) / 9f
            val indices = (0 until minOf(sortedRecords.size, 10)).map { it * step }

            // X轴配置
            val xAxis = chart.xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f // 设置最小间隔为1
            xAxis.valueFormatter = object : ValueFormatter() {
                private val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
                override fun getFormattedValue(value: Float): String {
                    val index = value.toInt().coerceIn(0, sortedRecords.size - 1)
                    return dateFormat.format(sortedRecords[index].date)
                }
            }
            xAxis.setLabelCount(minOf(sortedRecords.size, 10), true) // 最多10个标签

            // Y轴配置
            chart.axisLeft.setDrawGridLines(true)
            chart.axisRight.isEnabled = false
            chart.legend.isEnabled = true

            // 创建数据点（使用索引作为X值）
            val entries = sortedRecords.mapIndexed { index, _ ->
                Entry(index.toFloat(), sortedRecords[index].price.toFloat())
            }

            val dataSet = LineDataSet(entries, "$productName Price History")
            dataSet.color = Color.BLUE
            dataSet.setCircleColor(Color.BLACK)
            dataSet.lineWidth = 2f
            dataSet.circleRadius = 3f
            dataSet.setDrawValues(false)
            dataSet.valueTextSize = 8f

            val lineData = LineData(dataSet)
            chart.data = lineData
            chart.invalidate()
        }
    }

    /**
     * ViewHolder 
     */
    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.txt_product_name)
        val price: TextView = view.findViewById(R.id.txt_product_price)
        val expandIndicator: TextView = view.findViewById(R.id.txt_expand_indicator)
        val chartContainer: LinearLayout = view.findViewById(R.id.layout_chart_container)
        val chartTitle: TextView = view.findViewById(R.id.txt_chart_title)
        val lineChart: LineChart = view.findViewById(R.id.line_chart)
    }
}