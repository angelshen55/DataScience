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

import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aisleron.R
import com.aisleron.domain.FilterType
import com.aisleron.domain.base.AisleronException
import com.aisleron.domain.location.LocationType
import com.aisleron.domain.loyaltycard.LoyaltyCard
import com.aisleron.domain.product.ProductRecommendation
import com.aisleron.ui.AisleronExceptionMap
import com.aisleron.ui.AisleronFragment
import com.aisleron.ui.ApplicationTitleUpdateListener
import com.aisleron.ui.FabHandler
import com.aisleron.ui.FabHandler.FabClickedCallBack
import com.aisleron.ui.aisle.AisleDialog
import com.aisleron.ui.bundles.Bundler
import com.aisleron.ui.copyentity.CopyEntityDialogFragment
import com.aisleron.ui.copyentity.CopyEntityType
import com.aisleron.ui.loyaltycard.LoyaltyCardProvider
import com.aisleron.ui.settings.ShoppingListPreferences
import com.aisleron.ui.shoppinglist.ProductShoppingListItemViewModel
import com.aisleron.ui.widgets.ErrorSnackBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * A fragment representing a list of [ShoppingListItem].
 */
class ShoppingListFragment(
    private val applicationTitleUpdateListener: ApplicationTitleUpdateListener,
    private val fabHandler: FabHandler,
    private val shoppingListPreferences: ShoppingListPreferences,
    private val loyaltyCardProvider: LoyaltyCardProvider,
    private val aisleDialog: AisleDialog
) : Fragment(), SearchView.OnQueryTextListener, ActionMode.Callback, FabClickedCallBack,
    MenuProvider, AisleronFragment {

    private var actionMode: ActionMode? = null
    private var actionModeItem: ShoppingListItem? = null
    private var actionModeItemView: View? = null
    private var editShopMenuItem: MenuItem? = null
    private var loyaltyCardMenuItem: MenuItem? = null

    private val showEmptyAisles: Boolean
        get() = shoppingListPreferences.showEmptyAisles(requireContext())

    private val shoppingListViewModel: ShoppingListViewModel by viewModel()
    
    // Recommendation UI components
    private var recommendationsSection: LinearLayout? = null
    private var recommendationsRecyclerView: RecyclerView? = null
    private var recommendationsAdapter: RecommendationRecyclerViewAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shoppingListBundle = Bundler().getShoppingListBundle(arguments)
        
        // Pass context to ViewModel for preference access
        shoppingListViewModel.setContext(requireContext())
        
        shoppingListViewModel.hydrate(
            shoppingListBundle.locationId,
            shoppingListBundle.filterType,
            shoppingListPreferences.showEmptyAisles(requireContext())
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        initializeFab()

        val view = inflater.inflate(R.layout.fragment_shopping_list, container, false)

        // Initialize recommendation UI components
        recommendationsSection = view.findViewById(R.id.recommendations_section)
        recommendationsRecyclerView = view.findViewById(R.id.recommendations_list)
        recommendationsRecyclerView?.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        // Set the adapter
        if (view is RecyclerView) {
            setWindowInsetListeners(this, view, true, null)
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    shoppingListViewModel.shoppingListUiState.collect {
                        when (it) {
                            is ShoppingListViewModel.ShoppingListUiState.Error -> {
                                displayErrorSnackBar(
                                    it.errorCode,
                                    it.errorMessage,
                                    fabHandler.getFabView(requireActivity())
                                )

                                shoppingListViewModel.clearState()
                            }

                            is ShoppingListViewModel.ShoppingListUiState.Updated -> {
                                updateTitle()
                                setMenuItemVisibility()

                                (view.adapter as ShoppingListItemRecyclerViewAdapter).submitList(
                                    it.shoppingList
                                )
                            }
                            
                            // Handle the new state with recommendations
                            is ShoppingListViewModel.ShoppingListUiState.UpdatedWithRecommendations -> {
                                updateTitle()
                                setMenuItemVisibility()
                                
                                // Handle recommendations display
                                handleRecommendationsDisplay(it.recommendations, it.isFirstTimeToday)

                                (view.adapter as ShoppingListItemRecyclerViewAdapter).submitList(
                                    it.shoppingList
                                )
                            }

                            else -> Unit
                        }
                    }
                }
            }
            
            with(view) {
                var touchHelper: ItemTouchHelper? = null

                LinearLayoutManager(context)
                adapter = ShoppingListItemRecyclerViewAdapter(
                    createShoppingListItemListener(),
                    shoppingListPreferences.trackingMode(requireContext()),
                    shoppingListViewModel.defaultFilter
                )

                val callback: ItemTouchHelper.Callback = ShoppingListItemMoveCallbackListener(
                    view.adapter as ShoppingListItemRecyclerViewAdapter
                )
                touchHelper = ItemTouchHelper(callback)
                touchHelper.attachToRecyclerView(view)
            }
        } else {
            // Handle the new layout with recommendations section
            val shoppingListRecyclerView = view.findViewById<RecyclerView>(R.id.frg_shopping_list)
            if (shoppingListRecyclerView != null) {
                setWindowInsetListeners(this, shoppingListRecyclerView, true, null)
                viewLifecycleOwner.lifecycleScope.launch {
                    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        shoppingListViewModel.shoppingListUiState.collect {
                            when (it) {
                                is ShoppingListViewModel.ShoppingListUiState.Error -> {
                                    displayErrorSnackBar(
                                        it.errorCode,
                                        it.errorMessage,
                                        fabHandler.getFabView(requireActivity())
                                    )

                                    shoppingListViewModel.clearState()
                                }

                                is ShoppingListViewModel.ShoppingListUiState.Updated -> {
                                    updateTitle()
                                    setMenuItemVisibility()
                                    
                                    // Hide recommendations section
                                    recommendationsSection?.visibility = View.GONE

                                    (shoppingListRecyclerView.adapter as? ShoppingListItemRecyclerViewAdapter)?.submitList(
                                        it.shoppingList
                                    ) ?: run {
                                        shoppingListRecyclerView.adapter = ShoppingListItemRecyclerViewAdapter(
                                            createShoppingListItemListener(),
                                            shoppingListPreferences.trackingMode(requireContext()),
                                            shoppingListViewModel.defaultFilter
                                        )
                                        (shoppingListRecyclerView.adapter as ShoppingListItemRecyclerViewAdapter).submitList(
                                            it.shoppingList
                                        )
                                    }
                                }
                                
                                // Handle the new state with recommendations
                                is ShoppingListViewModel.ShoppingListUiState.UpdatedWithRecommendations -> {
                                    updateTitle()
                                    setMenuItemVisibility()
                                    
                                    // Handle recommendations display
                                    handleRecommendationsDisplay(it.recommendations, it.isFirstTimeToday)

                                    (shoppingListRecyclerView.adapter as? ShoppingListItemRecyclerViewAdapter)?.submitList(
                                        it.shoppingList
                                    ) ?: run {
                                        shoppingListRecyclerView.adapter = ShoppingListItemRecyclerViewAdapter(
                                            createShoppingListItemListener(),
                                            shoppingListPreferences.trackingMode(requireContext()),
                                            shoppingListViewModel.defaultFilter
                                        )
                                        (shoppingListRecyclerView.adapter as ShoppingListItemRecyclerViewAdapter).submitList(
                                            it.shoppingList
                                        )
                                    }
                                }

                                else -> Unit
                            }
                        }
                    }
                }
                
                with(shoppingListRecyclerView) {
                    var touchHelper: ItemTouchHelper? = null

                    LinearLayoutManager(context)
                    if (adapter == null) {
                        adapter = ShoppingListItemRecyclerViewAdapter(
                            createShoppingListItemListener(),
                            shoppingListPreferences.trackingMode(requireContext()),
                            shoppingListViewModel.defaultFilter
                        )
                    }

                    val callback: ItemTouchHelper.Callback = ShoppingListItemMoveCallbackListener(
                        adapter as ShoppingListItemRecyclerViewAdapter
                    )
                    touchHelper = ItemTouchHelper(callback)
                    touchHelper.attachToRecyclerView(shoppingListRecyclerView)
                }
            }
        }
        return view
    }
    
    private fun handleRecommendationsDisplay(recommendations: List<ProductRecommendation>, isFirstTimeToday: Boolean = true) {
        // Always show recommendations section when manually triggered
        recommendationsSection?.visibility = View.VISIBLE
        
        // Show hint message if not first time today
        val hintTextView = view?.findViewById<android.widget.TextView>(R.id.recommendations_hint)
        if (!isFirstTimeToday) {
            hintTextView?.visibility = View.VISIBLE
            hintTextView?.text = "You already checked"
        } else {
            hintTextView?.visibility = View.GONE
        }
        
        // Show message if no recommendations, otherwise show recommendations
        if (recommendations.isEmpty()) {
            // Show a message indicating no recommendations
            recommendationsAdapter = RecommendationRecyclerViewAdapter(
                listOf(createNoRecommendationsItem()),
                object : RecommendationRecyclerViewAdapter.RecommendationListener {
                    override fun onAddToListClicked(recommendation: ProductRecommendation) {
                        // Do nothing for the "no recommendations" item
                    }
                }
            )
        } else {
            recommendationsAdapter = RecommendationRecyclerViewAdapter(
                recommendations,
                object : RecommendationRecyclerViewAdapter.RecommendationListener {
                    override fun onAddToListClicked(recommendation: ProductRecommendation) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            // Restore product if soft deleted, then add to current location's default aisle
                            shoppingListViewModel.restoreAndAddProductToCurrentLocation(
                                recommendation.product.id,
                                shoppingListViewModel.locationId,
                                recommendation.product.name
                            )
                            
                            // Move the product to needed status
                            // Create a simple data class implementing ProductShoppingListItem with only the required fields
                            data class SimpleProductShoppingListItem(
                                override val id: Int,
                                override val name: String,
                                override val inStock: Boolean,
                                override val qtyNeeded: Int,
                                override val price: Double
                            ) : ProductShoppingListItem {
                                override val aisleId: Int = 0
                                override val aisleRank: Int = 0
                                override val rank: Int = 0
                                override val itemType: ShoppingListItem.ItemType = ShoppingListItem.ItemType.PRODUCT
                            }
                            
                            val item = SimpleProductShoppingListItem(
                                id = recommendation.product.id,
                                name = recommendation.product.name,
                                inStock = recommendation.product.inStock,
                                qtyNeeded = recommendation.product.qtyNeeded,
                                price = recommendation.product.price
                            )
                            shoppingListViewModel.updateProductStatus(item, false)
                            
                            // Update last display date to prevent automatic recommendations today
                            // But still allow manual recommendations via the button
                            if (shoppingListViewModel.context != null) {
                                val preferences = com.aisleron.ui.settings.ShoppingListPreferencesImpl()
                                preferences.setLastRecommendationDisplayDate(
                                    shoppingListViewModel.context!!, 
                                    System.currentTimeMillis()
                                )
                            }
                            
                            // Request a refresh of the shopping list to show the added product
                            shoppingListViewModel.requestDefaultList()
                        }
                    }
                }
            )
        }
        recommendationsRecyclerView?.adapter = recommendationsAdapter
    }
    
    // Helper method to create a "no recommendations" item
    private fun createNoRecommendationsItem(): ProductRecommendation {
        // Create a dummy product for the "no recommendations" message
        val dummyProduct = com.aisleron.domain.product.Product(
            id = -1,
            name = "No recommendations available",
            inStock = false,
            qtyNeeded = 0,
            price = 0.0
        )
        
        return ProductRecommendation(
            product = dummyProduct,
            score = 0.0,
            purchaseCount = 0,
            frequencyScore = 0.0,
            timeDecayScore = 0.0,
            periodicityScore = 0.0,
            daysSinceLastPurchase = 0,
            averagePurchaseInterval = 0.0
        )
    }

    private fun createShoppingListItemListener(): ShoppingListItemRecyclerViewAdapter.ShoppingListItemListener {
        return object : ShoppingListItemRecyclerViewAdapter.ShoppingListItemListener {
            override fun onClick(item: ShoppingListItem) {
                actionMode?.finish()
            }

            override fun onProductStatusChange(
                item: ProductShoppingListItem,
                inStock: Boolean
            ) {
                actionMode?.finish()
                shoppingListViewModel.updateProductStatus(item, inStock)
                displayStatusChangeSnackBar(item, inStock)
            }

            override fun onProductQuantityChange(
                item: ProductShoppingListItem, quantity: Int
            ) {
                shoppingListViewModel.updateProductNeededQuantity(item, quantity)
            }

            override fun onProductPriceChange(
                item: ProductShoppingListItem,
                price: Double
            ) {
                shoppingListViewModel.updateProductPrice(item, price)
            }

            override fun onListPositionChanged(
                item: ShoppingListItem, precedingItem: ShoppingListItem?
            ) {
                actionMode?.finish()
                shoppingListViewModel.updateItemRank(item, precedingItem)
            }

            override fun onLongClick(item: ShoppingListItem, view: View): Boolean {
                // Finish the previous action mode and start a new one
                actionMode?.finish()
                if (item.itemType == ShoppingListItem.ItemType.EMPTY_LIST) {
                    return false
                }

                actionModeItem = item
                actionModeItemView = view
                actionModeItemView?.isSelected = true
                return when (actionMode) {
                    null -> {
                        // Start the CAB using the ActionMode.Callback defined earlier.
                        actionMode =
                            (requireActivity() as AppCompatActivity).startSupportActionMode(
                                this@ShoppingListFragment
                            )
                        true
                    }

                    else -> false
                }
            }

            override fun onMoved(item: ShoppingListItem) {
                shoppingListViewModel.movedItem(item)
            }

            override fun onAisleExpandToggle(
                item: AisleShoppingListItem, expanded: Boolean
            ) {
                actionMode?.finish()
                shoppingListViewModel.updateAisleExpanded(item, expanded)
            }

            override fun onDragStart(viewHolder: RecyclerView.ViewHolder) {
                // This will be handled by the touchHelper in the with block
            }

            override fun onMove(item: ShoppingListItem) {}
        }
    }
    
    override fun onResume() {
        super.onResume()
        shoppingListViewModel.setShowEmptyAisles(showEmptyAisles)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        aisleDialog.observeLifecycle(viewLifecycleOwner)
        val menuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        view.keepScreenOn = shoppingListPreferences.keepScreenOn(requireContext())
    }
    
    private fun setMenuItemVisibility() {
        editShopMenuItem?.isVisible = shoppingListViewModel.locationType == LocationType.SHOP
        loyaltyCardMenuItem?.isVisible = shoppingListViewModel.loyaltyCard != null
    }

    private fun displayStatusChangeSnackBar(item: ProductShoppingListItem, inStock: Boolean) {
        if (shoppingListPreferences.isStatusChangeSnackBarHidden(requireContext())) return

        val newStatus = getString(if (inStock) R.string.menu_in_stock else R.string.menu_needed)

        Snackbar.make(
            requireView(),
            getString(R.string.status_change_confirmation, item.name, newStatus),
            Snackbar.LENGTH_SHORT
        ).setAction(getString(R.string.undo)) { _ ->
            shoppingListViewModel.updateProductStatus(item, !inStock)
        }.setAnchorView(fabHandler.getFabView(this.requireActivity())).show()
    }

    private fun displayErrorSnackBar(
        errorCode: AisleronException.ExceptionCode,
        errorMessage: String?,
        anchorView: View?
    ) {
        val snackBarMessage =
            getString(AisleronExceptionMap().getErrorResourceId(errorCode), errorMessage)

        ErrorSnackBar().make(
            requireView(),
            snackBarMessage,
            Snackbar.LENGTH_SHORT,
            anchorView
        ).show()
    }

    private fun initializeFab() {
        fabHandler.setFabOnClickedListener(this)
        fabHandler.setFabItems(
            this.requireActivity(),
            FabHandler.FabOption.ADD_SHOP,
            FabHandler.FabOption.ADD_AISLE,
            FabHandler.FabOption.ADD_PRODUCT
        )

        fabHandler.setFabOnClickListener(this.requireActivity(), FabHandler.FabOption.ADD_PRODUCT) {
            navigateToAddProduct(shoppingListViewModel.defaultFilter)
        }

        fabHandler.setFabOnClickListener(this.requireActivity(), FabHandler.FabOption.ADD_AISLE) {
            aisleDialog.showAddDialog(requireContext(), shoppingListViewModel.locationId)
        }
    }

    private fun updateTitle() {
        val appTitle =
            when (shoppingListViewModel.locationType) {
                LocationType.HOME ->
                    when (shoppingListViewModel.defaultFilter) {
                        FilterType.IN_STOCK -> resources.getString(R.string.menu_in_stock)
                        FilterType.NEEDED -> resources.getString(R.string.menu_needed)
                        FilterType.ALL -> resources.getString(R.string.menu_all_items)
                    }

                LocationType.SHOP -> shoppingListViewModel.locationName
            }

        applicationTitleUpdateListener.applicationTitleUpdated(requireActivity(), appTitle)
    }

    private fun navigateToAddProduct(filterType: FilterType, aisleId: Int? = null) {
        val bundle =
            Bundler().makeAddProductBundle(
                locationId = shoppingListViewModel.locationId,
                name = null,
                inStock = filterType == FilterType.IN_STOCK,
                aisleId = aisleId
            )

        this.findNavController().navigate(R.id.nav_add_product, bundle)
    }

    private fun navigateToEditProduct(productId: Int) {
        val bundle = Bundler().makeEditProductBundle(
            productId = productId, locationId = shoppingListViewModel.locationId
        )

        this.findNavController().navigate(R.id.nav_add_product, bundle)
    }

    private fun navigateToEditShop(locationId: Int) {
        val bundle = Bundler().makeEditLocationBundle(locationId)
        this.findNavController().navigate(R.id.nav_add_shop, bundle)
    }

    private fun confirmDelete(context: Context, item: ShoppingListItem) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
        builder
            .setTitle(getString(R.string.delete_confirmation, item.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                shoppingListViewModel.removeItem(item)
            }

        val dialog: AlertDialog = builder.create()

        dialog.show()
    }

    private fun showCopyProductDialog(item: ShoppingListItem) {
        val dialog = CopyEntityDialogFragment.newInstance(
            type = CopyEntityType.Product(item.id),
            title = getString(R.string.copy_entity_title, item.name),
            defaultName = "${item.name} (${getString(android.R.string.copy)})",
            nameHint = getString(R.string.new_product_name)
        )

        dialog.onCopySuccess = {
            requireView().postDelayed({
                Snackbar.make(
                    requireView(),
                    getString(R.string.entity_copied, item.name),
                    Snackbar.LENGTH_SHORT
                )
                    .setAnchorView(fabHandler.getFabView(this.requireActivity()))
                    .show()
            }, 250)
        }

        dialog.show(childFragmentManager, "copyDialog")
    }

    private fun editShoppingListItem(item: ShoppingListItem) {
        when (item) {
            is AisleShoppingListItem -> aisleDialog.showEditDialog(requireContext(), item)
            is ProductShoppingListItem -> navigateToEditProduct(item.id)
        }
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        shoppingListViewModel.submitProductSearch(productNameQuery = newText ?: "")
        return false
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        // Inflate a menu resource providing context menu items.
        val inflater: MenuInflater = mode.menuInflater
        inflater.inflate(R.menu.shopping_list_fragment_context, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.title = actionModeItem?.name
        menu.findItem(R.id.mnu_delete_shopping_list_item)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)

        menu.findItem(R.id.mnu_add_product_to_aisle)
            .setVisible(actionModeItem?.itemType == ShoppingListItem.ItemType.AISLE)

        menu.findItem(R.id.mnu_copy_shopping_list_item)
            .setVisible(actionModeItem?.itemType == ShoppingListItem.ItemType.PRODUCT)

        return false // Return false if nothing is done
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        var result = true
        when (item.itemId) {
            R.id.mnu_edit_shopping_list_item ->
                actionModeItem?.let { editShoppingListItem(it) }

            R.id.mnu_delete_shopping_list_item ->
                actionModeItem?.let { confirmDelete(requireContext(), it) }

            R.id.mnu_add_product_to_aisle ->
                actionModeItem?.let {
                    navigateToAddProduct(
                        shoppingListViewModel.defaultFilter, it.aisleId
                    )
                }

            R.id.mnu_copy_shopping_list_item ->
                actionModeItem?.let { showCopyProductDialog(it) }

            else -> result = false // No action picked, so don't close the CAB.
        }

        if (result) mode.finish()  // Action picked, so close the CAB.

        return result
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        actionModeItemView?.isSelected = false
        actionMode = null
        actionModeItem = null
        actionModeItemView = null
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.shopping_list_fragment_main, menu)

        val searchManager =
            getSystemService(requireContext(), SearchManager::class.java) as SearchManager
        val searchableInfo =
            searchManager.getSearchableInfo(requireActivity().componentName)

        val searchView = menu.findItem(R.id.action_search).actionView as SearchView
        searchView.setMaxWidth(Integer.MAX_VALUE)
        searchView.setSearchableInfo(searchableInfo)
        searchView.setOnQueryTextListener(this@ShoppingListFragment)
        searchView.setOnCloseListener {
            shoppingListViewModel.requestDefaultList()
            false
        }

        //OnAttachStateChange is here as a workaround because OnCloseListener doesn't fire
        searchView.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}

            override fun onViewDetachedFromWindow(v: View) {
                shoppingListViewModel.requestDefaultList()
            }
        })

        menu.findItem(R.id.mnu_show_empty_aisles).apply { isChecked = showEmptyAisles }

        editShopMenuItem = menu.findItem(R.id.mnu_edit_shop)
        loyaltyCardMenuItem = menu.findItem(R.id.mnu_show_loyalty_card)
        setMenuItemVisibility()
    }

    //NOTE: If you override onMenuItemSelected, OnSupportNavigateUp will only be called when returning false
    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.mnu_edit_shop -> {
                navigateToEditShop(locationId = shoppingListViewModel.locationId)
                true
            }
            
            R.id.mnu_show_recommendations -> {
                // Show recommendations manually
                showRecommendations()
                true
            }

            R.id.mnu_sort_list_by_name -> {
                confirmSort(requireContext())
                true
            }

            R.id.mnu_show_loyalty_card -> {
                shoppingListViewModel.loyaltyCard?.let { showLoyaltyCard(it) }
                true
            }

            R.id.mnu_show_empty_aisles -> {
                shoppingListPreferences.setShowEmptyAisles(requireContext(), !showEmptyAisles)
                menuItem.isChecked = showEmptyAisles
                shoppingListViewModel.setShowEmptyAisles(showEmptyAisles)

                true
            }

            else -> false
        }
    }
    
    private fun showRecommendations() {
        // Check if recommendations are currently visible
        if (recommendationsSection?.visibility == View.VISIBLE && recommendationsAdapter != null) {
            // Hide recommendations if they're already visible
            shoppingListViewModel.hideRecommendations()
        } else {
            // Request recommendations from the ViewModel with forceRefresh=true
            // This ensures we always recalculate when manually triggered via button
            shoppingListViewModel.loadRecommendations(forceRefresh = true)
        }
    }
    
    // Modify this method to show a message when there are no recommendations
    private fun showNoRecommendationsMessage() {
        Snackbar.make(
            requireView(),
            "Today's recommendations have already been shown",
            Snackbar.LENGTH_SHORT
        ).setAnchorView(fabHandler.getFabView(this.requireActivity())).show()
    }

    private fun showLoyaltyCard(loyaltyCard: LoyaltyCard) {
        try {
            loyaltyCardProvider.displayLoyaltyCard(requireContext(), loyaltyCard)
        } catch (e: AisleronException.LoyaltyCardProviderException) {
            loyaltyCardProvider.getNotInstalledDialog(requireContext()).show()
        } catch (e: Exception) {
            displayErrorSnackBar(

                AisleronException.ExceptionCode.GENERIC_EXCEPTION,
                e.message,
                fabHandler.getFabView(this.requireActivity())
            )
        }
    }

    private fun confirmSort(context: Context) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
        builder
            .setTitle(getString(R.string.sort_confirm_title))
            .setMessage(R.string.sort_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                shoppingListViewModel.sortListByName()
            }

        val dialog: AlertDialog = builder.create()

        dialog.show()
    }

    companion object {

        private const val ARG_LOCATION_ID = "locationId"
        private const val ARG_FILTER_TYPE = "filterType"

        @JvmStatic
        fun newInstance(
            locationId: Long,
            filterType: FilterType,
            applicationTitleUpdateListener: ApplicationTitleUpdateListener,
            fabHandler: FabHandler,
            shoppingListPreferences: ShoppingListPreferences,
            loyaltyCardProvider: LoyaltyCardProvider,
            alertDialog: AisleDialog
        ) =
            ShoppingListFragment(
                applicationTitleUpdateListener,
                fabHandler,
                shoppingListPreferences,
                loyaltyCardProvider,
                alertDialog
            ).apply {
                arguments = Bundle().apply {
                    putInt(ARG_LOCATION_ID, locationId.toInt())
                    putSerializable(ARG_FILTER_TYPE, filterType)
                }
            }
    }

    override fun fabClicked(fabOption: FabHandler.FabOption) {
        actionMode?.finish()
    }
}