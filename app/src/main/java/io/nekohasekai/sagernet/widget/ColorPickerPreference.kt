/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 Matsuri authors                                         *
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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.res.TypedArrayUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.setPadding
import androidx.core.widget.NestedScrollView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.applyGlassBlur
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.utils.WallpaperManager
import kotlin.math.roundToInt

@SuppressLint("RestrictedApi")
class ColorPickerPreference
@JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = TypedArrayUtils.getAttr(
        context,
        androidx.preference.R.attr.editTextPreferenceStyle,
        android.R.attr.editTextPreferenceStyle
    )
) : Preference(
    context, attrs, defStyle
) {

    var inited = false
    var onPickWallpaper: (() -> Unit)? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val widgetFrame = holder.findViewById(android.R.id.widget_frame) as LinearLayout

        if (!inited) {
            inited = true

            var color = context.getColorAttr(android.R.attr.colorPrimary)
            if (color == ContextCompat.getColor(context, R.color.white)) {
                color = ContextCompat.getColor(context, R.color.material_light_black)
            }

            widgetFrame.addView(
                getImageViewAtColor(
                    color,
                    36,
                    0
                )
            )
            widgetFrame.visibility = View.VISIBLE
        }
    }

    fun getImageViewAtColor(color: Int, sizeDp: Int, paddingDp: Int): ImageView {
        val factor = context.resources.displayMetrics.density
        val size = (sizeDp * factor).roundToInt()
        val paddingSize = (paddingDp * factor).roundToInt()

        return ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
            setPadding(paddingSize)
            setImageDrawable(getColor(resources, color))
        }
    }

    fun getColor(res: Resources, color: Int): Drawable {
        val drawable = ResourcesCompat.getDrawable(
            res,
            R.drawable.ic_baseline_fiber_manual_record_24,
            null
        )!!
        DrawableCompat.setTint(drawable.mutate(), color)
        return drawable
    }

    override fun onClick() {
        super.onClick()

        lateinit var dialog: AlertDialog

        fun addColorsToLayout(layout: FlexboxLayout, arrayRes: Int, idOffset: Int) {
            val colors = context.resources.getIntArray(arrayRes)
            for ((i, color) in colors.withIndex()) {
                val view = getImageViewAtColor(color, 64, 0).apply {
                    setOnClickListener {
                        persistInt(i + idOffset)
                        dialog.dismiss()
                        callChangeListener(i + idOffset)
                    }
                }
                layout.addView(view)
            }
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // --- 1. Wallpaper Theme Section ---
            addView(TextView(context).apply {
                text = context.getString(R.string.theme_wallpaper_category)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, dp2px(8))
            })

            // Switch Card
            val switchCard = MaterialCardView(context).apply {
                radius = dp2px(16).toFloat()
                cardElevation = 0f
                maxCardElevation = 0f
                strokeWidth = dp2px(1)
                strokeColor = Theme.getCardStrokeColor(context)
                setCardBackgroundColor(Theme.getCardBackgroundColor(context))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp2px(10)
                }
            }
            val switchRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp2px(14), dp2px(10), dp2px(14), dp2px(10))
            }
            val textLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val switchTitle = TextView(context).apply {
                text = context.getString(R.string.enable_wallpaper_theme)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            textLayout.addView(switchTitle)
            switchRow.addView(textLayout)

            val toggleSwitch = SwitchCompat(context).apply {
                isChecked = DataStore.wallpaperThemeEnabled
            }
            switchRow.addView(toggleSwitch)
            switchCard.addView(switchRow)
            addView(switchCard)

            // Horizontal Wallpapers Carousel
            val wallpaperScrollView = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
                setPadding(0, 0, 0, dp2px(16))
            }
            val wallpaperContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            wallpaperScrollView.addView(wallpaperContainer)
            addView(wallpaperScrollView)

            fun populateWallpapers() {
                wallpaperContainer.removeAllViews()

                // + Upload Wallpaper Button Card
                val uploadCard = MaterialCardView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp2px(85), dp2px(120)).apply {
                        marginEnd = dp2px(10)
                    }
                    radius = dp2px(14).toFloat()
                    cardElevation = 0f
                    maxCardElevation = 0f
                    strokeWidth = dp2px(1)
                    strokeColor = Theme.getCardStrokeColor(context)
                    setCardBackgroundColor(Theme.getCardBackgroundColor(context))

                    val uploadContent = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        addView(ImageView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(dp2px(30), dp2px(30))
                            setImageResource(R.drawable.ic_image_photo)
                            imageTintList = ColorStateList.valueOf(
                                context.getColorAttr(androidx.appcompat.R.attr.colorAccent)
                            )
                        })
                        addView(TextView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                topMargin = dp2px(6)
                            }
                            text = context.getString(R.string.upload_wallpaper)
                            textSize = 12f
                            setTextColor(context.getColorAttr(androidx.appcompat.R.attr.colorAccent))
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            gravity = Gravity.CENTER
                        })
                    }
                    addView(uploadContent)
                    setOnClickListener {
                        dialog.dismiss()
                        if (onPickWallpaper != null) {
                            onPickWallpaper?.invoke()
                        } else {
                            val act = context as? Activity
                            try {
                                val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
                                act?.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    }
                }
                wallpaperContainer.addView(uploadCard)

                // Wallpaper items
                val currentPath = DataStore.currentWallpaperPath
                val isThemeEnabled = DataStore.wallpaperThemeEnabled
                val wallpapers = WallpaperManager.getAllWallpapers(context)

                wallpapers.forEach { item ->
                    val isSelected = isThemeEnabled && (
                            currentPath == item.path || (currentPath.isEmpty() && item.path == WallpaperManager.PRESET_MESH)
                            )
                    val card = MaterialCardView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(dp2px(85), dp2px(120)).apply {
                            marginEnd = dp2px(10)
                        }
                        radius = dp2px(14).toFloat()
                        cardElevation = 0f
                        maxCardElevation = 0f
                        val accent = context.getColorAttr(androidx.appcompat.R.attr.colorAccent)
                        if (isSelected) {
                            strokeWidth = dp2px(2)
                            strokeColor = accent
                        } else {
                            strokeWidth = dp2px(1)
                            strokeColor = Theme.getCardStrokeColor(context)
                        }
                        setCardBackgroundColor(Theme.getCardBackgroundColor(context))
                    }

                    val frame = FrameLayout(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }

                    val imageView = ImageView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        val thumb = WallpaperManager.getThumbnail(context, item, dp2px(85), dp2px(120))
                        if (thumb != null) {
                            setImageDrawable(thumb)
                        } else {
                            setBackgroundColor(0xFF222222.toInt())
                        }
                    }
                    frame.addView(imageView)

                    // Scrim at bottom
                    val scrimView = View(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp2px(34),
                            Gravity.BOTTOM
                        )
                        background = GradientDrawable(
                            GradientDrawable.Orientation.BOTTOM_TOP,
                            intArrayOf(0xCC000000.toInt(), 0x00000000)
                        )
                    }
                    frame.addView(scrimView)

                    // Wallpaper title
                    val titleView = TextView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                        ).apply {
                            setPadding(dp2px(4), 0, dp2px(4), dp2px(4))
                        }
                        text = item.name
                        textSize = 10f
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                        isSingleLine = true
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    frame.addView(titleView)

                    // Checkmark badge when active
                    if (isSelected) {
                        val badge = ImageView(context).apply {
                            layoutParams = FrameLayout.LayoutParams(dp2px(20), dp2px(20), Gravity.TOP or Gravity.START).apply {
                                setMargins(dp2px(4), dp2px(4), 0, 0)
                            }
                            setImageResource(R.drawable.ic_action_done)
                            imageTintList = ColorStateList.valueOf(Color.WHITE)
                            val accent = context.getColorAttr(androidx.appcompat.R.attr.colorAccent)
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(accent)
                            }
                            setPadding(dp2px(2))
                        }
                        frame.addView(badge)
                    }

                    // Delete button for custom uploaded wallpapers
                    if (!item.isPreset) {
                        val deleteBtn = ImageView(context).apply {
                            layoutParams = FrameLayout.LayoutParams(dp2px(20), dp2px(20), Gravity.TOP or Gravity.END).apply {
                                setMargins(0, dp2px(4), dp2px(4), 0)
                            }
                            setImageResource(R.drawable.ic_action_delete)
                            imageTintList = ColorStateList.valueOf(Color.WHITE)
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(0x99000000.toInt())
                            }
                            setPadding(dp2px(2))
                            setOnClickListener {
                                MaterialAlertDialogBuilder(context)
                                    .setTitle(R.string.delete_wallpaper_confirm)
                                    .setPositiveButton(android.R.string.ok) { _, _ ->
                                        WallpaperManager.deleteWallpaper(context, item.path)
                                        populateWallpapers()
                                        callChangeListener(DataStore.appTheme)
                                        (context as? Activity)?.let { act -> ActivityCompat.recreate(act) }
                                    }
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .show()
                            }
                        }
                        frame.addView(deleteBtn)
                    }

                    card.addView(frame)

                    card.setOnClickListener {
                        DataStore.wallpaperThemeEnabled = true
                        DataStore.currentWallpaperPath = item.path
                        dialog.dismiss()
                        callChangeListener(DataStore.appTheme)
                        (context as? Activity)?.let { act -> ActivityCompat.recreate(act) }
                    }

                    wallpaperContainer.addView(card)
                }
            }

            populateWallpapers()

            toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
                DataStore.wallpaperThemeEnabled = isChecked
                if (isChecked && DataStore.currentWallpaperPath.isEmpty()) {
                    DataStore.currentWallpaperPath = WallpaperManager.PRESET_MESH
                }
                populateWallpapers()
                dialog.dismiss()
                callChangeListener(DataStore.appTheme)
                (context as? Activity)?.let { act -> ActivityCompat.recreate(act) }
            }

            // --- 2. Cupertino Section ---
            addView(TextView(context).apply {
                text = context.getString(R.string.theme_cupertino_category)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, dp2px(8))
            })
            val cupertinoFlex = FlexboxLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                flexWrap = FlexWrap.WRAP
                justifyContent = JustifyContent.SPACE_BETWEEN
            }
            addColorsToLayout(cupertinoFlex, R.array.cupertino_colors, 101)
            addView(cupertinoFlex)

            // --- 3. Material Section ---
            addView(TextView(context).apply {
                text = context.getString(R.string.theme_material_category)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dp2px(16), 0, dp2px(8))
            })
            val materialFlex = FlexboxLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                flexWrap = FlexWrap.WRAP
                justifyContent = JustifyContent.SPACE_BETWEEN
            }
            addColorsToLayout(materialFlex, R.array.material_colors, 1)
            addView(materialFlex)
        }

        val scrollView = NestedScrollView(context).apply {
            setPadding(dp2px(16), dp2px(16), dp2px(16), dp2px(16))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(container)
        }

        dialog = MaterialAlertDialogBuilder(context).setTitle(title)
            .setView(scrollView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.applyGlassBlur()
        dialog.show()
    }
}
