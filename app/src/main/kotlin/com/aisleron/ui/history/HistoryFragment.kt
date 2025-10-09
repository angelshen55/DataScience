package com.aisleron.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.aisleron.R
import com.google.android.material.tabs.TabLayoutMediator

/**
 * History Page Main Fragment
 */
class HistoryFragment : Fragment() {
    
    // UI
    private lateinit var viewPager: androidx.viewpager2.widget.ViewPager2
    private lateinit var tabLayout: com.google.android.material.tabs.TabLayout

    override fun onCreateView(
        inflater: LayoutInflater, 
        container: ViewGroup?, 
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_history_with_tabs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewPager = view.findViewById(R.id.view_pager)
        tabLayout = view.findViewById(R.id.tab_layout)
        
        viewPager.adapter = HistoryPagerAdapter(requireActivity())
        
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Purchase History"  
                1 -> "Price History"     
                else -> ""
            }
        }.attach()
    }

    override fun onResume() {
        super.onResume()
        tabLayout.setTabTextColors(
            android.graphics.Color.WHITE,
            android.graphics.Color.WHITE
        )
    }
}