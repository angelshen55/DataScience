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
import com.aisleron.domain.record.Record
import com.aisleron.domain.record.RecordRepository
import com.aisleron.domain.product.ProductRepository
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class HistoryFragment : Fragment() {

    private val recordRepository: RecordRepository by inject()
    private val productRepository: ProductRepository by inject()
    private lateinit var recycler: RecyclerView
    private val adapter = SimpleRecordAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_history, container, false)
        recycler = v.findViewById(R.id.recycler_history)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            val data: List<Record> = recordRepository.getAll()
            adapter.submit(data, productRepository)
        }
    }
}