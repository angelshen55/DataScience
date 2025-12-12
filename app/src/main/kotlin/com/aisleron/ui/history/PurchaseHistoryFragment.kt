package com.aisleron.ui.history

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aisleron.R
import com.aisleron.domain.record.Record
import com.aisleron.domain.record.RecordRepository
import com.aisleron.domain.product.ProductRepository
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.Calendar
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem

/**
 * Purchase History Pager Fragment
 * show all purchase record
 */
class PurchaseHistoryFragment : Fragment(), MenuProvider {
    
    private val recordRepository: RecordRepository by inject()
    private val productRepository: ProductRepository by inject()
    private val vm by lazy { PurchaseHistoryViewModel(recordRepository, productRepository) }
    
    private lateinit var recycler: RecyclerView
    private val adapter = SimpleRecordAdapter()

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
        
        // Add menu provider using viewLifecycleOwner so it's automatically removed when view is destroyed
        // This is the same pattern used by ShoppingListFragment
        val menuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }
    
    // MenuProvider methods
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        // Only create menu if this fragment is actually visible and we're on the history page
        if (!shouldShowMenu()) {
            return
        }
        
        menuInflater.inflate(R.menu.menu_purchase_history, menu)
    }
    
    private fun shouldShowMenu(): Boolean {
        // Check if fragment is attached and visible
        if (!isAdded || view == null) {
            return false
        }
        
        // Check if fragment is visible (for ViewPager2)
        if (!isVisible) {
            return false
        }
        
        // Check if parent fragment (HistoryFragment) is visible
        val parent = parentFragment
        if (parent != null && (!parent.isVisible || !parent.isResumed)) {
            return false
        }
        
        // Check if we're currently on the history navigation destination
        return try {
            val navController = findNavController()
            navController.currentDestination?.id == R.id.nav_history
        } catch (e: Exception) {
            false
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_filter -> {
                showFilterDialog()
                true
            }
            else -> false
        }
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