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
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.res.TypedArrayUtils
import androidx.core.graphics.ColorUtils
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
import io.nekohasekai.sagernet.ui.MainActivity
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

            val color = Theme.getAccentColor(context)

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
        val accent = Theme.getAccentColor(context)
        var currentSelectedTheme = DataStore.appTheme

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // =========================================================================
        // 1. Live Preview Card (FlClash ThemeLivePreview)
        // =========================================================================
        val previewCard = MaterialCardView(context).apply {
            radius = dp2px(20).toFloat()
            cardElevation = dp2px(2).toFloat()
            maxCardElevation = dp2px(4).toFloat()
            strokeWidth = dp2px(2)
            strokeColor = Theme.getCardStrokeColor(context)
            setCardBackgroundColor(Theme.getCardBackgroundColor(context))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp2px(125)
            ).apply {
                bottomMargin = dp2px(16)
            }
        }

        val previewFrameLayout = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val previewBgImage = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        previewFrameLayout.addView(previewBgImage)

        val previewScrim = View(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        previewFrameLayout.addView(previewScrim)

        // Mock Mini Floating Card
        val miniCard = MaterialCardView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            radius = dp2px(14).toFloat()
            cardElevation = 0f
            maxCardElevation = 0f
            strokeWidth = dp2px(1)
            strokeColor = Theme.getCardStrokeColor(context)
            setCardBackgroundColor(Theme.getCardBackgroundColor(context))
        }

        val miniCardContent = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp2px(14), dp2px(8), dp2px(14), dp2px(8))
        }

        val miniCardText = TextView(context).apply {
            text = "Exclave Liquid Glass"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (Theme.usingNightMode()) Color.WHITE else Color.BLACK)
        }
        miniCardContent.addView(miniCardText)

        val miniStatusChip = TextView(context).apply {
            text = "Active"
            textSize = 10f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp2px(6), dp2px(2), dp2px(6), dp2px(2))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp2px(8)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp2px(8).toFloat()
                setColor(accent)
            }
        }
        miniCardContent.addView(miniStatusChip)
        miniCard.addView(miniCardContent)
        previewFrameLayout.addView(miniCard)

        // Mode badge at top right
        val previewModeBadge = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                setMargins(0, dp2px(8), dp2px(8), 0)
            }
            textSize = 10f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp2px(8), dp2px(3), dp2px(8), dp2px(3))
            background = GradientDrawable().apply {
                cornerRadius = dp2px(10).toFloat()
                setColor(0x88000000.toInt())
                setStroke(dp2px(1), 0x33FFFFFF)
            }
        }
        previewFrameLayout.addView(previewModeBadge)

        previewCard.addView(previewFrameLayout)
        container.addView(previewCard)

        fun refreshLivePreview() {
            val isNight = Theme.usingNightMode()
            val isPureBlack = isNight && DataStore.amoledPureBlack
            val curAccent = Theme.getAccentColor(context)

            if (isPureBlack && !Theme.isWallpaperTheme()) {
                previewBgImage.setImageDrawable(null)
                previewBgImage.setBackgroundColor(Color.BLACK)
                previewScrim.setBackgroundColor(Color.TRANSPARENT)
            } else if (Theme.isWallpaperTheme() && DataStore.currentWallpaperPath.isNotEmpty()) {
                val drawable = WallpaperManager.loadWallpaperDrawable(context, DataStore.currentWallpaperPath)
                if (drawable != null) {
                    previewBgImage.setImageDrawable(drawable)
                } else {
                    previewBgImage.setImageResource(R.drawable.bg_mesh_gradient)
                }
                previewScrim.setBackgroundColor(if (isNight) Color.parseColor("#40000000") else Color.parseColor("#20FFFFFF"))
            } else {
                previewBgImage.setImageResource(R.drawable.bg_mesh_gradient)
                previewScrim.setBackgroundColor(if (isNight) Color.parseColor("#50000000") else Color.parseColor("#20FFFFFF"))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val blur = DataStore.wallpaperBlurRadius.toFloat().coerceIn(0f, 60f)
                if (blur > 0f) {
                    previewBgImage.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP))
                } else {
                    previewBgImage.setRenderEffect(null)
                }
            }

            previewCard.strokeColor = Theme.getCardStrokeColor(context)
            previewCard.setCardBackgroundColor(Theme.getCardBackgroundColor(context))

            miniCard.strokeColor = Theme.getCardStrokeColor(context)
            miniCard.setCardBackgroundColor(Theme.getCardBackgroundColor(context))
            miniCardText.setTextColor(if (isNight) Color.WHITE else Color.BLACK)

            miniStatusChip.background = GradientDrawable().apply {
                cornerRadius = dp2px(8).toFloat()
                setColor(curAccent)
            }

            previewModeBadge.text = when {
                isPureBlack -> "🖤 AMOLED"
                DataStore.nightTheme == 1 -> "🌙 " + context.getString(R.string.theme_mode_dark)
                DataStore.nightTheme == 2 -> "☀️ " + context.getString(R.string.theme_mode_light)
                else -> "📱 " + context.getString(R.string.theme_mode_system)
            }
        }

        refreshLivePreview()

        // =========================================================================
        // 2. Appearance Mode Item (FlClash _ThemeModeItem)
        // =========================================================================
        container.addView(TextView(context).apply {
            text = context.getString(R.string.theme_appearance_mode)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp2px(8))
        })

        val modeGroupCard = MaterialCardView(context).apply {
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
                bottomMargin = dp2px(12)
            }
        }

        val modeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp2px(4), dp2px(4), dp2px(4), dp2px(4))
        }

        val modes = listOf(
            Triple(0, context.getString(R.string.theme_mode_system), "📱"),
            Triple(2, context.getString(R.string.theme_mode_light), "☀️"),
            Triple(1, context.getString(R.string.theme_mode_dark), "🌙")
        )

        fun populateModeButtons() {
            modeRow.removeAllViews()
            val curMode = DataStore.nightTheme
            for ((modeVal, label, icon) in modes) {
                val isSelected = curMode == modeVal
                val btn = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp2px(38), 1f).apply {
                        setMargins(dp2px(2), 0, dp2px(2), 0)
                    }
                    gravity = Gravity.CENTER
                    text = "$icon $label"
                    textSize = 12f
                    setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                    if (isSelected) {
                        setTextColor(Color.WHITE)
                        background = GradientDrawable().apply {
                            cornerRadius = dp2px(12).toFloat()
                            setColor(accent)
                        }
                    } else {
                        setTextColor(context.getColorAttr(android.R.attr.textColorSecondary))
                        background = ColorDrawable(Color.TRANSPARENT)
                    }
                    setOnClickListener {
                        DataStore.nightTheme = modeVal
                        Theme.currentNightMode = -1
                        Theme.applyNightTheme()
                        populateModeButtons()
                        refreshLivePreview()
                        (context as? MainActivity)?.updateWallpaperBackground()
                    }
                }
                modeRow.addView(btn)
            }
        }

        populateModeButtons()
        modeGroupCard.addView(modeRow)
        container.addView(modeGroupCard)

        // =========================================================================
        // 3. AMOLED Pure Black Switch (FlClash _PureBlackItem)
        // =========================================================================
        val pureBlackCard = MaterialCardView(context).apply {
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
                bottomMargin = dp2px(12)
            }
        }

        val pureBlackRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp2px(14), dp2px(10), dp2px(14), dp2px(10))
        }

        val pureBlackTextLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        pureBlackTextLayout.addView(TextView(context).apply {
            text = "🖤 " + context.getString(R.string.theme_pure_black)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        pureBlackTextLayout.addView(TextView(context).apply {
            text = context.getString(R.string.theme_pure_black_desc)
            textSize = 11f
            setTextColor(context.getColorAttr(android.R.attr.textColorSecondary))
        })
        pureBlackRow.addView(pureBlackTextLayout)

        val pureBlackSwitch = SwitchCompat(context).apply {
            isChecked = DataStore.amoledPureBlack
            setOnCheckedChangeListener { _, isChecked ->
                DataStore.amoledPureBlack = isChecked
                refreshLivePreview()
                (context as? MainActivity)?.updateWallpaperBackground()
            }
        }
        pureBlackRow.addView(pureBlackSwitch)
        pureBlackCard.addView(pureBlackRow)
        container.addView(pureBlackCard)

        // =========================================================================
        // 4. Liquid Glass Mode Selection (FlClash _LiquidGlassItem)
        // =========================================================================
        container.addView(TextView(context).apply {
            text = context.getString(R.string.theme_liquid_glass)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp2px(8))
        })

        val glassGroupCard = MaterialCardView(context).apply {
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
                bottomMargin = dp2px(12)
            }
        }

        val glassRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp2px(4), dp2px(4), dp2px(4), dp2px(4))
        }

        val glassModes = listOf(
            Pair(0, context.getString(R.string.theme_liquid_glass_none)),
            Pair(1, context.getString(R.string.theme_liquid_glass_standard)),
            Pair(2, context.getString(R.string.theme_liquid_glass_ultra))
        )

        fun populateGlassButtons() {
            glassRow.removeAllViews()
            val curGlass = DataStore.liquidGlassMode
            for ((gVal, label) in glassModes) {
                val isSelected = curGlass == gVal
                val btn = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp2px(36), 1f).apply {
                        setMargins(dp2px(2), 0, dp2px(2), 0)
                    }
                    gravity = Gravity.CENTER
                    text = label
                    textSize = 12f
                    setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                    if (isSelected) {
                        setTextColor(Color.WHITE)
                        background = GradientDrawable().apply {
                            cornerRadius = dp2px(12).toFloat()
                            setColor(accent)
                        }
                    } else {
                        setTextColor(context.getColorAttr(android.R.attr.textColorSecondary))
                        background = ColorDrawable(Color.TRANSPARENT)
                    }
                    setOnClickListener {
                        DataStore.liquidGlassMode = gVal
                        populateGlassButtons()
                        refreshLivePreview()
                    }
                }
                glassRow.addView(btn)
            }
        }

        populateGlassButtons()
        glassGroupCard.addView(glassRow)
        container.addView(glassGroupCard)

        // =========================================================================
        // 5. FlClash Sliders (Background Blur & Card Opacity)
        // =========================================================================
        val slidersCard = MaterialCardView(context).apply {
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
                bottomMargin = dp2px(12)
            }
        }

        val slidersLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp2px(14), dp2px(12), dp2px(14), dp2px(12))
        }

        // --- Background Blur Slider ---
        val blurHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        blurHeaderRow.addView(TextView(context).apply {
            text = "💧 " + context.getString(R.string.theme_background_blur)
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val blurValueText = TextView(context).apply {
            text = "${DataStore.wallpaperBlurRadius} dp"
            textSize = 12f
            setTextColor(accent)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        blurHeaderRow.addView(blurValueText)
        slidersLayout.addView(blurHeaderRow)

        val blurSeekBar = SeekBar(context).apply {
            max = 60
            progress = DataStore.wallpaperBlurRadius.coerceIn(0, 60)
            thumbTintList = ColorStateList.valueOf(accent)
            progressTintList = ColorStateList.valueOf(accent)
            setPadding(dp2px(4), dp2px(6), dp2px(4), dp2px(10))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    blurValueText.text = "$progress dp"
                    DataStore.wallpaperBlurRadius = progress
                    refreshLivePreview()
                    (context as? MainActivity)?.updateWallpaperBackground()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        slidersLayout.addView(blurSeekBar)

        // --- Card Glass Opacity Slider ---
        val opacityHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp2px(4)
            }
        }
        opacityHeaderRow.addView(TextView(context).apply {
            text = "✨ " + context.getString(R.string.theme_card_opacity)
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val opacityValueText = TextView(context).apply {
            text = "${DataStore.cardGlassOpacity}%"
            textSize = 12f
            setTextColor(accent)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        opacityHeaderRow.addView(opacityValueText)
        slidersLayout.addView(opacityHeaderRow)

        val opacitySeekBar = SeekBar(context).apply {
            max = 100
            progress = DataStore.cardGlassOpacity.coerceIn(25, 100)
            thumbTintList = ColorStateList.valueOf(accent)
            progressTintList = ColorStateList.valueOf(accent)
            setPadding(dp2px(4), dp2px(6), dp2px(4), dp2px(4))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val realProgress = progress.coerceAtLeast(25)
                    opacityValueText.text = "$realProgress%"
                    DataStore.cardGlassOpacity = realProgress
                    refreshLivePreview()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        slidersLayout.addView(opacitySeekBar)

        slidersCard.addView(slidersLayout)
        container.addView(slidersCard)

        // =========================================================================
        // 6. Wallpaper Theme Section (FlClash Background Management)
        // =========================================================================
        container.addView(TextView(context).apply {
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
        container.addView(switchCard)

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
        container.addView(wallpaperScrollView)

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
                        imageTintList = ColorStateList.valueOf(accent)
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
                        setTextColor(accent)
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

                if (isSelected) {
                    val badge = ImageView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(dp2px(20), dp2px(20), Gravity.TOP or Gravity.START).apply {
                            setMargins(dp2px(4), dp2px(4), 0, 0)
                        }
                        setImageResource(R.drawable.ic_action_done)
                        imageTintList = ColorStateList.valueOf(Color.WHITE)
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(accent)
                        }
                        setPadding(dp2px(2))
                    }
                    frame.addView(badge)
                }

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
                                    refreshLivePreview()
                                    (context as? MainActivity)?.updateWallpaperBackground()
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
                    toggleSwitch.isChecked = true
                    populateWallpapers()
                    refreshLivePreview()
                    (context as? MainActivity)?.updateWallpaperBackground()
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
            refreshLivePreview()
            (context as? MainActivity)?.updateWallpaperBackground()
        }

        // =========================================================================
        // 7. Accent Color Palettes (FlClash _ColorSwatch & Custom Colors)
        // =========================================================================
        fun getFlClashSwatch(color: Int, isSelected: Boolean, onClick: () -> Unit): View {
            val size = dp2px(48)
            val frame = FrameLayout(context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(size, size).apply {
                    val m = dp2px(4)
                    setMargins(m, m, m, m)
                }
            }
            val circle = View(context).apply {
                val circleSize = dp2px(38)
                layoutParams = FrameLayout.LayoutParams(circleSize, circleSize, Gravity.CENTER)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    if (isSelected) {
                        setStroke(dp2px(2), Color.WHITE)
                    }
                }
            }
            frame.addView(circle)
            if (isSelected) {
                val check = ImageView(context).apply {
                    val checkSize = dp2px(20)
                    layoutParams = FrameLayout.LayoutParams(checkSize, checkSize, Gravity.CENTER)
                    setImageResource(R.drawable.ic_action_done)
                    val isDark = ColorUtils.calculateLuminance(color) < 0.5
                    imageTintList = ColorStateList.valueOf(if (isDark) Color.WHITE else Color.BLACK)
                }
                frame.addView(check)
            }
            frame.setOnClickListener { onClick() }
            return frame
        }

        lateinit var customColorsFlex: FlexboxLayout
        lateinit var cupertinoFlex: FlexboxLayout
        lateinit var materialFlex: FlexboxLayout

        // Forward declaration of custom color dialog
        fun showCustomColorDialog(initialColor: Int = if (DataStore.customColor != 0) DataStore.customColor else 0xFF007AFF.toInt()) {
            var currentColor = initialColor
            val colorDialogView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp2px(20), dp2px(12), dp2px(20), dp2px(12))
            }

            // --- Top Preview: Large Circle & Hex Input ---
            val previewRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp2px(16)
                }
            }

            val largeColorCircle = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp2px(54), dp2px(54)).apply {
                    marginEnd = dp2px(16)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(currentColor)
                    setStroke(dp2px(2), 0x33FFFFFF)
                }
            }
            previewRow.addView(largeColorCircle)

            val hexInputLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            hexInputLayout.addView(TextView(context).apply {
                text = context.getString(R.string.hex_code)
                textSize = 12f
                setTextColor(context.getColorAttr(android.R.attr.textColorSecondary))
            })

            val hexEditText = EditText(context).apply {
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setText(String.format("#%06X", 0xFFFFFF and currentColor))
                setSingleLine()
            }
            hexInputLayout.addView(hexEditText)
            previewRow.addView(hexInputLayout)
            colorDialogView.addView(previewRow)

            // --- Quick Popular Aesthetic Presets ---
            val quickPresets = intArrayOf(
                0xFF007AFF.toInt(), // Electric Blue
                0xFFFF2D55.toInt(), // Cyber Pink
                0xFFAF52DE.toInt(), // Neon Purple
                0xFF30D158.toInt(), // Tiffany Mint
                0xFFFF9500.toInt(), // Flame Orange
                0xFFFFD60A.toInt(), // Sunrise Gold
                0xFF64D2FF.toInt(), // Glacier Cyan
                0xFFFF3B30.toInt(), // Crimson Red
                0xFF5856D6.toInt(), // Royal Violet
                0xFF00C7BE.toInt()  // Emerald Teal
            )

            val quickScrollView = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp2px(16)
                }
            }
            val quickContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            quickScrollView.addView(quickContainer)

            lateinit var rSeek: SeekBar
            lateinit var gSeek: SeekBar
            lateinit var bSeek: SeekBar
            lateinit var rText: TextView
            lateinit var gText: TextView
            lateinit var bText: TextView
            var isInternalUpdating = false

            fun updateColor(newColor: Int, fromText: Boolean = false, fromSliders: Boolean = false) {
                currentColor = newColor
                (largeColorCircle.background as? GradientDrawable)?.setColor(newColor)
                if (!fromText) {
                    isInternalUpdating = true
                    hexEditText.setText(String.format("#%06X", 0xFFFFFF and newColor))
                    isInternalUpdating = false
                }
                if (!fromSliders) {
                    isInternalUpdating = true
                    val r = Color.red(newColor)
                    val g = Color.green(newColor)
                    val b = Color.blue(newColor)
                    rSeek.progress = r
                    gSeek.progress = g
                    bSeek.progress = b
                    rText.text = "$r"
                    gText.text = "$g"
                    bText.text = "$b"
                    isInternalUpdating = false
                }
            }

            for (pColor in quickPresets) {
                val chip = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp2px(32), dp2px(32)).apply {
                        setMargins(dp2px(4), 0, dp2px(4), 0)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(pColor)
                        setStroke(dp2px(1), 0x33FFFFFF)
                    }
                    setOnClickListener {
                        updateColor(pColor)
                    }
                }
                quickContainer.addView(chip)
            }
            colorDialogView.addView(quickScrollView)

            fun createChannelSlider(title: String, colorTint: Int, initVal: Int, onValChanged: (Int) -> Unit): Pair<SeekBar, TextView> {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp2px(4)
                    }
                }
                row.addView(TextView(context).apply {
                    text = title
                    textSize = 12f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(colorTint)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                val valText = TextView(context).apply {
                    text = "$initVal"
                    textSize = 12f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                row.addView(valText)
                colorDialogView.addView(row)

                val seek = SeekBar(context).apply {
                    max = 255
                    progress = initVal
                    thumbTintList = ColorStateList.valueOf(colorTint)
                    progressTintList = ColorStateList.valueOf(colorTint)
                    setPadding(dp2px(4), dp2px(6), dp2px(4), dp2px(8))
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                            valText.text = "$progress"
                            if (fromUser && !isInternalUpdating) {
                                onValChanged(progress)
                            }
                        }
                        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                    })
                }
                colorDialogView.addView(seek)
                return Pair(seek, valText)
            }

            val rPair = createChannelSlider(context.getString(R.string.color_red), 0xFFFF3B30.toInt(), Color.red(currentColor)) { r ->
                val newC = Color.rgb(r, Color.green(currentColor), Color.blue(currentColor))
                updateColor(newC, fromSliders = true)
            }
            rSeek = rPair.first
            rText = rPair.second

            val gPair = createChannelSlider(context.getString(R.string.color_green), 0xFF34C759.toInt(), Color.green(currentColor)) { g ->
                val newC = Color.rgb(Color.red(currentColor), g, Color.blue(currentColor))
                updateColor(newC, fromSliders = true)
            }
            gSeek = gPair.first
            gText = gPair.second

            val bPair = createChannelSlider(context.getString(R.string.color_blue), 0xFF007AFF.toInt(), Color.blue(currentColor)) { b ->
                val newC = Color.rgb(Color.red(currentColor), Color.green(currentColor), b)
                updateColor(newC, fromSliders = true)
            }
            bSeek = bPair.first
            bText = bPair.second

            hexEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (isInternalUpdating) return
                    val text = s?.toString()?.trim() ?: ""
                    if (text.startsWith("#") && (text.length == 7 || text.length == 9)) {
                        try {
                            val parsed = Color.parseColor(text)
                            updateColor(parsed, fromText = true)
                        } catch (_: Exception) {}
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            val customDialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.custom_color_title)
                .setView(colorDialogView)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    Theme.addCustomColor(currentColor)
                    DataStore.customColor = currentColor
                    DataStore.customColorEnabled = true
                    val closestTheme = Theme.findClosestTheme(currentColor)
                    DataStore.appTheme = closestTheme
                    persistInt(closestTheme)
                    dialog.dismiss()
                    callChangeListener(closestTheme)
                    (context as? Activity)?.let { act -> ActivityCompat.recreate(act) }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
            customDialog.applyGlassBlur()
            customDialog.show()
        }

        fun populateColors() {
            // Populate custom colors
            customColorsFlex.removeAllViews()

            // + Add Custom Color Button (FlClash _AddColorButton)
            val addBtnSize = dp2px(48)
            val addFrame = FrameLayout(context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(addBtnSize, addBtnSize).apply {
                    val m = dp2px(4)
                    setMargins(m, m, m, m)
                }
            }
            val addCircle = FrameLayout(context).apply {
                val circleSize = dp2px(38)
                layoutParams = FrameLayout.LayoutParams(circleSize, circleSize, Gravity.CENTER)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Theme.getCardBackgroundColor(context))
                    setStroke(dp2px(1), accent)
                }
                addView(ImageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(dp2px(20), dp2px(20), Gravity.CENTER)
                    setImageResource(R.drawable.ic_action_add)
                    imageTintList = ColorStateList.valueOf(accent)
                })
            }
            addFrame.addView(addCircle)
            addFrame.setOnClickListener {
                showCustomColorDialog()
            }
            customColorsFlex.addView(addFrame)

            val savedCustomColors = Theme.getCustomColors()
            for (color in savedCustomColors) {
                val isSelected = DataStore.customColorEnabled && DataStore.customColor == color
                val swatch = getFlClashSwatch(color, isSelected) {
                    DataStore.customColor = color
                    DataStore.customColorEnabled = true
                    val closestTheme = Theme.findClosestTheme(color)
                    DataStore.appTheme = closestTheme
                    persistInt(closestTheme)
                    dialog.dismiss()
                    callChangeListener(closestTheme)
                    (context as? Activity)?.let { act -> ActivityCompat.recreate(act) }
                }
                swatch.setOnLongClickListener {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.delete_color_confirm)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            Theme.deleteCustomColor(color)
                            populateColors()
                            refreshLivePreview()
                            (context as? Activity)?.let { act -> ActivityCompat.recreate(act) }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                    true
                }
                customColorsFlex.addView(swatch)
            }

            // Populate Cupertino Colors
            cupertinoFlex.removeAllViews()
            val cupertinoColors = context.resources.getIntArray(R.array.cupertino_colors)
            for ((i, color) in cupertinoColors.withIndex()) {
                val themeId = i + 101
                val isSelected = !DataStore.customColorEnabled && currentSelectedTheme == themeId
                val swatch = getFlClashSwatch(color, isSelected) {
                    currentSelectedTheme = themeId
                    DataStore.customColorEnabled = false
                    persistInt(themeId)
                    DataStore.appTheme = themeId
                    populateColors()
                    refreshLivePreview()
                    callChangeListener(themeId)
                    dialog.dismiss()
                    (context as? Activity)?.let { act -> ActivityCompat.recreate(act) }
                }
                cupertinoFlex.addView(swatch)
            }

            // Populate Material Colors
            materialFlex.removeAllViews()
            val materialColors = context.resources.getIntArray(R.array.material_colors)
            for ((i, color) in materialColors.withIndex()) {
                val themeId = i + 1
                val isSelected = !DataStore.customColorEnabled && currentSelectedTheme == themeId
                val swatch = getFlClashSwatch(color, isSelected) {
                    currentSelectedTheme = themeId
                    DataStore.customColorEnabled = false
                    persistInt(themeId)
                    DataStore.appTheme = themeId
                    populateColors()
                    refreshLivePreview()
                    callChangeListener(themeId)
                    dialog.dismiss()
                    (context as? Activity)?.let { act -> ActivityCompat.recreate(act) }
                }
                materialFlex.addView(swatch)
            }
        }

        // --- Custom Colors Section ---
        container.addView(TextView(context).apply {
            text = "🎨 " + context.getString(R.string.theme_custom_colors)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp2px(8))
        })
        customColorsFlex = FlexboxLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            flexWrap = FlexWrap.WRAP
        }
        container.addView(customColorsFlex)

        // --- Cupertino Section ---
        container.addView(TextView(context).apply {
            text = context.getString(R.string.theme_cupertino_category)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp2px(12), 0, dp2px(8))
        })
        cupertinoFlex = FlexboxLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            flexWrap = FlexWrap.WRAP
            justifyContent = JustifyContent.SPACE_BETWEEN
        }
        container.addView(cupertinoFlex)

        // --- Material Section ---
        container.addView(TextView(context).apply {
            text = context.getString(R.string.theme_material_category)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp2px(16), 0, dp2px(8))
        })
        materialFlex = FlexboxLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            flexWrap = FlexWrap.WRAP
            justifyContent = JustifyContent.SPACE_BETWEEN
        }
        container.addView(materialFlex)

        populateColors()

        val scrollView = NestedScrollView(context).apply {
            setPadding(dp2px(16), dp2px(16), dp2px(16), dp2px(16))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(container)
        }

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                callChangeListener(DataStore.appTheme)
                (context as? Activity)?.let { act -> ActivityCompat.recreate(act) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.applyGlassBlur()
        dialog.show()
    }
}
