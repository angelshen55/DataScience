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

package com.aisleron.ui.product

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.view.doOnLayout
import com.aisleron.utils.PriceInputUtils
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aisleron.R
import com.aisleron.databinding.FragmentProductBinding
import com.aisleron.domain.base.AisleronException
import com.aisleron.ui.AddEditFragmentListener
import com.aisleron.ui.AisleronExceptionMap
import com.aisleron.ui.AisleronFragment
import com.aisleron.ui.ApplicationTitleUpdateListener
import com.aisleron.ui.FabHandler
import com.aisleron.ui.bundles.AddEditProductBundle
import com.aisleron.ui.bundles.Bundler
import com.aisleron.ui.widgets.ErrorSnackBar
import com.aisleron.ui.settings.ShoppingListPreferencesImpl
import com.aisleron.domain.product.Product
import com.aisleron.domain.product.ProductRepository
import com.aisleron.domain.product.usecase.AddProductUseCase
import com.aisleron.domain.product.usecase.GetProductUseCase
import com.aisleron.domain.product.usecase.UpdateProductStatusUseCase
import com.aisleron.domain.aisle.usecase.GetAisleUseCase
import com.aisleron.domain.aisle.usecase.GetDefaultAisleForLocationUseCase
import com.aisleron.domain.aisleproduct.AisleProductRepository
import com.aisleron.domain.aisleproduct.usecase.AddAisleProductsUseCase
import com.aisleron.domain.aisleproduct.usecase.GetAisleMaxRankUseCase
import com.aisleron.domain.aisleproduct.AisleProduct
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.aisleron.data.api.ModelApiService
import com.aisleron.data.api.GenerateRequest

class ProductFragment(
    private val addEditFragmentListener: AddEditFragmentListener,
    private val applicationTitleUpdateListener: ApplicationTitleUpdateListener,
    private val fabHandler: FabHandler
) : Fragment(), MenuProvider, AisleronFragment, KoinComponent {

    private val productViewModel: ProductViewModel by viewModel()
    private var _binding: FragmentProductBinding? = null

    private val binding get() = _binding!!

    private var appTitle: String = ""
    
    // Tracker for recommendation dialog metrics
    private val recommendationTracker: RecommendationDialogTracker by lazy {
        RecommendationDialogTracker(requireContext())
    }
    
    // Independent coroutine scope for background tasks that should not be cancelled by lifecycle
    // Use application scope to ensure it survives Fragment lifecycle
    private val backgroundScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + kotlinx.coroutines.CoroutineName("PurchaseSetCollection")
    )
    
    // Flag to track if Fragment is being destroyed
    private var isFragmentDestroyed = false
    
    // Inject use cases for purchase set collection
    private val collectPurchaseSetsUseCase: com.aisleron.domain.product.usecase.CollectPurchaseSetsUseCase by inject()
    private val purchaseSetRepository: com.aisleron.domain.product.PurchaseSetRepository by inject()
    private val modelTrainingDataUploader: com.aisleron.domain.product.ModelTrainingDataUploader by inject()
    private val recordRepository: com.aisleron.domain.record.RecordRepository by inject()
    
    // Inject API service for model communication
    private val modelApiService: ModelApiService by inject()
    
    // Inject use cases for adding products to needed list
    private val productRepository: ProductRepository by inject()
    private val getProductUseCase: GetProductUseCase by inject()
    private val addProductUseCase: AddProductUseCase by inject()
    private val updateProductStatusUseCase: UpdateProductStatusUseCase by inject()
    private val getAisleUseCase: GetAisleUseCase by inject()
    private val getDefaultAisleForLocationUseCase: GetDefaultAisleForLocationUseCase by inject()
    private val aisleProductRepository: AisleProductRepository by inject()
    private val addAisleProductsUseCase: AddAisleProductsUseCase by inject()
    private val getAisleMaxRankUseCase: GetAisleMaxRankUseCase by inject()
    private val getHomeLocationUseCase: com.aisleron.domain.location.usecase.GetHomeLocationUseCase by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val addEditProductBundle = Bundler().getAddEditProductBundle(arguments)

        appTitle = when (addEditProductBundle.actionType) {
            AddEditProductBundle.ProductAction.ADD -> getString(R.string.add_product)
            AddEditProductBundle.ProductAction.EDIT -> getString(R.string.edit_product)
        }

        if (savedInstanceState == null) {
            productViewModel.hydrate(
                addEditProductBundle.productId,
                addEditProductBundle.inStock ?: false,
                addEditProductBundle.locationId,
                addEditProductBundle.aisleId
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        fabHandler.setFabItems(this.requireActivity())

        _binding = FragmentProductBinding.inflate(inflater, container, false)
        setWindowInsetListeners(this, binding.root, false, R.dimen.text_margin)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    productViewModel.uiData.collect { data ->
                        // Update EditText only if needed to avoid cursor jumping
                        if (binding.edtProductName.text.toString() != data.productName) {
                            binding.edtProductName.setText(data.productName)
                        }

                        // Update price field
                        val priceText = if (data.price == 0.0) "" else data.price.toString()
                        if (binding.edtProductPrice.text.toString() != priceText) {
                            if (data.price == 0.0) {
                                binding.edtProductPrice.setText("")
                            } else {
                                PriceInputUtils.setPriceText(binding.edtProductPrice, data.price)
                            }
                        }

                        // Update CheckedTextView
                        if (binding.chkProductInStock.isChecked != data.inStock) {
                            binding.chkProductInStock.isChecked = data.inStock
                        }
                    }
                }

                launch {
                    productViewModel.productUiState.collect {
                        when (it) {
                            is ProductViewModel.ProductUiState.Success -> {
                                if (it.showRecommendationDialog) {
                                    showRecommendationBottomSheet()
                                } else {
                                    addEditFragmentListener.addEditActionCompleted(requireActivity())
                                }
                            }

                            is ProductViewModel.ProductUiState.Error -> {
                                displayErrorSnackBar(it.errorCode, it.errorMessage)
                            }

                            ProductViewModel.ProductUiState.Loading,
                            ProductViewModel.ProductUiState.Empty -> Unit
                        }
                    }
                }
            }
        }

        binding.edtProductName.doAfterTextChanged {
            val newText = it?.toString() ?: ""
            if (productViewModel.uiData.value.productName != newText) {
                productViewModel.updateProductName(newText)
            }
        }

        // Set up Enter key listener for price updates
        PriceInputUtils.setupPriceEnterListenerWithIme(binding.edtProductPrice) {
            val newText = binding.edtProductPrice.text.toString()
            val price = newText.toDoubleOrNull() ?: 0.0
            if (productViewModel.uiData.value.price != price) {
                productViewModel.updatePrice(price)
            }
        }

        binding.chkProductInStock.setOnClickListener {
            val chk = binding.chkProductInStock
            chk.isChecked = !chk.isChecked
            if (productViewModel.uiData.value.inStock != chk.isChecked) {
                productViewModel.updateInStock(chk.isChecked)
            }
        }

        return binding.root
    }

    private fun displayErrorSnackBar(
        errorCode: AisleronException.ExceptionCode, errorMessage: String?
    ) {
        val snackBarMessage =
            getString(AisleronExceptionMap().getErrorResourceId(errorCode), errorMessage)
        ErrorSnackBar().make(requireView(), snackBarMessage, Snackbar.LENGTH_SHORT).show()
    }

    private fun showRecommendationBottomSheet() {
        // Get the location ID from the bundle
        val addEditProductBundle = Bundler().getAddEditProductBundle(arguments)
        val locationId = addEditProductBundle.locationId
        
        // Get current product name from ViewModel
        val currentProductName = productViewModel.uiData.value.productName
        
        // Call model API to get recommendations first, then decide whether to show dialog
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Build prompt for model
                val prompt = if (currentProductName.isNotBlank()) {
                    "顾客已购买「$currentProductName」，请推测该顾客还可能一起购买的其他商品名称。"
                } else {
                    "请推荐一些常见的购物商品。"
                }
                
                android.util.Log.i("ProductFragment", "Calling model API with prompt: $prompt")
                
                // Call model API
                val request = GenerateRequest(
                    prompt = prompt,
                    max_new_tokens = 128
                )
                val response = modelApiService.generateRecommendations(request)
                
                if (response.isSuccessful && response.body() != null) {
                    val prediction = response.body()!!.prediction
                    android.util.Log.i("ProductFragment", "Raw model prediction: $prediction")
                    android.util.Log.i("ProductFragment", "Prediction length: ${prediction.length}")
                    
                    // Parse prediction to extract product names
                    val recommendedProductNames = parseProductNamesFromPrediction(prediction)
                    android.util.Log.i("ProductFragment", "Parsed ${recommendedProductNames.size} product names: $recommendedProductNames")
                    
                    if (recommendedProductNames.isEmpty()) {
                        android.util.Log.w("ProductFragment", "Warning: No products parsed from prediction. Raw prediction was: $prediction")
                    }
                    
                    // Get home location for checking stock
                    val homeLocation = getHomeLocationUseCase()
                    val homeLocationId = homeLocation?.id
                    
                    // Filter out products that are already in needed list or stock
                    val filteredProductNames = recommendedProductNames.filter { name ->
                        val product = productRepository.getByName(name.trim())
                        if (product != null && product.id > 0) {
                            // Check if product is in needed list (inStock = false) in current location
                            val inNeededList = locationId != null && isProductInNeededList(product.id, locationId)
                            
                            // Check if product is in stock (inStock = true) in home location
                            val inStock = homeLocationId != null && isProductInStock(product.id, homeLocationId)
                            
                            // Filter out if in needed list or stock
                            val shouldFilter = inNeededList || inStock
                            
                            if (shouldFilter) {
                                android.util.Log.d("ProductFragment", "Filtering out ${product.name}: inNeededList=$inNeededList, inStock=$inStock")
                            }
                            
                            !shouldFilter
                        } else {
                            // Product doesn't exist yet, include it
                            true
                        }
                    }
                    
                    android.util.Log.i("ProductFragment", "After filtering: ${filteredProductNames.size} products (filtered out ${recommendedProductNames.size - filteredProductNames.size})")
                    
                    // If no products remain after filtering, don't show the dialog
                    if (filteredProductNames.isEmpty()) {
                        android.util.Log.i("ProductFragment", "No products to recommend after filtering, skipping dialog")
                        // Navigate back since there's nothing to show
                        activity?.let {
                            if (isAdded) {
                                addEditFragmentListener.addEditActionCompleted(it)
                            }
                        }
                        return@launch
                    }
                    
                    // Only create and show dialog if we have products to recommend
                    val bottomSheetDialog = BottomSheetDialog(requireContext())
                    val view = LayoutInflater.from(requireContext()).inflate(
                        R.layout.dialog_recommendation_bottom_sheet,
                        null
                    )
                    
                    val totalRecommended = filteredProductNames.size
                    
                    // Start tracking dialog
                    recommendationTracker.onDialogShown(totalRecommended)
                    
                    // Get products by name and check if they're in needed list (for button state)
                    val recommendedProductsWithStatus = filteredProductNames.map { name ->
                        val product = productRepository.getByName(name.trim()) ?: Product(
                            id = 0, // Product doesn't exist
                            name = name.trim(),
                            inStock = false,
                            qtyNeeded = 0,
                            price = 0.0
                        )
                        
                        // Check if product is already in needed list (for button state)
                        val isInNeededList = if (product.id > 0 && locationId != null) {
                            isProductInNeededList(product.id, locationId)
                        } else {
                            false
                        }
                        
                        ProductWithStatus(product, isInNeededList)
                    }
                    
                    // Set up RecyclerView
                    val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_recommendations)
                    recyclerView.layoutManager = LinearLayoutManager(requireContext())
                    recyclerView.adapter = RecommendationProductAdapter(
                        recommendedProductsWithStatus.map { it.product },
                        recommendedProductsWithStatus.map { it.isInNeededList },
                        object : RecommendationProductAdapter.RecommendationProductListener {
                            override fun onAddToListClicked(product: Product) {
                                viewLifecycleOwner.lifecycleScope.launch {
                                    addProductToNeededList(product, locationId)
                                    // Track product addition
                                    recommendationTracker.onProductAdded()
                                    // Show feedback that product was added
                                    Snackbar.make(
                                        view,
                                        "${product.name} added to needed list",
                                        Snackbar.LENGTH_SHORT
                                    ).show()
                                    // Refresh the adapter to update button states
                                    val updatedProductsWithStatus = filteredProductNames.map { name ->
                                        val p = productRepository.getByName(name.trim()) ?: Product(
                                            id = 0,
                                            name = name.trim(),
                                            inStock = false,
                                            qtyNeeded = 0,
                                            price = 0.0
                                        )
                                        val isInNeededList = if (p.id > 0 && locationId != null) {
                                            isProductInNeededList(p.id, locationId)
                                        } else {
                                            false
                                        }
                                        ProductWithStatus(p, isInNeededList)
                                    }
                                    (recyclerView.adapter as? RecommendationProductAdapter)?.updateProducts(
                                        updatedProductsWithStatus.map { it.product },
                                        updatedProductsWithStatus.map { it.isInNeededList }
                                    )
                                }
                            }
                        }
                    )
                    
                    // Set up "Stop Recommendation Today" button
                    val stopButton = view.findViewById<android.widget.Button>(R.id.btn_stop_recommendation_today)
                    stopButton.setOnClickListener {
                        val preferences = ShoppingListPreferencesImpl()
                        preferences.setLastRecommendationDisplayDate(
                            requireContext(),
                            System.currentTimeMillis()
                        )
                        bottomSheetDialog.dismiss()
                    }
                    
                    bottomSheetDialog.setContentView(view)
                    
                    // Set up dismiss listener
                    bottomSheetDialog.setOnDismissListener {
                        // Track dialog dismissal and log metrics
                        recommendationTracker.onDialogDismissed()
                        
                        // If retraining is needed, collect purchase sets for model training
                        val shouldRetrain = recommendationTracker.getLastRetrainDecision()
                        if (shouldRetrain) {
                            // Use independent background scope to avoid cancellation when Fragment is destroyed
                            backgroundScope.launch {
                                try {
                                    handleRetrainNeeded()
                                } catch (e: Exception) {
                                    android.util.Log.e("ProductFragment", "Error in handleRetrainNeeded", e)
                                }
                            }
                        }
                        
                        // Navigate back after dialog is dismissed
                        // Check if Fragment is still attached to avoid IllegalStateException
                        activity?.let {
                            if (isAdded) {
                                addEditFragmentListener.addEditActionCompleted(it)
                            }
                        }
                    }
                    
                    // Show the dialog
                    bottomSheetDialog.show()
                } else {
                    android.util.Log.e("ProductFragment", "Model API call failed: ${response.code()} - ${response.message()}")
                    // API call failed, just navigate back
                    activity?.let {
                        if (isAdded) {
                            addEditFragmentListener.addEditActionCompleted(it)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ProductFragment", "Error calling model API", e)
                // Error occurred, just navigate back
                activity?.let {
                    if (isAdded) {
                        addEditFragmentListener.addEditActionCompleted(it)
                    }
                }
            }
        }
    }
    
    /**
     * Parse product names from model prediction
     * Handles various formats:
     * - Comma-separated: "Egg, Tomato, Salt"
     * - With quotes: "Egg", "Tomato", "Salt"
     * - List format: ["Egg", "Tomato", "Salt"]
     * - Chinese comma: "Egg，Tomato，Salt"
     * - Mixed formats
     */
    private fun parseProductNamesFromPrediction(prediction: String): List<String> {
        if (prediction.isBlank()) {
            return emptyList()
        }
        
        var cleaned = prediction.trim()
        
        // Remove JSON array brackets if present: ["Egg", "Tomato"] -> "Egg", "Tomato"
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.removePrefix("[").removeSuffix("]").trim()
        }
        
        // Remove curly braces if present: {"Egg", "Tomato"} -> "Egg", "Tomato"
        if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
            cleaned = cleaned.removePrefix("{").removeSuffix("}").trim()
        }
        
        // Split by comma (both English and Chinese comma)
        val products = mutableListOf<String>()
        
        // Try to split by comma (handle both English and Chinese comma)
        val parts = cleaned.split(Regex("[,，]"))
        
        for (part in parts) {
            var productName = part.trim()
            
            // Remove quotes if present (both single and double quotes)
            productName = productName.removeSurrounding("\"").removeSurrounding("'").trim()
            
            // Remove any leading/trailing punctuation
            productName = productName.trim().removePrefix("「").removeSuffix("」")
            productName = productName.trim().removePrefix("《").removeSuffix("》")
            productName = productName.trim().removePrefix("(").removeSuffix(")")
            productName = productName.trim().removePrefix("[").removeSuffix("]")
            
            // Only add non-empty product names
            if (productName.isNotBlank()) {
                products.add(productName)
            }
        }
        
        // Remove duplicates while preserving order
        val uniqueProducts = products.distinct()
        
        // Limit to 10 recommendations
        return uniqueProducts.take(10)
    }
    
    // Data class to hold product with its status
    private data class ProductWithStatus(
        val product: Product,
        val isInNeededList: Boolean
    )
    
    // Check if product is already in needed list (in current location with inStock = false)
    private suspend fun isProductInNeededList(productId: Int, locationId: Int): Boolean {
        if (productId == 0) return false
        
        // Get all aisles where this product exists
        val productAisles = aisleProductRepository.getProductAisles(productId)
        
        // Check if product is in any aisle of the current location and is needed (inStock = false)
        return productAisles.any { ap ->
            val aisle = getAisleUseCase(ap.aisleId)
            val product = getProductUseCase(productId)
            aisle?.locationId == locationId && product?.inStock == false
        }
    }
    
    // Check if product is already in stock (in home location with inStock = true)
    private suspend fun isProductInStock(productId: Int, locationId: Int): Boolean {
        if (productId == 0) return false
        
        // Get all aisles where this product exists
        val productAisles = aisleProductRepository.getProductAisles(productId)
        
        // Check if product is in any aisle of the location and is in stock (inStock = true)
        return productAisles.any { ap ->
            val aisle = getAisleUseCase(ap.aisleId)
            val product = getProductUseCase(productId)
            aisle?.locationId == locationId && product?.inStock == true
        }
    }
    
    /**
     * Handle retrain needed: collect purchase sets and prepare for model training
     * This runs in background scope and doesn't depend on Fragment lifecycle
     */
    private suspend fun handleRetrainNeeded() {
        android.util.Log.i("ProductFragment", "Model retraining needed - collecting purchase sets...")
        
        try {
            // Debug: Check all records in database first
            debugCheckAllRecords()
            
            // Collect purchase sets from recent history (last 7 days)
            val newSetsCount = collectPurchaseSetsUseCase(7)
            android.util.Log.i("ProductFragment", "Collected $newSetsCount new purchase sets")
            
            // Print all purchase sets table to logcat (simplified, without product names)
            printPurchaseSetsTableSimplified()
            
            // Get pending sets for upload
            val pendingSets = purchaseSetRepository.getPendingUploadSets()
            if (pendingSets.isNotEmpty()) {
                android.util.Log.i("ProductFragment", "Found ${pendingSets.size} pending purchase sets for upload")
                
                // Upload to model (placeholder implementation)
                val uploadSuccess = modelTrainingDataUploader.uploadPurchaseSets(pendingSets)
                
                if (uploadSuccess) {
                    // Mark sets as uploaded
                    pendingSets.forEach { set ->
                        purchaseSetRepository.markAsUploaded(set.id)
                    }
                    android.util.Log.i("ProductFragment", "Successfully uploaded ${pendingSets.size} purchase sets to model")
                } else {
                    android.util.Log.w("ProductFragment", "Failed to upload purchase sets to model")
                }
            } else {
                android.util.Log.i("ProductFragment", "No pending purchase sets to upload")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            android.util.Log.w("ProductFragment", "Retrain handling was cancelled")
            // Don't log as error, cancellation is expected in some cases
        } catch (e: Exception) {
            android.util.Log.e("ProductFragment", "Error handling retrain needed", e)
        }
    }
    
    /**
     * Debug: Check all records in database to verify data exists
     * Simplified version that doesn't depend on Fragment state
     */
    private suspend fun debugCheckAllRecords() {
        try {
            val allRecords = recordRepository.getAll()
            
            android.util.Log.i("DebugRecords", "=".repeat(80))
            android.util.Log.i("DebugRecords", "ALL RECORDS IN DATABASE (Total: ${allRecords.size})")
            android.util.Log.i("DebugRecords", "=".repeat(80))
            
            if (allRecords.isEmpty()) {
                android.util.Log.w("DebugRecords", "WARNING: No records found in database!")
                return
            }
            
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            
            // Log records with productId only (don't fetch product name to avoid Fragment dependency)
            allRecords.forEach { record ->
                val dateStr = dateFormat.format(record.date)
                android.util.Log.i("DebugRecords", "Record ID: ${record.id} | ProductID: ${record.productId} | Date: $dateStr | Stock: ${record.stock} | Shop: ${record.shop}")
            }
            
            val stockTrueCount = allRecords.count { it.stock }
            val stockFalseCount = allRecords.count { !it.stock }
            android.util.Log.i("DebugRecords", "Summary: stock=true: $stockTrueCount, stock=false: $stockFalseCount")
            android.util.Log.i("DebugRecords", "=".repeat(80))
            
        } catch (e: kotlinx.coroutines.CancellationException) {
            android.util.Log.w("DebugRecords", "Record check was cancelled")
            throw e // Re-throw cancellation to respect coroutine cancellation
        } catch (e: Exception) {
            android.util.Log.e("DebugRecords", "Error checking records", e)
        }
    }
    
    /**
     * Print all purchase sets in a formatted table to logcat (simplified version)
     * Uses product IDs instead of names to avoid Fragment dependency
     */
    private suspend fun printPurchaseSetsTableSimplified() {
        try {
            val allSets = purchaseSetRepository.getAll()
            
            if (allSets.isEmpty()) {
                android.util.Log.i("PurchaseSetTable", "=".repeat(80))
                android.util.Log.i("PurchaseSetTable", "PURCHASE SETS TABLE - EMPTY")
                android.util.Log.i("PurchaseSetTable", "=".repeat(80))
                return
            }
            
            // Print table header
            android.util.Log.i("PurchaseSetTable", "=".repeat(80))
            android.util.Log.i("PurchaseSetTable", "PURCHASE SETS TABLE (Total: ${allSets.size})")
            android.util.Log.i("PurchaseSetTable", "=".repeat(80))
            android.util.Log.i("PurchaseSetTable", String.format("%-5s | %-50s | %-20s | %-8s", "ID", "Product IDs", "Time Window", "Uploaded"))
            android.util.Log.i("PurchaseSetTable", "-".repeat(80))
            
            // Print each purchase set
            allSets.forEach { set ->
                // Format product IDs as sorted list
                val productIdsStr = set.productIds.sorted().joinToString(", ")
                val productsDisplay = if (productIdsStr.length > 50) {
                    productIdsStr.take(47) + "..."
                } else {
                    productIdsStr
                }
                
                // Format time window
                val timeFormat = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
                val timeWindow = "${timeFormat.format(set.startTime)} - ${timeFormat.format(set.endTime)}"
                
                // Format uploaded status
                val uploadedStatus = if (set.uploadedToModel) "Yes" else "No"
                
                android.util.Log.i("PurchaseSetTable", String.format("%-5d | %-50s | %-20s | %-8s", 
                    set.id, productsDisplay, timeWindow, uploadedStatus))
            }
            
            android.util.Log.i("PurchaseSetTable", "=".repeat(80))
            
            // Print summary statistics
            val pendingCount = allSets.count { !it.uploadedToModel }
            val uploadedCount = allSets.count { it.uploadedToModel }
            android.util.Log.i("PurchaseSetTable", "Summary: Pending=$pendingCount, Uploaded=$uploadedCount")
            android.util.Log.i("PurchaseSetTable", "=".repeat(80))
            
        } catch (e: Exception) {
            android.util.Log.e("PurchaseSetTable", "Error printing purchase sets table", e)
        }
    }
    
    private suspend fun addProductToNeededList(product: Product, locationId: Int?) {
        try {
            if (locationId == null) return
            
            val defaultAisle = getDefaultAisleForLocationUseCase(locationId) ?: return
            
            // If product doesn't exist, create it as a new product
            if (product.id == 0) {
                // Create new product with inStock = false (needed)
                // AddProductUseCase will automatically add it to the defaultAisle
                addProductUseCase(
                    Product(
                        id = 0,
                        name = product.name,
                        inStock = false, // Create as needed
                        qtyNeeded = 0,
                        price = 0.0
                    ),
                    defaultAisle
                )
                // Product is now created and added to needed list
            } else {
                // Product exists, get it
                val actualProduct = getProductUseCase(product.id) ?: return
                
                // Get all aisles where this product exists
                val productAisles = aisleProductRepository.getProductAisles(actualProduct.id)
                
                // Check if product is already in any aisle of the current location
                val isInCurrentLocation = productAisles.any { ap ->
                    val aisle = getAisleUseCase(ap.aisleId)
                    aisle?.locationId == locationId
                }
                
                // If not in current location, add to default aisle
                if (!isInCurrentLocation) {
                    val isInDefaultAisle = productAisles.any { it.aisleId == defaultAisle.id }
                    if (!isInDefaultAisle) {
                        val maxRank = getAisleMaxRankUseCase(defaultAisle)
                        addAisleProductsUseCase(
                            listOf(
                                AisleProduct(
                                    aisleId = defaultAisle.id,
                                    product = actualProduct,
                                    rank = maxRank + 1,
                                    id = 0
                                )
                            )
                        )
                    }
                }
                
                // Update product status to needed (inStock = false)
                updateProductStatusUseCase(actualProduct.id, false)
            }
        } catch (e: Exception) {
            // Handle error silently or show a message
            e.printStackTrace()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val menuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // Mark fragment as destroyed but don't cancel background tasks yet
        // They may still be processing important data
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isFragmentDestroyed = true
        // Don't cancel background scope immediately - let tasks complete
        // The scope uses SupervisorJob so it won't be cancelled by child failures
        // It will be cleaned up when Fragment is garbage collected
    }

    override fun onResume() {
        super.onResume()
        applicationTitleUpdateListener.applicationTitleUpdated(requireActivity(), appTitle)

        val edtProductName = binding.edtProductName
        edtProductName.doOnLayout {
            edtProductName.requestFocus()
            val imm =
                ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
            imm?.showSoftInput(edtProductName, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(
            name: String?,
            inStock: Boolean,
            addEditFragmentListener: AddEditFragmentListener,
            applicationTitleUpdateListener: ApplicationTitleUpdateListener,
            fabHandler: FabHandler
        ) =
            ProductFragment(
                addEditFragmentListener, applicationTitleUpdateListener, fabHandler
            ).apply {
                arguments = Bundler().makeAddProductBundle(name, inStock)
            }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.add_edit_fragment_main, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.mnu_btn_save -> {
                productViewModel.saveProduct()
                true
            }

            else -> false
        }
    }
}