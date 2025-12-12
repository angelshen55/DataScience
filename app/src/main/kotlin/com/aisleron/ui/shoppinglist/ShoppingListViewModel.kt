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

package com.aisleron.ui.shoppinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aisleron.domain.FilterType
import com.aisleron.domain.aisle.Aisle
import com.aisleron.domain.aisle.usecase.GetAisleUseCase
import com.aisleron.domain.aisle.usecase.RemoveAisleUseCase
import com.aisleron.domain.aisle.usecase.UpdateAisleExpandedUseCase
import com.aisleron.domain.aisle.usecase.UpdateAisleRankUseCase
import com.aisleron.domain.aisleproduct.AisleProduct
import com.aisleron.domain.aisleproduct.AisleProductRepository
import com.aisleron.domain.aisleproduct.usecase.AddAisleProductsUseCase
import com.aisleron.domain.aisleproduct.usecase.GetAisleMaxRankUseCase
import com.aisleron.domain.aisleproduct.usecase.UpdateAisleProductRankUseCase
import com.aisleron.domain.base.AisleronException
import com.aisleron.domain.location.Location
import com.aisleron.domain.location.LocationType
import com.aisleron.domain.location.usecase.SortLocationByNameUseCase
import com.aisleron.domain.loyaltycard.LoyaltyCard
import com.aisleron.domain.loyaltycard.usecase.GetLoyaltyCardForLocationUseCase
import com.aisleron.domain.product.Product
import com.aisleron.domain.product.ProductRecommendation
import com.aisleron.domain.product.usecase.GetProductRecommendationsUseCase
import com.aisleron.domain.product.usecase.RemoveProductUseCase
import com.aisleron.domain.product.usecase.UpdateProductQtyNeededUseCase
import com.aisleron.domain.product.usecase.UpdateProductPriceUseCase
import com.aisleron.domain.product.usecase.UpdateProductStatusUseCase
import com.aisleron.domain.aisle.usecase.GetDefaultAisleForLocationUseCase
import com.aisleron.domain.shoppinglist.usecase.GetShoppingListUseCase
import com.aisleron.ui.settings.ShoppingListPreferencesImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ShoppingListViewModel(
    private val getShoppingListUseCase: GetShoppingListUseCase,
    private val updateProductStatusUseCase: UpdateProductStatusUseCase,
    private val updateAisleProductRankUseCase: UpdateAisleProductRankUseCase,
    private val updateAisleRankUseCase: UpdateAisleRankUseCase,
    private val removeAisleUseCase: RemoveAisleUseCase,
    private val removeProductUseCase: RemoveProductUseCase,
    private val getAisleUseCase: GetAisleUseCase,
    private val updateAisleExpandedUseCase: UpdateAisleExpandedUseCase,
    private val sortLocationByNameUseCase: SortLocationByNameUseCase,
    private val getLoyaltyCardForLocationUseCase: GetLoyaltyCardForLocationUseCase,
    private val updateProductQtyNeededUseCase: UpdateProductQtyNeededUseCase,
    private val updateProductPriceUseCase: UpdateProductPriceUseCase,
    // New dependency for recommendations
    private val getProductRecommendationsUseCase: GetProductRecommendationsUseCase,
    private val getDefaultAisleForLocationUseCase: GetDefaultAisleForLocationUseCase,
    private val addAisleProductsUseCase: AddAisleProductsUseCase,
    private val getAisleMaxRankUseCase: GetAisleMaxRankUseCase,
    private val aisleProductRepository: AisleProductRepository,
    private val getProductUseCase: com.aisleron.domain.product.usecase.GetProductUseCase,
    private val debounceTime: Long = 300,
    coroutineScopeProvider: CoroutineScope? = null
) : ViewModel() {
    private val coroutineScope = coroutineScopeProvider ?: this.viewModelScope
    private var searchJob: Job? = null
    private var updateQtyJob: Job? = null
    private var updatePriceJob: Job? = null

    private var _location: Location? = null
    private var _showDefaultAisle: Boolean = true
    private val showDefaultAisle: Boolean get() = _showDefaultAisle
    val locationName: String get() = _location?.name ?: ""
    val locationType: LocationType get() = _location?.type ?: LocationType.HOME
    val locationId: Int get() = _location?.id ?: 0

    private var _defaultFilter: FilterType = FilterType.NEEDED
    val defaultFilter: FilterType get() = _defaultFilter

    private var _showEmptyAisles: Boolean = true
    private val showEmptyAisles: Boolean get() = _showEmptyAisles

    private var _loyaltyCard: LoyaltyCard? = null
    val loyaltyCard: LoyaltyCard? get() = _loyaltyCard

    private lateinit var shoppingListFilterParameters: ShoppingListFilterParameters

    private val _shoppingListUiState = MutableStateFlow<ShoppingListUiState>(
        ShoppingListUiState.Empty
    )

    val shoppingListUiState = _shoppingListUiState.asStateFlow()

    private fun isValidAisle(aisle: Aisle, parameters: ShoppingListFilterParameters): Boolean {
        return (parameters.showDefaultAisle || !aisle.isDefault) &&
                (aisle.products.count { isValidAisleProduct(it, parameters) } > 0 ||
                        parameters.showAllAisles)
    }

    private fun isValidAisleProduct(
        ap: AisleProduct, parameters: ShoppingListFilterParameters, aisleExpanded: Boolean = true
    ): Boolean {
        return ((ap.product.inStock && parameters.filterType == FilterType.IN_STOCK) ||
                (!ap.product.inStock && parameters.filterType == FilterType.NEEDED) ||
                (parameters.filterType == FilterType.ALL)
                ) &&
                (parameters.productNameQuery == "" || (ap.product.name.contains(
                    parameters.productNameQuery.trim(), true
                ))) &&
                ((parameters.showAllProducts || aisleExpanded))
    }

    private fun getShoppingList(
        location: Location?, parameters: ShoppingListFilterParameters
    ): List<ShoppingListItem> {
        val filteredList: MutableList<ShoppingListItem> = location?.let { l ->
            l.aisles.filter { a -> isValidAisle(a, parameters) }.flatMap { a ->
                listOf(
                    AisleShoppingListItemViewModel(
                        rank = a.rank,
                        id = a.id,
                        name = a.name,
                        isDefault = a.isDefault,
                        locationId = a.locationId,
                        expanded = a.expanded,
                        updateAisleRankUseCase = updateAisleRankUseCase,
                        getAisleUseCase = getAisleUseCase,
                        removeAisleUseCase = removeAisleUseCase,
                        childCount = a.products.count { ap -> isValidAisleProduct(ap, parameters) }
                    )) +
                        a.products.filter { ap -> isValidAisleProduct(ap, parameters, a.expanded) }
                            .map { ap ->
                                ProductShoppingListItemViewModel(
                                    aisleRank = a.rank,
                                    rank = ap.rank,
                                    id = ap.product.id,
                                    name = ap.product.name,
                                    inStock = ap.product.inStock,
                                    qtyNeeded = ap.product.qtyNeeded,
                                    price = ap.product.price,
                                    aisleId = ap.aisleId,
                                    aisleProductId = ap.id,
                                    removeProductUseCase = removeProductUseCase,
                                    updateAisleProductRankUseCase = updateAisleProductRankUseCase
                                )
                            }
            }
        }?.toMutableList() ?: mutableListOf()

        filteredList.sortWith(
            compareBy(
                { it.aisleRank },
                { it.aisleId },
                { it.itemType },
                { it.rank },
                { it.name })
        )

        if (filteredList.isEmpty()) {
            filteredList.add(EmptyShoppingListItem())
        }

        return filteredList.toList()
    }

    private fun getDefaultFilterParameters(): ShoppingListFilterParameters {
        return ShoppingListFilterParameters(
            filterType = _defaultFilter,
            showDefaultAisle = showDefaultAisle,
            showAllAisles = showEmptyAisles
        )
    }

    // Store context for preference access
    private var _context: android.content.Context? = null
    
    fun setContext(context: android.content.Context) {
        _context = context
    }
    
    val context: android.content.Context? get() = _context
    
    // Store today's recommendations and date
    private var todayRecommendations: List<ProductRecommendation>? = null
    private var todayRecommendationsDate: Long = 0L
    
    private fun isToday(date: Long): Boolean {
        if (date == 0L) return false
        val dateCalendar = java.util.Calendar.getInstance().apply { timeInMillis = date }
        val todayCalendar = java.util.Calendar.getInstance()
        return dateCalendar.get(java.util.Calendar.YEAR) == todayCalendar.get(java.util.Calendar.YEAR) &&
               dateCalendar.get(java.util.Calendar.DAY_OF_YEAR) == todayCalendar.get(java.util.Calendar.DAY_OF_YEAR)
    }
    
    fun loadRecommendations() {
        coroutineScope.launch {
            if (_context != null) {
                val preferences = com.aisleron.ui.settings.ShoppingListPreferencesImpl()
                val currentTime = System.currentTimeMillis()
                val storedDate = preferences.getTodayRecommendationsDate(_context!!)
                
                // Check if this is the first time showing recommendations today
                val isFirstTimeToday = !isToday(storedDate)
                
                // Check if we have recommendations for today in memory cache
                val recommendations = if (isToday(todayRecommendationsDate) && todayRecommendations != null) {
                    // Use cached recommendations for today
                    todayRecommendations!!
                } else {
                    // Get new recommendations and cache them
                    val newRecommendations = getProductRecommendationsUseCase()
                    todayRecommendations = newRecommendations
                    todayRecommendationsDate = currentTime
                    // Update the date to mark that we've shown recommendations today (only on first time)
                    if (isFirstTimeToday) {
                        preferences.setTodayRecommendationsDate(_context!!, currentTime)
                    }
                    newRecommendations
                }
                
                // Update the UI with recommendations
                val currentShoppingList = getShoppingList(_location, shoppingListFilterParameters)
                
                // Always show recommendations UI, even if empty
                _shoppingListUiState.value = ShoppingListUiState.UpdatedWithRecommendations(
                    currentShoppingList,
                    recommendations,
                    isFirstTimeToday = isFirstTimeToday
                )
            }
        }
    }
    
    fun hideRecommendations() {
        coroutineScope.launch {
            val currentShoppingList = getShoppingList(_location, shoppingListFilterParameters)
            _shoppingListUiState.value = ShoppingListUiState.Updated(currentShoppingList)
        }
    }

    fun hydrate(locationId: Int, filterType: FilterType, showEmptyAisles: Boolean = false) {
        _defaultFilter = filterType
        _showEmptyAisles = showEmptyAisles
        coroutineScope.launch {
            getShoppingListUseCase(locationId).collect { collectedLocation ->
                _shoppingListUiState.value = ShoppingListUiState.Loading
                _location = collectedLocation

                if (!::shoppingListFilterParameters.isInitialized)
                    shoppingListFilterParameters = getDefaultFilterParameters()

                shoppingListFilterParameters.showAllAisles = _showEmptyAisles
                _location?.let {
                    // If default aisle preference has changed, update the filter parameters accordingly
                    if (it.showDefaultAisle != _showDefaultAisle) {
                        _showDefaultAisle = it.showDefaultAisle
                        shoppingListFilterParameters.showDefaultAisle = _showDefaultAisle
                    }

                    _loyaltyCard = getLoyaltyCardForLocationUseCase(it.id)
                }

                // Check if we should show recommendations (only for ALL filter)
                if (filterType == FilterType.ALL && _context != null) {
                    val preferences = com.aisleron.ui.settings.ShoppingListPreferencesImpl()
                    // Show recommendations if we haven't shown them today
                    if (preferences.shouldShowRecommendationsToday(_context!!)) {
                        val recommendations = getProductRecommendationsUseCase()
                        
                        _shoppingListUiState.value = ShoppingListUiState.UpdatedWithRecommendations(
                            getShoppingList(_location, shoppingListFilterParameters),
                            recommendations
                        )
                    } else {
                        _shoppingListUiState.value = ShoppingListUiState.Updated(
                            getShoppingList(_location, shoppingListFilterParameters)
                        )
                    }
                } else {
                    _shoppingListUiState.value = ShoppingListUiState.Updated(
                        getShoppingList(_location, shoppingListFilterParameters)
                    )
                }
            }
        }
    }

    fun updateProductStatus(item: ProductShoppingListItem, inStock: Boolean) {
        coroutineScope.launch {
            updateProductStatusUseCase(item.id, inStock)
        }
    }
    
    /**
     * Add product to current location's default aisle if it's not already in any aisle of that location
     */
    suspend fun addProductToCurrentLocationIfNeeded(productId: Int, locationId: Int) {
        // Get product
        val product = getProductUseCase(productId) ?: return
        
        // Get all aisles where this product exists
        val productAisles = aisleProductRepository.getProductAisles(productId)
        
        // Check if product is already in any aisle of the current location
        val isInCurrentLocation = productAisles.any { ap ->
            val aisle = getAisleUseCase(ap.aisleId)
            aisle?.locationId == locationId
        }
        
        // If not in current location, add to default aisle
        if (!isInCurrentLocation) {
            val defaultAisle = getDefaultAisleForLocationUseCase(locationId)
            if (defaultAisle != null) {
                // Check if product is already in this aisle (shouldn't happen, but check anyway)
                val isInDefaultAisle = productAisles.any { it.aisleId == defaultAisle.id }
                if (!isInDefaultAisle) {
                    addAisleProductsUseCase(
                        listOf(
                            AisleProduct(
                                aisleId = defaultAisle.id,
                                product = product,
                                rank = getAisleMaxRankUseCase(defaultAisle) + 1,
                                id = 0
                            )
                        )
                    )
                }
            }
        }
    }

    fun updateProductNeededQuantity(item: ProductShoppingListItem, quantity: Int) {
        updateQtyJob?.cancel()
        updateQtyJob = coroutineScope.launch {
            delay(debounceTime)
            try {
                updateProductQtyNeededUseCase(item.id, quantity)
            } catch (e: Exception) {
                _shoppingListUiState.value =
                    ShoppingListUiState.Error(
                        AisleronException.ExceptionCode.GENERIC_EXCEPTION, e.message
                    )
            }
        }

    }

    fun updateProductPrice(item: ProductShoppingListItem, price: Double) {
        updatePriceJob?.cancel()
        updatePriceJob = coroutineScope.launch {
            delay(debounceTime)
            try {
                updateProductPriceUseCase(item.id, price)
            } catch (e: Exception) {
                _shoppingListUiState.value =
                    ShoppingListUiState.Error(
                        AisleronException.ExceptionCode.GENERIC_EXCEPTION, e.message
                    )
            }
        }
    }

    fun updateAisleExpanded(item: AisleShoppingListItem, expanded: Boolean) {
        coroutineScope.launch {
            updateAisleExpandedUseCase(item.id, expanded)
        }
    }

    fun updateItemRank(item: ShoppingListItem, precedingItem: ShoppingListItem?) {
        coroutineScope.launch {
            (item as ShoppingListItemViewModel).updateRank(precedingItem)
        }
    }

    fun submitProductSearch(productNameQuery: String) {
        searchJob?.cancel()

        searchJob = coroutineScope.launch {
            delay(debounceTime) //Add debounce to the search so it doesn't execute every keypress

            shoppingListFilterParameters.filterType = FilterType.ALL
            shoppingListFilterParameters.showDefaultAisle = true
            shoppingListFilterParameters.productNameQuery = productNameQuery
            shoppingListFilterParameters.showAllProducts = true
            shoppingListFilterParameters.showAllAisles = showEmptyAisles

            _shoppingListUiState.value = ShoppingListUiState.Loading
            val searchResults = getShoppingList(_location, shoppingListFilterParameters)
            _shoppingListUiState.value = ShoppingListUiState.Updated(searchResults)
        }
    }

    fun setShowEmptyAisles(showEmptyAisles: Boolean) {
        if (_showEmptyAisles != showEmptyAisles) {
            _showEmptyAisles = showEmptyAisles
            requestDefaultList()
        }
    }

    fun requestDefaultList() {
        shoppingListFilterParameters = getDefaultFilterParameters()
        coroutineScope.launch {
            _shoppingListUiState.value = ShoppingListUiState.Loading
            val searchResults = getShoppingList(_location, shoppingListFilterParameters)
            _shoppingListUiState.value = ShoppingListUiState.Updated(searchResults)
        }
    }

    fun movedItem(item: ShoppingListItem) {
        //TODO: Do some smarts to only expand the list if I'm dragging an aisle, dragging a product across an aisle, or reached the end of the list
        //TODO: When dragging an aisle, hide all products
        shoppingListFilterParameters.showAllAisles = true
        coroutineScope.launch {
            _shoppingListUiState.value = ShoppingListUiState.Loading
            val searchResults = getShoppingList(_location, shoppingListFilterParameters)
            _shoppingListUiState.value = ShoppingListUiState.Updated(searchResults)
        }
    }

    fun removeItem(item: ShoppingListItem) {
        coroutineScope.launch {
            try {
                (item as ShoppingListItemViewModel).remove()
            } catch (e: AisleronException) {
                _shoppingListUiState.value = ShoppingListUiState.Error(e.exceptionCode, e.message)
            } catch (e: Exception) {
                _shoppingListUiState.value =
                    ShoppingListUiState.Error(
                        AisleronException.ExceptionCode.GENERIC_EXCEPTION, e.message
                    )
            }
        }
    }

    fun sortListByName() {
        coroutineScope.launch {
            try {
                sortLocationByNameUseCase(locationId)
            } catch (e: Exception) {
                _shoppingListUiState.value =
                    ShoppingListUiState.Error(
                        AisleronException.ExceptionCode.GENERIC_EXCEPTION, e.message
                    )
            }
        }
    }

    fun clearState() {
        _shoppingListUiState.value = ShoppingListUiState.Empty
    }

    sealed class ShoppingListUiState {
        data object Empty : ShoppingListUiState()
        data object Loading : ShoppingListUiState()
        data class Error(
            val errorCode: AisleronException.ExceptionCode, val errorMessage: String?
        ) : ShoppingListUiState()

        data class Updated(val shoppingList: List<ShoppingListItem>) : ShoppingListUiState()
        
        // New state for recommendations
        data class UpdatedWithRecommendations(
            val shoppingList: List<ShoppingListItem>,
            val recommendations: List<ProductRecommendation>,
            val isFirstTimeToday: Boolean = true
        ) : ShoppingListUiState()
    }
}