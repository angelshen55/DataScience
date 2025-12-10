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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aisleron.R
import com.aisleron.domain.aisle.Aisle
import com.aisleron.domain.aisle.AisleRepository
import com.aisleron.domain.location.Location
import com.aisleron.domain.location.LocationType
import com.aisleron.domain.FilterType
import com.aisleron.domain.location.LocationRepository
import com.aisleron.domain.product.Product
import com.aisleron.domain.product.usecase.AddProductUseCase
import com.aisleron.domain.record.Record
import com.aisleron.domain.record.RecordRepository
import com.aisleron.ui.bundles.Bundler
import com.aisleron.ui.bundles.ReceiptPreviewBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.math.BigDecimal
import java.util.Date

class ReceiptPreviewFragment : Fragment() {

    private lateinit var adapter: ReceiptPreviewAdapter
    private var tvSummary: TextView? = null
    private var initialItems: List<com.aisleron.domain.receipt.ReceiptItem> = emptyList()

    // 存储每个 item 的 aisle 选择
    private val itemAisleMap = mutableMapOf<Int, Aisle?>()
    private val itemAisleLocationName = mutableMapOf<Int, String>()

    // Repositories and UseCases
    private val locationRepository: LocationRepository by inject()
    private val aisleRepository: AisleRepository by inject()
    private val productRepository: com.aisleron.domain.product.ProductRepository by inject()
    private val aisleProductRepository: com.aisleron.domain.aisleproduct.AisleProductRepository by inject()
    private val addProductUseCase: AddProductUseCase by inject()
    private val recordRepository: RecordRepository by inject()
    private val addLocationUseCase: com.aisleron.domain.location.usecase.AddLocationUseCase by inject()
    private val addAisleUseCase: com.aisleron.domain.aisle.usecase.AddAisleUseCase by inject()
    private val getLocationUseCase: com.aisleron.domain.location.usecase.GetLocationUseCase by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // 从参数中获取初始商品列表
            val bundle = Bundler().getReceiptPreviewBundle(arguments)
            initialItems = bundle?.toReceiptItems() ?: emptyList()
            if (initialItems.isEmpty()) {
                android.util.Log.w("ReceiptPreviewFragment", "No items received in bundle")
            }
        } catch (e: Exception) {
            android.util.Log.e("ReceiptPreviewFragment", "Error getting items from bundle", e)
            initialItems = emptyList()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_receipt_preview, container, false)

        val rv = view.findViewById<RecyclerView>(R.id.rv_items)
        tvSummary = view.findViewById(R.id.tv_summary)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)
        val btnImport = view.findViewById<Button>(R.id.btn_import)

        // 使用延迟更新避免频繁刷新导致光标跳动
        var pendingRunnable: Runnable? = null

        adapter = ReceiptPreviewAdapter(
            onDelete = { index ->
                val list = adapter.currentList.toMutableList()
                if (index in list.indices) {
                    list.removeAt(index)
                    // 清理对应的 aisle 映射
                    itemAisleMap.remove(index)
                    itemAisleLocationName.remove(index)
                    // 重新映射索引
                    val newAisleMap = mutableMapOf<Int, Aisle?>()
                    val newLocMap = mutableMapOf<Int, String>()
                    itemAisleMap.forEach { (oldIdx, aisle) ->
                        when {
                            oldIdx < index -> {
                                newAisleMap[oldIdx] = aisle
                                itemAisleLocationName[oldIdx]?.let { newLocMap[oldIdx] = it }
                            }
                            oldIdx > index -> {
                                newAisleMap[oldIdx - 1] = aisle
                                itemAisleLocationName[oldIdx]?.let { newLocMap[oldIdx - 1] = it }
                            }
                        }
                    }
                    itemAisleMap.clear()
                    itemAisleMap.putAll(newAisleMap)
                    itemAisleLocationName.clear()
                    itemAisleLocationName.putAll(newLocMap)

                    adapter.submitList(list)
                    updateSummary()
                }
            },
            onUpdate = { index, item ->
                // 取消之前的延迟更新
                pendingRunnable?.let { view.removeCallbacks(it) }

                // 创建新的延迟更新任务
                pendingRunnable = Runnable {
                    val list = adapter.currentList.toMutableList()
                    if (index in list.indices) {
                        // 只有在列表中的项仍然存在时才更新
                        list[index] = item
                        adapter.submitList(list)
                        updateSummary()
                    }
                    pendingRunnable = null
                }

                // 延迟 300ms 更新，如果用户继续输入会取消之前的更新
                view.postDelayed(pendingRunnable!!, 300)
            },
            onSelectAisle = { index -> showAisleSelectionDialog(index) },
            getAisleName = { index ->
                val aisle = itemAisleMap[index]
                val loc = itemAisleLocationName[index]
                if (aisle != null && loc != null) "$loc · ${aisle.name}" else aisle?.name
            }
        )

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        if (initialItems.isNotEmpty()) {
            adapter.submitList(initialItems)
            updateSummary()
        } else {
            android.util.Log.w("ReceiptPreviewFragment", "No items to display")
        }

        btnCancel.setOnClickListener {
            // 返回上一页
            findNavController().popBackStack()
        }

        btnImport.setOnClickListener {
            importItems()
        }

        return view
    }

    private fun updateSummary() {
        val items = adapter.currentList
        val total = items.fold(BigDecimal.ZERO) { acc, it ->
            acc + it.unitPrice.multiply(java.math.BigDecimal.valueOf(it.quantity))
        }
        tvSummary?.text = "Items: ${items.size}   Total: ${total.toPlainString()}"
    }

    private fun showAisleSelectionDialog(itemIndex: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            // 获取所有 locations 的所有 aisles
            val allAisles = withContext(Dispatchers.IO) {
                val locations = locationRepository.getAll()
                locations.flatMap { location ->
                    aisleRepository.getForLocation(location.id)
                }
            }

            // 获取所有 locations 用于显示 aisle 的 location 信息
            val locations = withContext(Dispatchers.IO) {
                locationRepository.getAll()
            }
            val locationMap = locations.associateBy { it.id }

            // 创建显示名称（包含 location 信息）
            val aisleDisplayNames = allAisles.map { aisle ->
                val location = locationMap[aisle.locationId]
                val locationName = location?.name ?: "Unknown"
                "${aisle.name} (${locationName})"
            }.toTypedArray()

            val currentAisle = itemAisleMap[itemIndex]
            val selectedIndex = currentAisle?.let { allAisles.indexOf(it) } ?: -1

        AlertDialog.Builder(requireContext())
            .setTitle("Select Aisle")
                .setSingleChoiceItems(aisleDisplayNames, selectedIndex) { dialog, which ->
                    val selectedAisle = allAisles[which]
                    itemAisleMap[itemIndex] = selectedAisle
                    val locName = locationMap[selectedAisle.locationId]?.name ?: "Unknown"
                    itemAisleLocationName[itemIndex] = locName
                    adapter.notifyItemChanged(itemIndex)
                    dialog.dismiss()
                }
            .setNeutralButton("Create New Aisle") { _, _ ->
                showCreateAisleDialog(itemIndex)
            }
            .setPositiveButton("Create New Location") { _, _ ->
                showCreateLocationThenAisleDialog(itemIndex)
            }
            .setNegativeButton("Cancel", null)
            .show()
        }
    }

    private fun showCreateLocationThenAisleDialog(itemIndex: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            val locationNameInput = android.widget.EditText(requireContext())
            locationNameInput.hint = "Location name"

            AlertDialog.Builder(requireContext())
                .setTitle("Create New Location")
                .setView(locationNameInput)
                .setPositiveButton("Create") { _, _ ->
                    val locationName = locationNameInput.text.toString().trim()
                    if (locationName.isNotEmpty()) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            try {
                                val newLocation = withContext(Dispatchers.IO) {
                                    val newLocationId = addLocationUseCase(
                                        Location(
                                            id = 0,
                                            type = LocationType.SHOP,
                                            defaultFilter = FilterType.NEEDED,
                                            name = locationName,
                                            pinned = false,
                                            aisles = emptyList(),
                                            showDefaultAisle = true
                                        )
                                    )
                                    getLocationUseCase(newLocationId)
                                }

                                val aisleNameInput = android.widget.EditText(requireContext())
                                aisleNameInput.hint = "Aisle name"

                                AlertDialog.Builder(requireContext())
                                    .setTitle("Create New Aisle in ${newLocation?.name ?: "Unknown"}")
                                    .setView(aisleNameInput)
                                    .setPositiveButton("Create") { _, _ ->
                                        val aisleName = aisleNameInput.text.toString().trim()
                                        if (aisleName.isNotEmpty() && newLocation != null) {
                                            viewLifecycleOwner.lifecycleScope.launch {
                                                try {
                                                    val newAisle = withContext(Dispatchers.IO) {
                                                        val existingAisles = aisleRepository.getForLocation(newLocation.id)
                                                        val maxRank = existingAisles.maxOfOrNull { it.rank } ?: 0
                                                        val newRank = maxRank + 100
                                                        val aisle = Aisle(
                                                            name = aisleName,
                                                            products = emptyList(),
                                                            locationId = newLocation.id,
                                                            rank = newRank,
                                                            id = 0,
                                                            isDefault = false,
                                                            expanded = true
                                                        )
                                                        val aisleId = addAisleUseCase(aisle)
                                                        aisleRepository.get(aisleId)
                                                    }
                                                    newAisle?.let {
                                                        itemAisleMap[itemIndex] = it
                                                        itemAisleLocationName[itemIndex] = newLocation?.name ?: "Unknown"
                                                        adapter.notifyItemChanged(itemIndex)
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(requireContext(), "Error creating aisle: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            } catch (e: Exception) {
                                Toast.makeText(requireContext(), "Error creating location: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showCreateAisleDialog(itemIndex: Int) {
        // 先让用户选择 location
        viewLifecycleOwner.lifecycleScope.launch {
            val locations = withContext(Dispatchers.IO) {
                locationRepository.getAll()
            }

            val locationNames = locations.map { it.name }.toTypedArray()

            AlertDialog.Builder(requireContext())
                .setTitle("Select Location for New Aisle")
                .setSingleChoiceItems(locationNames, -1) { dialog, which ->
                    val selectedLocation = locations[which]
                    dialog.dismiss()

                    // 然后创建 aisle
                    val input = android.widget.EditText(requireContext())
                    input.hint = "Aisle name"

                    AlertDialog.Builder(requireContext())
                        .setTitle("Create New Aisle in ${selectedLocation.name}")
                        .setView(input)
                        .setPositiveButton("Create") { _, _ ->
                            val aisleName = input.text.toString().trim()
                            if (aisleName.isNotEmpty()) {
                                viewLifecycleOwner.lifecycleScope.launch {
                                    try {
                                        val newAisle = withContext(Dispatchers.IO) {
                                            // 获取该 location 下现有的最大 rank，以便新 aisle 排在最前面
                                            val existingAisles = aisleRepository.getForLocation(selectedLocation.id)
                                            val maxRank = existingAisles.maxOfOrNull { it.rank } ?: 0
                                            val newRank = maxRank + 100 // 加 100 以确保新 aisle 在最前面

                                            val aisle = Aisle(
                                                name = aisleName,
                                                products = emptyList(),
                                                locationId = selectedLocation.id,
                                                rank = newRank,
                                                id = 0,
                                                isDefault = false,
                                                expanded = true
                                            )
                                            val aisleId = addAisleUseCase(aisle)
                                            aisleRepository.get(aisleId)
                                        }
                                        newAisle?.let {
                                            itemAisleMap[itemIndex] = it
                                            itemAisleLocationName[itemIndex] = selectedLocation.name
                                            adapter.notifyItemChanged(itemIndex)
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(requireContext(), "Error creating aisle: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun importItems() {
        val items = adapter.currentList
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), "No items to import", Toast.LENGTH_SHORT).show()
            return
        }

        // 检查是否所有项都有 aisle
        val missingSelections = items.indices.filter { index ->
            itemAisleMap[index] == null
        }

        if (missingSelections.isNotEmpty()) {
            Toast.makeText(requireContext(), "Please select aisle for all items", Toast.LENGTH_LONG).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                var successCount = 0
                var errorCount = 0

                withContext(Dispatchers.IO) {
                    items.forEachIndexed { index, receiptItem ->
                        try {
                            val aisle = itemAisleMap[index]!!

                            // 从 aisle 获取 location 信息
                            val location = getLocationUseCase(aisle.locationId)
                            val shopName = location?.name ?: "Unknown"

                            // 检查产品是否已存在
                            var product = productRepository.getByName(receiptItem.name.trim())
                            var productId: Int

                            if (product == null) {
                                // 产品不存在，创建新产品
                                val newProduct = Product(
                                    id = 0,
                                    name = receiptItem.name,
                                    inStock = true,
                                    qtyNeeded = 0,
                                    price = receiptItem.unitPrice.toDouble()
                                )
                                // 使用 AddProductUseCase 添加产品（会自动添加到默认 aisles 和指定 aisle）
                                productId = addProductUseCase(newProduct, aisle)
                                product = productRepository.get(productId)
                            } else {
                                // 产品已存在，使用现有产品
                                productId = product.id

                                // 检查产品是否已在指定的 aisle 中
                                val productAisles = aisleProductRepository.getProductAisles(productId)
                                val isInAisle = productAisles.any { it.aisleId == aisle.id }

                                if (!isInAisle) {
                                    // 产品不在指定的 aisle 中，添加到该 aisle
                                    val maxRank = aisleProductRepository.getAisleMaxRank(aisle.id)
                                    aisleProductRepository.add(
                                        com.aisleron.domain.aisleproduct.AisleProduct(
                                            aisleId = aisle.id,
                                            product = product,
                                            rank = maxRank + 1,
                                            id = 0
                                        )
                                    )
                                }
                            }

                            // 创建 Record 记录购买历史
                            product?.let {
                                recordRepository.add(Record(
                                    id = 0,
                                    productId = it.id,
                                    date = Date(),
                                    stock = true,
                                    price = receiptItem.unitPrice.toDouble(),
                                    quantity = receiptItem.quantity,
                                    shop = shopName
                                ))
                                successCount++
                            }
                        } catch (e: com.aisleron.domain.base.AisleronException) {
                            android.util.Log.e("ReceiptPreviewFragment", "Error importing item ${receiptItem.name}: ${e.message}", e)
                            errorCount++
                        } catch (e: Exception) {
                            android.util.Log.e("ReceiptPreviewFragment", "Error importing item ${receiptItem.name}", e)
                            errorCount++
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (errorCount == 0) {
                        Toast.makeText(requireContext(), "Successfully imported $successCount items", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    } else {
                        Toast.makeText(requireContext(), "Imported $successCount items, $errorCount failed", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error importing items: ${e.message}", Toast.LENGTH_LONG).show()
                    android.util.Log.e("ReceiptPreviewFragment", "Error importing items", e)
                }
            }
        }
    }
}
