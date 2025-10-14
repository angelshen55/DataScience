package com.aisleron.ui.history

import com.aisleron.domain.product.Product
import com.aisleron.domain.record.Record

/**
 * expandable data
 */
data class ExpandableProductItem(
    val product: Product,
    val records: List<Record> = emptyList(),
    var isExpanded: Boolean = false
)