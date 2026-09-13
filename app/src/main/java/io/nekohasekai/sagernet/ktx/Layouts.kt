/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
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

package io.nekohasekai.sagernet.ktx

import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.FabStyle
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.MainActivity

fun RecyclerView.applyGlassBlur() {
    // 动态应用玻璃质感：监听 View 挂载并注入样式
    addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
        override fun onChildViewAttachedToWindow(view: View) {
            applyFrostedStyle(view)
        }
        override fun onChildViewDetachedFromWindow(view: View) {}
    })
}

private fun applyFrostedStyle(view: View) {
    if (view is com.google.android.material.card.MaterialCardView) {
        // 强制应用半透明背景、精致描边和圆角，并去掉阴影以保持玻璃通透感
        view.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(view.context, R.color.surface_glass))
        view.strokeColor = androidx.core.content.ContextCompat.getColor(view.context, R.color.card_stroke)
        view.strokeWidth = dp2px(1) 
        view.radius = dp2px(28).toFloat()
        view.cardElevation = 0f
    } else if (view is android.view.ViewGroup) {
        for (i in 0 until view.childCount) {
            applyFrostedStyle(view.getChildAt(i))
        }
    }
}

fun View.applyGlassBlur() {
    applyFrostedStyle(this)
}

class FixedLinearLayoutManager(val recyclerView: RecyclerView) :
    LinearLayoutManager(recyclerView.context, RecyclerView.VERTICAL, false) {

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (ignored: IndexOutOfBoundsException) {
        }
    }

    private var listenerDisabled = false

    override fun scrollVerticallyBy(
        dx: Int, recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        return super.scrollVerticallyBy(dx, recycler, state)
    }

}

class FixedGridLayoutManager(val recyclerView: RecyclerView, spanCount: Int) :
    GridLayoutManager(recyclerView.context, spanCount) {

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (ignored: IndexOutOfBoundsException) {
        }
    }

    private var listenerDisabled = false

    override fun supportsPredictiveItemAnimations(): Boolean {
        return false
    }

    override fun scrollVerticallyBy(
        dx: Int, recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        return super.scrollVerticallyBy(dx, recycler, state)
    }

}
