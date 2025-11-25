package com.aisleron.ui.shoppinglist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aisleron.R
import com.aisleron.domain.product.ProductRecommendation
import java.text.DecimalFormat

class RecommendationRecyclerViewAdapter(
    private val recommendations: List<ProductRecommendation>,
    private val listener: RecommendationListener
) : RecyclerView.Adapter<RecommendationRecyclerViewAdapter.RecommendationViewHolder>() {

    interface RecommendationListener {
        fun onAddToListClicked(recommendation: ProductRecommendation)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecommendationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recommendation, parent, false)
        return RecommendationViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecommendationViewHolder, position: Int) {
        holder.bind(recommendations[position])
    }

    override fun getItemCount(): Int = recommendations.size

    inner class RecommendationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.txt_recommendation_name)
        private val detailsTextView: TextView = itemView.findViewById(R.id.txt_recommendation_details)
        private val addButton: Button = itemView.findViewById(R.id.btn_add_to_list)

        fun bind(recommendation: ProductRecommendation) {
            // Check if this is the "no recommendations" item
            if (recommendation.product.id == -1) {
                nameTextView.text = recommendation.product.name  // "No recommendations available"
                detailsTextView.text = "No products to recommend at this time"
                addButton.visibility = View.GONE  // Hide the add button
                // Don't set click listener for this item
            } else {
                nameTextView.text = recommendation.product.name
                
                // Format details text with purchase pattern information
                val df = DecimalFormat("#.#")
                val details = buildString {
                    append("Bought ${recommendation.purchaseCount} times")
                    append(" • Last bought ${recommendation.daysSinceLastPurchase} days ago")
                    if (recommendation.averagePurchaseInterval > 0) {
                        append(" • Avg. every ${df.format(recommendation.averagePurchaseInterval)} days")
                    }
                }
                detailsTextView.text = details
                
                addButton.visibility = View.VISIBLE  // Show the add button
                addButton.setOnClickListener {
                    listener.onAddToListClicked(recommendation)
                }
            }
        }
    }
}