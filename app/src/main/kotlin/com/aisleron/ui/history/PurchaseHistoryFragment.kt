package com.aisleron.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import android.app.DatePickerDialog
import java.util.Calendar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aisleron.R
import com.aisleron.domain.record.Record
import com.aisleron.domain.record.RecordRepository
import com.aisleron.domain.product.ProductRepository
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Purchase History Pager Fragment
 * show all purchase record
 */
class PurchaseHistoryFragment : Fragment() {
    
    private val recordRepository: RecordRepository by inject()
    private val productRepository: ProductRepository by inject()
    private val vm by lazy { PurchaseHistoryViewModel(recordRepository, productRepository) }
    
    private lateinit var recycler: RecyclerView
    private val adapter = SimpleRecordAdapter()
    private var menuProvider: MenuProvider? = null

    override fun onCreateView(
        inflater: LayoutInflater, 
        container: ViewGroup?, 
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_purchase_history, container, false)
        recycler = v.findViewById(R.id.recycler_purchase_history)
        
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
        
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewLifecycleOwner.lifecycleScope.launch {
            vm.filteredRecords.collect { list ->
                adapter.submit(list, productRepository)
            }
        }

        val menuHost: MenuHost = requireActivity()
        val provider = object : MenuProvider {
            override fun onCreateMenu(menu: android.view.Menu, menuInflater: android.view.MenuInflater) {
                menuInflater.inflate(R.menu.menu_purchase_history, menu)
            }

            override fun onMenuItemSelected(menuItem: android.view.MenuItem): Boolean {
                if (menuItem.itemId == R.id.action_filter) {
                    showFilterDialog()
                    return true
                }
                return false
            }
        }
        menuProvider = provider
        menuHost.addMenuProvider(provider, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val menuHost: MenuHost = requireActivity()
        menuProvider?.let { menuHost.removeMenuProvider(it) }
        menuProvider = null
    }

    private fun showFilterDialog() {
        val ctx = requireContext()
        val view = layoutInflater.inflate(R.layout.dialog_history_filters, null)
        val edtName = view.findViewById<android.widget.EditText>(R.id.edt_name)
        val edtShop = view.findViewById<android.widget.EditText>(R.id.edt_shop)
        val btnStart = view.findViewById<android.widget.Button>(R.id.btn_start)
        val btnEnd = view.findViewById<android.widget.Button>(R.id.btn_end)

        var startMs: Long? = vm.startMillisFilter.value
        var endMs: Long? = vm.endMillisFilter.value

        fun pickDate(callback: (Long?) -> Unit) {
            val cal = Calendar.getInstance()
            DatePickerDialog(ctx, { _, y, m, d ->
                cal.set(Calendar.YEAR, y)
                cal.set(Calendar.MONTH, m)
                cal.set(Calendar.DAY_OF_MONTH, d)
                // normalize to day start
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                callback(cal.timeInMillis)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnStart.setOnClickListener { pickDate { ms -> startMs = ms } }
        btnEnd.setOnClickListener { pickDate { ms -> endMs = ms?.let { it + 86_399_999 } } }

        edtName.setText(vm.nameFilter.value)
        edtShop.setText(vm.shopFilter.value ?: "")

        android.app.AlertDialog.Builder(ctx)
            .setTitle("Filter Records")
            .setView(view)
            .setPositiveButton("Apply") { _, _ ->
                val name = edtName.text?.toString()?.takeIf { it.isNotBlank() }
                val shop = edtShop.text?.toString()?.takeIf { it.isNotBlank() }
                vm.setFilters(name, shop, startMs, endMs)
            }
            .setNegativeButton("Clear") { _, _ ->
                vm.setFilters(null, null, null, null)
            }
            .show()
    }
}