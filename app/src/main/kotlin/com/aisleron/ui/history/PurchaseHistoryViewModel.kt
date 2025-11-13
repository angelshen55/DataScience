package com.aisleron.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aisleron.domain.product.ProductRepository
import com.aisleron.domain.record.Record
import com.aisleron.domain.record.RecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PurchaseHistoryViewModel(
    private val recordRepository: RecordRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val allRecords = MutableStateFlow<List<Record>>(emptyList())

    val nameFilter = MutableStateFlow("")
    val shopFilter = MutableStateFlow<String?>(null) // null => all
    val startMillisFilter = MutableStateFlow<Long?>(null)
    val endMillisFilter = MutableStateFlow<Long?>(null)

    val filteredRecords: StateFlow<List<Record>> = combine(
        allRecords, nameFilter, shopFilter, startMillisFilter, endMillisFilter
    ) { records, name, shop, startMs, endMs ->
        if (records.isEmpty()) return@combine emptyList()

        val nameLower = name.trim().lowercase()

        records.filter { r ->
            val passShop = shop == null || r.shop == shop
            val passStart = startMs == null || r.date.time >= startMs
            val passEnd = endMs == null || r.date.time <= endMs
            val passName = if (nameLower.isEmpty()) true else {
                val product = runCatching { productRepository.get(r.productId) }.getOrNull()
                val n = product?.name?.lowercase() ?: ""
                n.contains(nameLower)
            }
            passShop && passStart && passEnd && passName
        }.sortedByDescending { it.date }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            allRecords.value = recordRepository.getAll()
        }
    }

    fun setFilters(name: String?, shop: String?, startMs: Long?, endMs: Long?) {
        nameFilter.value = name ?: ""
        shopFilter.value = shop
        startMillisFilter.value = startMs
        endMillisFilter.value = endMs
    }
}


