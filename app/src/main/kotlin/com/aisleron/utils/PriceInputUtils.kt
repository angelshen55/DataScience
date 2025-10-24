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

package com.aisleron.utils

import android.view.KeyEvent
import android.view.View
import android.widget.EditText

object PriceInputUtils {
    
    /**
     * Sets the text of a price EditText without changing cursor position.
     * The cursor will remain where the user placed it.
     * 
     * @param editText The EditText to set the price text
     * @param price The price value to set
     */
    fun setPriceText(editText: EditText, price: Double) {
        val priceText = String.format("%.2f", price)
        editText.setText(priceText)
    }
    
    /**
     * Sets the text of a price EditText without changing cursor position.
     * The cursor will remain where the user placed it.
     * 
     * @param editText The EditText to set the price text
     * @param priceText The price text to set
     */
    fun setPriceTextOnly(editText: EditText, priceText: String) {
        editText.setText(priceText)
    }
    
    /**
     * Sets up Enter key listener for price input that triggers the provided callback.
     * Only when Enter is pressed will the price update be processed.
     * 
     * @param editText The EditText to set up the Enter key listener
     * @param onEnterPressed Callback to execute when Enter is pressed
     */
    fun setupPriceEnterListener(editText: EditText, onEnterPressed: () -> Unit) {
        editText.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                onEnterPressed()
                true
            } else {
                false
            }
        }
    }
    
    /**
     * Sets up Enter key listener for price input that triggers the provided callback.
     * Also handles IME action for soft keyboards.
     * 
     * @param editText The EditText to set up the Enter key listener
     * @param onEnterPressed Callback to execute when Enter is pressed
     */
    fun setupPriceEnterListenerWithIme(editText: EditText, onEnterPressed: () -> Unit) {
        // Handle hardware Enter key
        editText.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                onEnterPressed()
                true
            } else {
                false
            }
        }
        
        // Handle IME action (soft keyboard Enter)
        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) {
                onEnterPressed()
                true
            } else {
                false
            }
        }
    }
}
