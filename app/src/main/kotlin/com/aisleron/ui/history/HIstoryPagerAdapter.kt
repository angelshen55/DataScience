package com.aisleron.ui.history

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * History pager adapter
 * manage two tab pages: Purchase History and Price History
 */
class HistoryPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    
    override fun getItemCount(): Int = 2
    
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> PurchaseHistoryFragment()  
            1 -> PriceHistoryFragment()     
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}