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
import java.util.Calendar

class ShoppingListPreferencesTestImpl : ShoppingListPreferences {

    private var _hideStatusChangeSnackBar: Boolean = false
    private var _showEmptyAisles: Boolean = false
    private var _keepScreenOn: Boolean = false
    private var _trackingMode: ShoppingListPreferences.TrackingMode =
        ShoppingListPreferences.TrackingMode.CHECKBOX
    private var _lastRecommendationDisplayDate: Long = 0L
    private var _todayRecommendationsDate: Long = 0L

    override fun isStatusChangeSnackBarHidden(context: Context): Boolean = _hideStatusChangeSnackBar
    override fun showEmptyAisles(context: Context): Boolean = _showEmptyAisles
    override fun keepScreenOn(context: Context): Boolean = _keepScreenOn
    override fun trackingMode(context: Context): ShoppingListPreferences.TrackingMode =
        _trackingMode

    override fun setShowEmptyAisles(context: Context, value: Boolean) {
        _showEmptyAisles = value
    }

    override fun getLastRecommendationDisplayDate(context: Context): Long =
        _lastRecommendationDisplayDate

    override fun setLastRecommendationDisplayDate(context: Context, timestamp: Long) {
        _lastRecommendationDisplayDate = timestamp
    }

    override fun shouldShowRecommendationsToday(context: Context): Boolean {
        val lastDisplayDate = getLastRecommendationDisplayDate(context)
        if (lastDisplayDate == 0L) return true

        val lastCal = Calendar.getInstance().apply { timeInMillis = lastDisplayDate }
        val todayCal = Calendar.getInstance()
        return lastCal.get(Calendar.YEAR) != todayCal.get(Calendar.YEAR) ||
                lastCal.get(Calendar.DAY_OF_YEAR) != todayCal.get(Calendar.DAY_OF_YEAR)
    }

    override fun getTodayRecommendationsDate(context: Context): Long = _todayRecommendationsDate

    override fun setTodayRecommendationsDate(context: Context, timestamp: Long) {
        _todayRecommendationsDate = timestamp
    }

    override fun isTodayRecommendationsDate(context: Context): Boolean {
        val todayTimestamp = getTodayRecommendationsDate(context)
        if (todayTimestamp == 0L) return false

        val todayStored = Calendar.getInstance().apply { timeInMillis = todayTimestamp }
        val todayNow = Calendar.getInstance()
        return todayStored.get(Calendar.YEAR) == todayNow.get(Calendar.YEAR) &&
                todayStored.get(Calendar.DAY_OF_YEAR) == todayNow.get(Calendar.DAY_OF_YEAR)
    }

    fun setHideStatusChangeSnackBar(hideSnackBar: Boolean) {
        _hideStatusChangeSnackBar = hideSnackBar
    }

    fun setShowEmptyAisles(showEmptyAisles: Boolean) {
        _showEmptyAisles = showEmptyAisles
    }

    fun setTrackingMode(trackingMode: ShoppingListPreferences.TrackingMode) {
        _trackingMode = trackingMode
    }
}
