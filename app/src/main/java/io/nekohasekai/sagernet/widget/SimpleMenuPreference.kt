/******************************************************************************
 *                                                                            *
 * Copyright (C) 2026  dyhkwong                                               *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.widget

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.core.view.setPadding
import androidx.preference.DropDownPreference
import androidx.preference.PreferenceViewHolder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr

open class SimpleMenuPreference
@JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.dropdownPreferenceStyle,
    defStyleRes: Int = 0
) : DropDownPreference(context, attrs, defStyleAttr, defStyleRes) {

    private lateinit var mAdapter: SimpleMenuAdapter

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val mSpinner = holder.itemView.findViewById<Spinner>(androidx.preference.R.id.spinner)
        mSpinner.layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
        mSpinner.setPadding(dp2px(2))
        mSpinner.setPopupBackgroundResource(R.drawable.bg_spinner_dropdown)

        val listener = mSpinner.onItemSelectedListener
        mSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                mAdapter.setSelectedItemPosition(position)
                listener?.onItemSelected(parent, view, position, id)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                listener?.onNothingSelected(parent)
            }

        }
    }

    override fun createAdapter(): ArrayAdapter<CharSequence?> {
        mAdapter = SimpleMenuAdapter(context, android.R.layout.simple_list_item_1)
        return mAdapter
    }

    private class SimpleMenuAdapter(context: Context, resource: Int) : ArrayAdapter<CharSequence?>(context, resource) {

        private var selectedItemPosition = -1

        private val radius = 12f * context.resources.displayMetrics.density
        private val selectedColor = context.getColorAttr(R.attr.colorMaterial100)

        private val topDrawable = GradientDrawable().apply {
            setColor(selectedColor)
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        }

        private val bottomDrawable = GradientDrawable().apply {
            setColor(selectedColor)
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
        }

        private val middleDrawable = GradientDrawable().apply {
            setColor(selectedColor)
        }

        private val singleDrawable = GradientDrawable().apply {
            setColor(selectedColor)
            cornerRadii = floatArrayOf(radius, radius, radius, radius, radius, radius, radius, radius)
        }

        fun setSelectedItemPosition(position: Int) {
            selectedItemPosition = position
            notifyDataSetChanged()
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getDropDownView(position, convertView, parent)
            if (position == selectedItemPosition) {
                view.background = when {
                    position == 0 && count == 1 -> singleDrawable
                    position == 0 -> topDrawable
                    position == count - 1 -> bottomDrawable
                    else -> middleDrawable
                }
            } else {
                view.background = null
            }
            return view
        }

    }

}