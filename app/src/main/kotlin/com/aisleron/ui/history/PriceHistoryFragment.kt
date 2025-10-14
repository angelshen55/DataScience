package com.aisleron.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aisleron.R
import com.aisleron.domain.product.ProductRepository
import com.aisleron.domain.record.RecordRepository
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Price History Fragment
 */
class PriceHistoryFragment : Fragment() {
    private val productRepository: ProductRepository by inject()
    private val recordRepository: RecordRepository by inject()
    private lateinit var recycler: RecyclerView
    private val adapter = ProductPriceHistoryAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // layout files
        val v = inflater.inflate(R.layout.fragment_price_history, container, false)
        recycler = v.findViewById(R.id.recycler_price_history)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            loadData()
        }
    }
    private suspend fun loadData() {
        val products = productRepository.getAllIncludingDeleted()
        val allRecords = recordRepository.getAll()
        val recordsMap = allRecords.groupBy { it.productId }
        adapter.submit(products, recordsMap)
    }
}