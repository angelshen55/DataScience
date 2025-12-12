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

package com.aisleron.ui.shoplist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aisleron.domain.base.AisleronException
import com.aisleron.domain.location.Location
import com.aisleron.domain.location.usecase.GetLocationUseCase
import com.aisleron.domain.location.usecase.GetPinnedShopsUseCase
import com.aisleron.domain.location.usecase.GetShopsUseCase
import com.aisleron.domain.location.usecase.RemoveLocationUseCase
import com.aisleron.domain.shoppinglist.usecase.GetShoppingListUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ShopListViewModel(
    private val getShopsUseCase: GetShopsUseCase,
    private val getPinnedShopsUseCase: GetPinnedShopsUseCase,
    private val removeLocationUseCase: RemoveLocationUseCase,
    private val getLocationUseCase: GetLocationUseCase,
    private val getShoppingListUseCase: GetShoppingListUseCase,
    coroutineScopeProvider: CoroutineScope? = null
) : ViewModel() {
    private val coroutineScope = coroutineScopeProvider ?: this.viewModelScope
    private val _shopListUiState = MutableStateFlow<ShopListUiState>(ShopListUiState.Empty)
    val shopListUiState: StateFlow<ShopListUiState> = _shopListUiState

    fun hydratePinnedShops() {
        hydrateShops(getPinnedShopsUseCase())
    }

    fun hydrateAllShops() {
        hydrateShops(getShopsUseCase())
    }

    private fun hydrateShops(shopsFlow: Flow<List<Location>>) {
        coroutineScope.launch {
            _shopListUiState.value = ShopListUiState.Loading
            shopsFlow.collect { locations ->
                // For each location fetch the full location to count needed items concurrently
                val deferredCounts = locations.map { loc ->
                    coroutineScope.async {
                        try {
                            val full = getShoppingListUseCase(loc.id).first()
                            full?.aisles?.sumOf { aisle ->
                                aisle.products.count { ap -> !ap.product.inStock }
                            } ?: 0
                        } catch (e: Exception) {
                            0
                        }
                    }
                }

                val counts = deferredCounts.awaitAll()

                val items = locations.mapIndexed { idx, l ->
                    ShopListItemViewModel(
                        id = l.id,
                        defaultFilter = l.defaultFilter,
                        name = l.name,
                        neededCount = counts.getOrNull(idx) ?: 0
                    )
                }

                // pick recommended shop (max neededCount)
                val maxItem = items.maxByOrNull { it.neededCount }
                val recommendedName = if (maxItem != null && maxItem.neededCount > 0) maxItem.name else null
                val recommendedNeededCount = if (maxItem != null && maxItem.neededCount > 0) maxItem.neededCount else 0

                _shopListUiState.value = ShopListUiState.Updated(
                    shops = items,
                    recommendedShopName = recommendedName,
                    recommendedNeededCount = recommendedNeededCount
                )
            }
        }
    }

    fun removeItem(item: ShopListItemViewModel) {
        coroutineScope.launch {
            try {
                val location = getLocationUseCase(item.id)
                location?.let { removeLocationUseCase(location) }
            } catch (e: Exception) {
                _shopListUiState.value =
                    ShopListUiState.Error(
                        AisleronException.ExceptionCode.GENERIC_EXCEPTION, e.message
                    )
            }
        }
    }

    sealed class ShopListUiState {
        data object Empty : ShopListUiState()
        data object Loading : ShopListUiState()
        data class Error(
            val errorCode: AisleronException.ExceptionCode, val errorMessage: String?
        ) : ShopListUiState()

        data class Updated(
            val shops: List<ShopListItemViewModel>,
            val recommendedShopName: String? = null,
            val recommendedNeededCount: Int = 0
        ) : ShopListUiState()
    }
}