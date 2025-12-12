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

package com.aisleron.ui.settings

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import java.util.Calendar

class ShoppingListPreferencesImpl : ShoppingListPreferences {
    override fun isStatusChangeSnackBarHidden(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            PREF_HIDE_STATUS_CHANGE_SNACK_BAR, false
        )

    override fun showEmptyAisles(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            PREF_SHOW_EMPTY_AISLES, false
        )

    override fun setShowEmptyAisles(context: Context, value: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit {
                putBoolean(PREF_SHOW_EMPTY_AISLES, value)
            }
    }

    override fun trackingMode(context: Context): ShoppingListPreferences.TrackingMode {
        val method = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_TRACKING_MODE, CHECKBOX)

        return when (method) {
            QUANTITY -> ShoppingListPreferences.TrackingMode.QUANTITY
            CHECKBOX_QUANTITY -> ShoppingListPreferences.TrackingMode.CHECKBOX_QUANTITY
            NONE -> ShoppingListPreferences.TrackingMode.NONE
            else -> ShoppingListPreferences.TrackingMode.CHECKBOX
        }
    }

    override fun keepScreenOn(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            PREF_KEEP_SCREEN_ON, false
        )
        
    // New methods for recommendation tracking
    override fun getLastRecommendationDisplayDate(context: Context): Long =
        PreferenceManager.getDefaultSharedPreferences(context).getLong(
            PREF_LAST_RECOMMENDATION_DISPLAY_DATE, 0L
        )
        
    override fun setLastRecommendationDisplayDate(context: Context, timestamp: Long) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit {
                putLong(PREF_LAST_RECOMMENDATION_DISPLAY_DATE, timestamp)
            }
    }
    
    override fun shouldShowRecommendationsToday(context: Context): Boolean {
        val lastDisplayDate = getLastRecommendationDisplayDate(context)
        if (lastDisplayDate == 0L) return true // Never displayed before
        
        val lastDisplayCalendar = Calendar.getInstance().apply { timeInMillis = lastDisplayDate }
        val todayCalendar = Calendar.getInstance()
        
        // Compare year, month, and day
        return lastDisplayCalendar.get(Calendar.YEAR) != todayCalendar.get(Calendar.YEAR) ||
               lastDisplayCalendar.get(Calendar.DAY_OF_YEAR) != todayCalendar.get(Calendar.DAY_OF_YEAR)
    }
    
    override fun getTodayRecommendationsDate(context: Context): Long =
        PreferenceManager.getDefaultSharedPreferences(context).getLong(
            PREF_TODAY_RECOMMENDATIONS_DATE, 0L
        )
    
    override fun setTodayRecommendationsDate(context: Context, timestamp: Long) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit {
                putLong(PREF_TODAY_RECOMMENDATIONS_DATE, timestamp)
            }
    }
    
    override fun isTodayRecommendationsDate(context: Context): Boolean {
        val storedDate = getTodayRecommendationsDate(context)
        if (storedDate == 0L) return false
        
        val storedCalendar = Calendar.getInstance().apply { timeInMillis = storedDate }
        val todayCalendar = Calendar.getInstance()
        
        // Compare year, month, and day
        return storedCalendar.get(Calendar.YEAR) == todayCalendar.get(Calendar.YEAR) &&
               storedCalendar.get(Calendar.DAY_OF_YEAR) == todayCalendar.get(Calendar.DAY_OF_YEAR)
    }
    
    // New method to check if recommendations should be shown (regardless of previous display)
    fun shouldShowRecommendations(context: Context): Boolean {
        return true // Always show recommendations, but track user interaction separately
    }

    companion object {
        const val PREF_HIDE_STATUS_CHANGE_SNACK_BAR = "hide_status_change_snack_bar"
        const val PREF_SHOW_EMPTY_AISLES = "show_empty_aisles"
        const val PREF_TRACKING_MODE = "tracking_mode"
        const val PREF_KEEP_SCREEN_ON = "keep_screen_on"
        
        // New preference key for recommendation tracking
        const val PREF_LAST_RECOMMENDATION_DISPLAY_DATE = "last_recommendation_display_date"
        const val PREF_TODAY_RECOMMENDATIONS_DATE = "today_recommendations_date"

        const val CHECKBOX = "checkbox"
        const val QUANTITY = "quantity"
        const val CHECKBOX_QUANTITY = "checkbox_quantity"
        const val NONE = "none"
    }
}