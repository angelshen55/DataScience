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

package com.aisleron.ui.receipt

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.aisleron.AppCompatActivityTestImpl
import com.aisleron.R
import com.aisleron.domain.receipt.ReceiptItem
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.Matcher
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class ReceiptPreviewDialogTest {

    @JvmField
    @Rule
    val activityScenarioRule = ActivityScenarioRule(AppCompatActivityTestImpl::class.java)

    private fun showDialog(items: List<ReceiptItem>, onConfirm: (List<ReceiptItem>) -> Unit = {}, onCancel: () -> Unit = {}) {
        val dialog = ReceiptPreviewDialog(
            initialItems = items,
            onCancelImport = onCancel,
            onConfirmImport = onConfirm
        )
        activityScenarioRule.scenario.onActivity {
            dialog.show(it.supportFragmentManager, "receiptPreview")
        }
        onView(withId(R.id.tv_summary))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    private fun clickChildViewWithId(id: Int): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = ViewMatchers.isAssignableFrom(View::class.java)
            override fun getDescription(): String = "Click on a child view with specified id."
            override fun perform(uiController: UiController, view: View) {
                val target = view.findViewById<View>(id)
                target.performClick()
                uiController.loopMainThreadUntilIdle()
            }
        }
    }

    private fun replaceTextInChildView(childId: Int, text: String): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = ViewMatchers.isAssignableFrom(ViewGroup::class.java)
            override fun getDescription(): String = "Replace text in a child EditText with specified id."
            override fun perform(uiController: UiController, view: View) {
                val editText = view.findViewById<View>(childId) as EditText
                replaceText(text).perform(uiController, editText)
                uiController.loopMainThreadUntilIdle()
            }
        }
    }

    private fun pressImeActionInChild(childId: Int): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = ViewMatchers.isAssignableFrom(ViewGroup::class.java)
            override fun getDescription(): String = "Press IME action button in a child EditText."
            override fun perform(uiController: UiController, view: View) {
                val editText = view.findViewById<View>(childId) as EditText
                pressImeActionButton().perform(uiController, editText)
                uiController.loopMainThreadUntilIdle()
            }
        }
    }

    @Test
    fun deleteItem_UpdatesSummaryCountsAndTotal() {
        val items = listOf(
            ReceiptItem(name = "Apple", unitPrice = BigDecimal("10"), quantity = 1.0),
            ReceiptItem(name = "Banana", unitPrice = BigDecimal("5"), quantity = 2.0)
        )

        showDialog(items)

        onView(withId(R.id.tv_summary))
            .inRoot(isDialog())
            .check(matches(withText(allOf(containsString("Items: 2"), containsString("Total: 20.0")))))

        onView(withId(R.id.rv_items))
            .inRoot(isDialog())
            .perform(
                RecyclerViewActions.actionOnItemAtPosition<androidx.recyclerview.widget.RecyclerView.ViewHolder>(
                    0,
                    clickChildViewWithId(R.id.btn_delete)
                )
            )

        onView(withId(R.id.tv_summary))
            .inRoot(isDialog())
            .check(matches(withText(allOf(containsString("Items: 1"), containsString("Total: 10.0")))))
    }

    @Test
    fun updateQuantityAndCommit_UpdatesSummaryTotal() {
        val items = listOf(
            ReceiptItem(name = "Apple", unitPrice = BigDecimal("10"), quantity = 1.0),
            ReceiptItem(name = "Banana", unitPrice = BigDecimal("5"), quantity = 2.0)
        )

        showDialog(items)

        onView(withId(R.id.rv_items))
            .inRoot(isDialog())
            .perform(
                RecyclerViewActions.actionOnItemAtPosition<androidx.recyclerview.widget.RecyclerView.ViewHolder>(
                    1,
                    replaceTextInChildView(R.id.et_qty, "3")
                )
            )
            .perform(
                RecyclerViewActions.actionOnItemAtPosition<androidx.recyclerview.widget.RecyclerView.ViewHolder>(
                    1,
                    pressImeActionInChild(R.id.et_qty)
                )
            )

        onView(withId(R.id.tv_summary))
            .inRoot(isDialog())
            .check(matches(withText(allOf(containsString("Items: 2"), containsString("Total: 25.0")))))
    }
}

