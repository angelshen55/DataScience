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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aisleron.R
import com.aisleron.domain.product.Product

class RecommendationProductAdapter(
    private var products: List<Product>,
    private var isInNeededListFlags: List<Boolean>,
    private val listener: RecommendationProductListener
) : RecyclerView.Adapter<RecommendationProductAdapter.RecommendationProductViewHolder>() {

    interface RecommendationProductListener {
        fun onAddToListClicked(product: Product)
    }

    fun updateProducts(newProducts: List<Product>, newIsInNeededListFlags: List<Boolean>) {
        products = newProducts
        isInNeededListFlags = newIsInNeededListFlags
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecommendationProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recommendation, parent, false)
        return RecommendationProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecommendationProductViewHolder, position: Int) {
        holder.bind(products[position], isInNeededListFlags[position])
    }

    override fun getItemCount(): Int = products.size

    inner class RecommendationProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.txt_recommendation_name)
        private val detailsTextView: TextView = itemView.findViewById(R.id.txt_recommendation_details)
        private val addButton: Button = itemView.findViewById(R.id.btn_add_to_list)

        fun bind(product: Product, isInNeededList: Boolean) {
            nameTextView.text = product.name
            detailsTextView.text = "" // No details for now, just product name
            
            // Enable/disable button based on whether product is already in needed list
            addButton.visibility = View.VISIBLE
            if (isInNeededList) {
                // Product is already in needed list, disable button
                addButton.isEnabled = false
                addButton.alpha = 0.5f // Make it look disabled
            } else {
                // Product is not in needed list, enable button
                addButton.isEnabled = true
                addButton.alpha = 1.0f
                addButton.setOnClickListener {
                    listener.onAddToListClicked(product)
                }
            }
            addButton.text = itemView.context.getString(R.string.add_to_list)
        }
    }
}

