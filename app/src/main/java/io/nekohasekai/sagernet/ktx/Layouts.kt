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

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.FabStyle
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.utils.Theme

fun RecyclerView.applyGlassBlur() {
    for (i in itemDecorationCount - 1 downTo 0) {
        val deco = getItemDecorationAt(i)
        if (deco::class.java.name.contains("Divider", ignoreCase = true)) {
            removeItemDecoration(deco)
        }
    }
    if (getTag(R.id.tag_custom_style) != true) {
        setTag(R.id.tag_custom_style, true)
        addItemDecoration(FrostedCardItemDecoration())
        addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                applyFrostedStyle(view)
            }
            override fun onChildViewDetachedFromWindow(view: View) {}
        })
    }
    for (i in 0 until childCount) {
        applyFrostedStyle(getChildAt(i))
    }
}

private data class PreferenceItemRun(
    val isCategory: Boolean,
    val isFirst: Boolean,
    val isLast: Boolean
)

@SuppressLint("RestrictedApi")
private fun getPreferenceItemRun(adapter: androidx.preference.PreferenceGroupAdapter, position: Int): PreferenceItemRun {
    try {
        val count = adapter.itemCount
        if (position !in 0 until count) {
            return PreferenceItemRun(isCategory = false, isFirst = false, isLast = false)
        }
        val pref = runCatching { adapter.getItem(position) }.getOrNull()
        if (pref == null || pref is androidx.preference.PreferenceCategory) {
            return PreferenceItemRun(isCategory = true, isFirst = false, isLast = false)
        }
        val parent = pref.parent
        val prevPref = if (position > 0) runCatching { adapter.getItem(position - 1) }.getOrNull() else null
        val isFirst = position == 0 || prevPref == null || prevPref is androidx.preference.PreferenceCategory || prevPref.parent != parent

        val nextPref = if (position + 1 < count) runCatching { adapter.getItem(position + 1) }.getOrNull() else null
        val isLast = position == count - 1 || nextPref == null || nextPref is androidx.preference.PreferenceCategory || nextPref.parent != parent

        return PreferenceItemRun(isCategory = false, isFirst = isFirst, isLast = isLast)
    } catch (_: Exception) {
        return PreferenceItemRun(isCategory = false, isFirst = false, isLast = false)
    }
}

@SuppressLint("RestrictedApi")
private class FrostedCardItemDecoration : RecyclerView.ItemDecoration() {
    private val cornerRadius = dp2px(16).toFloat()
    private val strokeWidth = dp2px(1).toFloat()
    private val hMargin = dp2px(16)
    private val rect = RectF()
    private val rectCorner = RectF()
    private val itemPath = Path()
    private val strokePath = Path()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.strokeWidth = this@FrostedCardItemDecoration.strokeWidth
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.strokeWidth = dp2px(1).toFloat()
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        try {
            if (view is com.google.android.material.card.MaterialCardView) {
                outRect.set(0, 0, 0, 0)
                return
            }
            val adapter = parent.adapter as? androidx.preference.PreferenceGroupAdapter
            if (adapter != null) {
                val position = parent.getChildAdapterPosition(view)
                val count = adapter.itemCount
                if (position !in 0 until count) {
                    outRect.set(hMargin, 0, hMargin, 0)
                    return
                }
                val pref = runCatching { adapter.getItem(position) }.getOrNull()
                if (pref is androidx.preference.PreferenceCategory) {
                    outRect.set(hMargin, dp2px(6), hMargin, dp2px(2))
                    return
                }
                val run = getPreferenceItemRun(adapter, position)
                val topOffset = if (run.isFirst) dp2px(4) else 0
                val bottomOffset = if (run.isLast) dp2px(4) else 0
                outRect.set(hMargin, topOffset, hMargin, bottomOffset)
            } else {
                outRect.set(hMargin, dp2px(2), hMargin, dp2px(2))
            }
        } catch (_: Exception) {
            outRect.set(hMargin, 0, hMargin, 0)
        }
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        try {
            val context = parent.context
            // 经典主题与 Cupertino 主题均应用苹果级通透高斯磨砂玻璃质感与高光描边
            fillPaint.color = ContextCompat.getColor(context, R.color.surface_glass)
            strokePaint.color = ContextCompat.getColor(context, R.color.card_stroke)
            dividerPaint.color = if (Theme.usingNightMode()) 0x26FFFFFF.toInt() else 0x14000000.toInt()

            val adapter = parent.adapter as? androidx.preference.PreferenceGroupAdapter
            val childCount = parent.childCount
            for (i in 0 until childCount) {
                val child = parent.getChildAt(i) ?: continue
                if (child.visibility != View.VISIBLE) continue
                if (child is com.google.android.material.card.MaterialCardView) continue

                val tx = child.translationX
                val ty = child.translationY
                val L = child.left + tx
                val T = child.top + ty
                val R = child.right + tx
                val B = child.bottom + ty
                if (R <= L || B <= T) continue
                rect.set(L, T, R, B)

                if (adapter != null) {
                    val position = parent.getChildAdapterPosition(child)
                    val count = adapter.itemCount
                    if (position !in 0 until count) continue
                    val pref = runCatching { adapter.getItem(position) }.getOrNull()
                    if (pref is androidx.preference.PreferenceCategory) {
                        continue
                    }
                    val run = getPreferenceItemRun(adapter, position)
                    val r = minOf(cornerRadius, (R - L) / 2f, (B - T) / 2f).coerceAtLeast(0f)

                    if (run.isFirst && run.isLast) {
                        c.drawRoundRect(rect, r, r, fillPaint)
                        c.drawRoundRect(rect, r, r, strokePaint)
                    } else if (run.isFirst) {
                        itemPath.reset()
                        val radii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                        itemPath.addRoundRect(rect, radii, Path.Direction.CW)
                        c.drawPath(itemPath, fillPaint)

                        strokePath.reset()
                        strokePath.moveTo(L, B)
                        strokePath.lineTo(L, T + r)
                        if (r > 0f) {
                            rectCorner.set(L, T, L + 2 * r, T + 2 * r)
                            strokePath.arcTo(rectCorner, 180f, 90f, false)
                            strokePath.lineTo(R - r, T)
                            rectCorner.set(R - 2 * r, T, R, T + 2 * r)
                            strokePath.arcTo(rectCorner, 270f, 90f, false)
                        } else {
                            strokePath.lineTo(R, T)
                        }
                        strokePath.lineTo(R, B)
                        c.drawPath(strokePath, strokePaint)

                        val titleView = child.findViewById<View>(android.R.id.title)
                        val divL = if (titleView != null && titleView.left > 0) {
                            (titleView.left + tx).coerceAtLeast(L + dp2px(16))
                        } else {
                            L + dp2px(56)
                        }
                        val safeDivL = divL.coerceIn(L, R)
                        c.drawLine(safeDivL, B, R, B, dividerPaint)
                    } else if (run.isLast) {
                        itemPath.reset()
                        val radii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
                        itemPath.addRoundRect(rect, radii, Path.Direction.CW)
                        c.drawPath(itemPath, fillPaint)

                        strokePath.reset()
                        strokePath.moveTo(L, T)
                        strokePath.lineTo(L, B - r)
                        if (r > 0f) {
                            rectCorner.set(L, B - 2 * r, L + 2 * r, B)
                            strokePath.arcTo(rectCorner, 180f, -90f, false)
                            strokePath.lineTo(R - r, B)
                            rectCorner.set(R - 2 * r, B - 2 * r, R, B)
                            strokePath.arcTo(rectCorner, 90f, -90f, false)
                        } else {
                            strokePath.lineTo(R, B)
                        }
                        strokePath.lineTo(R, T)
                        c.drawPath(strokePath, strokePaint)
                    } else {
                        c.drawRect(rect, fillPaint)
                        c.drawLine(L, T, L, B, strokePaint)
                        c.drawLine(R, T, R, B, strokePaint)

                        val titleView = child.findViewById<View>(android.R.id.title)
                        val divL = if (titleView != null && titleView.left > 0) {
                            (titleView.left + tx).coerceAtLeast(L + dp2px(16))
                        } else {
                            L + dp2px(56)
                        }
                        val safeDivL = divL.coerceIn(L, R)
                        c.drawLine(safeDivL, B, R, B, dividerPaint)
                    }
                } else {
                    c.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
                    c.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
                }
            }
        } catch (_: Exception) {}
    }
}

@SuppressLint("RestrictedApi")
private fun applyFrostedStyle(view: View?, isDirectItem: Boolean = true) {
    if (view == null) return
    val parent = view.parent
    if (parent is RecyclerView) {
        val ad = parent.adapter as? androidx.preference.PreferenceGroupAdapter
        val p = parent.getChildAdapterPosition(view)
        if (ad != null && p in 0 until ad.itemCount) {
            val pref = runCatching { ad.getItem(p) }.getOrNull()
            if (pref is androidx.preference.PreferenceCategory) {
                view.setPadding(view.paddingLeft, dp2px(6), view.paddingRight, dp2px(2))
                (view.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { lp ->
                    lp.topMargin = 0
                    lp.bottomMargin = 0
                }
                if (view is android.view.ViewGroup) {
                    val titleView = view.findViewById<View>(android.R.id.title)
                    if (titleView != null && titleView != view) {
                        titleView.setPadding(titleView.paddingLeft, 0, titleView.paddingRight, 0)
                        (titleView.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { tlp ->
                            tlp.topMargin = 0
                            tlp.bottomMargin = 0
                        }
                    }
                }
                return
            }
        }
    }
    if (view is com.google.android.material.card.MaterialCardView) {
        if (view.getTag(R.id.tag_custom_style) == true) return
        view.setTag(R.id.tag_custom_style, true)
        view.setCardBackgroundColor(ContextCompat.getColor(view.context, R.color.surface_glass))
        view.strokeColor = ContextCompat.getColor(view.context, R.color.card_stroke)
        view.strokeWidth = dp2px(1) 
        view.radius = dp2px(24).toFloat()
        view.cardElevation = 0f
        view.maxCardElevation = 0f
        view.preventCornerOverlap = false
        view.useCompatPadding = false
        view.clipToOutline = true
        view.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: android.view.View, outline: android.graphics.Outline) {
                val w = v.width
                val h = v.height
                if (w <= 0 || h <= 0) return
                try {
                    val r = minOf(dp2px(24).toFloat(), w / 2f, h / 2f).coerceAtLeast(0f)
                    outline.setRoundRect(0, 0, w, h, r)
                } catch (_: Exception) {
                    try {
                        outline.setRect(0, 0, w, h)
                    } catch (_: Exception) {}
                }
            }
        }
    } else if (view is android.view.ViewGroup) {
        val parent = view.parent
        if (parent is RecyclerView) {
            view.clipToOutline = true
            view.outlineProvider = object : android.view.ViewOutlineProvider() {
                @SuppressLint("RestrictedApi")
                override fun getOutline(v: android.view.View, outline: android.graphics.Outline) {
                    val w = v.width
                    val h = v.height
                    if (w <= 0 || h <= 0) return
                    try {
                        val rv = v.parent as? RecyclerView
                        val ad = rv?.adapter as? androidx.preference.PreferenceGroupAdapter
                        val p = if (rv != null) rv.getChildAdapterPosition(v) else RecyclerView.NO_POSITION
                        val maxR = dp2px(16).toFloat()
                        val r = minOf(maxR, w / 2f, h / 2f).coerceAtLeast(0f)
                        if (ad != null && p in 0 until ad.itemCount) {
                            val run = getPreferenceItemRun(ad, p)
                            if (run.isCategory) {
                                outline.setRect(0, 0, w, h)
                            } else if (run.isFirst && run.isLast) {
                                outline.setRoundRect(0, 0, w, h, r)
                            } else if (run.isFirst) {
                                outline.setRoundRect(0, 0, w, h + r.toInt(), r)
                            } else if (run.isLast) {
                                outline.setRoundRect(0, -r.toInt(), w, h, r)
                            } else {
                                outline.setRect(0, 0, w, h)
                            }
                        } else {
                            outline.setRoundRect(0, 0, w, h, r)
                        }
                    } catch (_: Exception) {
                        try {
                            outline.setRect(0, 0, w, h)
                        } catch (_: Exception) {}
                    }
                }
            }
        } else if (isDirectItem && view.getTag(R.id.tag_custom_style) != true) {
            view.setTag(R.id.tag_custom_style, true)
            val ctx = view.context
            val glassDrawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(ContextCompat.getColor(ctx, R.color.surface_glass))
                cornerRadius = dp2px(16).toFloat()
                setStroke(dp2px(1), ContextCompat.getColor(ctx, R.color.card_stroke))
            }
            view.background = glassDrawable
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
        } catch (_: Exception) {
        }
    }

    override fun scrollVerticallyBy(
        dy: Int, recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        val scrollRange = try {
            super.scrollVerticallyBy(dy, recycler, state)
        } catch (_: Exception) {
            0
        }
        try {
            val activity = recyclerView.context as? MainActivity
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                if (dy > 6) {
                    activity.binding.cupertinoDock.hidePanel()
                } else if (dy < -6) {
                    activity.binding.cupertinoDock.showPanel()
                }
            }
        } catch (_: Exception) {}
        return scrollRange
    }

}

class FixedGridLayoutManager(val recyclerView: RecyclerView, spanCount: Int) :
    GridLayoutManager(recyclerView.context, spanCount) {

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (_: Exception) {
        }
    }

    override fun supportsPredictiveItemAnimations(): Boolean {
        return false
    }

    override fun scrollVerticallyBy(
        dy: Int, recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        val scrollRange = try {
            super.scrollVerticallyBy(dy, recycler, state)
        } catch (_: Exception) {
            0
        }
        try {
            val activity = recyclerView.context as? MainActivity
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                if (dy > 6) {
                    activity.binding.cupertinoDock.hidePanel()
                } else if (dy < -6) {
                    activity.binding.cupertinoDock.showPanel()
                }
            }
        } catch (_: Exception) {}
        return scrollRange
    }

}
