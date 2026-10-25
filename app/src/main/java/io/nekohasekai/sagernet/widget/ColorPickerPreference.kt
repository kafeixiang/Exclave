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
        var currentSelectedTheme = DataStore.appTheme

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        fun createSectionHeader(title: String, icon: String): View {
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp2px(2), dp2px(10), dp2px(2), dp2px(6))
                addView(TextView(context).apply {
                    text = "$icon  $title"
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(context.getColorAttr(android.R.attr.textColorPrimary))
                })
            }
        }

        fun createGroupCard(): MaterialCardView {
            return MaterialCardView(context).apply {
                radius = dp2px(18).toFloat()
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
        }

        fun createDivider(): View {
            return View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp2px(1)
                )
                val isNight = Theme.usingNightMode()
                setBackgroundColor(if (isNight) 0x1EFFFFFF.toInt() else 0x12000000.toInt())
            }
        }

        // =========================================================================
        // 1. Live Preview Card (FlClash ThemeLivePreview)
        // =========================================================================
        val previewCard = MaterialCardView(context).apply {
            radius = dp2px(18).toFloat()
            cardElevation = dp2px(2).toFloat()
            maxCardElevation = dp2px(4).toFloat()
            strokeWidth = dp2px(1)
            strokeColor = Theme.getCardStrokeColor(context)
            setCardBackgroundColor(Theme.getCardBackgroundColor(context))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp2px(110)
            ).apply {
                bottomMargin = dp2px(12)
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

        // Mock Mini Floating Neutral Liquid Glass Card
        val miniCard = MaterialCardView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            radius = dp2px(12).toFloat()
            cardElevation = 0f
            maxCardElevation = 0f
            strokeWidth = dp2px(1)
            strokeColor = Theme.getCardStrokeColor(context)
            setCardBackgroundColor(Theme.getCardBackgroundColor(context))
        }

        val miniCardContent = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp2px(12), dp2px(6), dp2px(12), dp2px(6))
        }

        val miniCardText = TextView(context).apply {
            text = "Liquid Glass"
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (Theme.usingNightMode()) Color.WHITE else Color.BLACK)
        }
        miniCardContent.addView(miniCardText)

        val miniStatusChip = TextView(context).apply {
            text = "Active"
            textSize = 9f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp2px(6), dp2px(1), dp2px(6), dp2px(1))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp2px(6)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp2px(6).toFloat()
                setColor(Theme.getAccentColor(context))
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
                setMargins(0, dp2px(6), dp2px(6), 0)
            }
            textSize = 9f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp2px(6), dp2px(2), dp2px(6), dp2px(2))
            background = GradientDrawable().apply {
                cornerRadius = dp2px(8).toFloat()
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
                cornerRadius = dp2px(6).toFloat()
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
        // 2. Group: Appearance Mode (FlClash _ThemeModeItem & _PureBlackItem)
        // =========================================================================
        container.addView(createSectionHeader(context.getString(R.string.theme_appearance_mode), "☀️"))

        val modeGroupCard = createGroupCard()
        val modeGroupLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val modeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp2px(6), dp2px(6), dp2px(6), dp2px(6))
        }

        val modes = listOf(
            Triple(0, context.getString(R.string.theme_mode_system), "📱"),
            Triple(2, context.getString(R.string.theme_mode_light), "☀️"),
            Triple(1, context.getString(R.string.theme_mode_dark), "🌙")
        )

        fun populateModeButtons() {
            modeRow.removeAllViews()
            val curMode = DataStore.nightTheme
            val curAccent = Theme.getAccentColor(context)
            for ((modeVal, label, icon) in modes) {
                val isSelected = curMode == modeVal
                val btn = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp2px(36), 1f).apply {
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
                            setColor(curAccent)
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
        modeGroupLayout.addView(modeRow)

        // Integrated AMOLED Pure Black Row inside Appearance card
        modeGroupLayout.addView(createDivider())

        val pureBlackRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp2px(14), dp2px(8), dp2px(14), dp2px(8))
        }
        val pureBlackTextLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        pureBlackTextLayout.addView(TextView(context).apply {
            text = "🖤 " + context.getString(R.string.theme_pure_black)
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
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
        modeGroupLayout.addView(pureBlackRow)

        modeGroupCard.addView(modeGroupLayout)
        container.addView(modeGroupCard)

        // =========================================================================
        // 3. Group: Theme Accent Colors (FlClash _PrimaryColorItem & Swatches)
        // =========================================================================
        container.addView(createSectionHeader(context.getString(R.string.theme_custom_colors), "🎨"))

        val colorsGroupCard = createGroupCard()
        val colorsGroupLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp2px(12), dp2px(12), dp2px(12), dp2px(12))
        }

        fun getFlClashSwatch(color: Int, isSelected: Boolean, onClick: () -> Unit): View {
            val size = dp2px(42)
            val frame = FrameLayout(context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(size, size).apply {
                    val m = dp2px(3)
                    setMargins(m, m, m, m)
                }
            }
            val circle = View(context).apply {
                val circleSize = dp2px(34)
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
                    val checkSize = dp2px(18)
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

        lateinit var mainSwatchesFlex: FlexboxLayout
        lateinit var moreColorsFlex: FlexboxLayout
        lateinit var btnExpandMore: TextView
        var isColorsExpanded = false

        // Custom Color Dialog
        fun showCustomColorDialog(initialColor: Int = if (DataStore.customColor != 0) DataStore.customColor else 0xFF007AFF.toInt()) {
            var currentColor = initialColor
            val colorDialogView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp2px(18), dp2px(12), dp2px(18), dp2px(12))
            }

            val previewRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp2px(14)
                }
            }

            val largeColorCircle = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp2px(48), dp2px(48)).apply {
                    marginEnd = dp2px(14)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(currentColor)
                    setStroke(dp2px(1), 0x33FFFFFF)
                }
            }
            previewRow.addView(largeColorCircle)

            val hexInputLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            hexInputLayout.addView(TextView(context).apply {
                text = context.getString(R.string.hex_code)
                textSize = 11f
                setTextColor(context.getColorAttr(android.R.attr.textColorSecondary))
            })

            val hexEditText = EditText(context).apply {
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setText(String.format("#%06X", 0xFFFFFF and currentColor))
                setSingleLine()
            }
            hexInputLayout.addView(hexEditText)
            previewRow.addView(hexInputLayout)
            colorDialogView.addView(previewRow)

            // Preset chips
            val quickPresets = intArrayOf(
                0xFF007AFF.toInt(), 0xFFFF2D55.toInt(), 0xFFAF52DE.toInt(), 0xFF30D158.toInt(),
                0xFFFF9500.toInt(), 0xFFFFD60A.toInt(), 0xFF64D2FF.toInt(), 0xFFFF3B30.toInt()
            )

            val quickScrollView = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp2px(12)
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
                    layoutParams = LinearLayout.LayoutParams(dp2px(28), dp2px(28)).apply {
                        setMargins(dp2px(3), 0, dp2px(3), 0)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(pColor)
                        setStroke(dp2px(1), 0x33FFFFFF)
                    }
                    setOnClickListener { updateColor(pColor) }
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
                        topMargin = dp2px(2)
                    }
                }
                row.addView(TextView(context).apply {
                    text = title
                    textSize = 11f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(colorTint)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                val valText = TextView(context).apply {
                    text = "$initVal"
                    textSize = 11f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                row.addView(valText)
                colorDialogView.addView(row)

                val seek = SeekBar(context).apply {
                    max = 255
                    progress = initVal
                    thumbTintList = ColorStateList.valueOf(colorTint)
                    progressTintList = ColorStateList.valueOf(colorTint)
                    setPadding(dp2px(4), dp2px(4), dp2px(4), dp2px(6))
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
            mainSwatchesFlex.removeAllViews()

            // 1. + Add Custom Color Button (FlClash _AddColorButton)
            val curAccent = Theme.getAccentColor(context)
            val addBtnSize = dp2px(42)
            val addFrame = FrameLayout(context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(addBtnSize, addBtnSize).apply {
                    val m = dp2px(3)
                    setMargins(m, m, m, m)
                }
            }
            val addCircle = FrameLayout(context).apply {
                val circleSize = dp2px(34)
                layoutParams = FrameLayout.LayoutParams(circleSize, circleSize, Gravity.CENTER)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Theme.getCardBackgroundColor(context))
                    setStroke(dp2px(1), curAccent)
                }
                addView(ImageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(dp2px(18), dp2px(18), Gravity.CENTER)
                    setImageResource(R.drawable.ic_action_add)
                    imageTintList = ColorStateList.valueOf(curAccent)
                })
            }
            addFrame.addView(addCircle)
            addFrame.setOnClickListener { showCustomColorDialog() }
            mainSwatchesFlex.addView(addFrame)

            // 2. User Saved Custom Colors
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
                mainSwatchesFlex.addView(swatch)
            }

            // 3. Curated 10 Modern Signature Cupertino Colors (Clean & Aesthetic)
            val cupertinoColors = context.resources.getIntArray(R.array.cupertino_colors)
            val curatedCount = minOf(10, cupertinoColors.size)
            for (i in 0 until curatedCount) {
                val color = cupertinoColors[i]
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
                mainSwatchesFlex.addView(swatch)
            }

            // 4. Extended Colors in Expandable section
            moreColorsFlex.removeAllViews()
            for (i in curatedCount until cupertinoColors.size) {
                val color = cupertinoColors[i]
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
                moreColorsFlex.addView(swatch)
            }

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
                moreColorsFlex.addView(swatch)
            }
        }

        mainSwatchesFlex = FlexboxLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            flexWrap = FlexWrap.WRAP
        }
        colorsGroupLayout.addView(mainSwatchesFlex)

        // Expand/Collapse More Presets
        moreColorsFlex = FlexboxLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            flexWrap = FlexWrap.WRAP
            visibility = View.GONE
        }
        colorsGroupLayout.addView(moreColorsFlex)

        btnExpandMore = TextView(context).apply {
            text = "▾ " + context.getString(R.string.expand_more_colors)
            textSize = 11f
            setTextColor(context.getColorAttr(android.R.attr.textColorSecondary))
            gravity = Gravity.CENTER
            setPadding(0, dp2px(8), 0, dp2px(2))
            setOnClickListener {
                isColorsExpanded = !isColorsExpanded
                moreColorsFlex.visibility = if (isColorsExpanded) View.VISIBLE else View.GONE
                text = if (isColorsExpanded) "▴ " + context.getString(R.string.collapse_colors) else "▾ " + context.getString(R.string.expand_more_colors)
            }
        }
        colorsGroupLayout.addView(btnExpandMore)

        populateColors()
        colorsGroupCard.addView(colorsGroupLayout)
        container.addView(colorsGroupCard)

        // =========================================================================
        // 4. Group: Glass & Wallpaper (FlClash Background Blur & Opacity Sliders)
        // =========================================================================
        container.addView(createSectionHeader(context.getString(R.string.theme_wallpaper_category), "✨"))

        val glassWallpaperCard = createGroupCard()
        val glassWallpaperLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp2px(14), dp2px(12), dp2px(14), dp2px(12))
        }

        // --- Background Blur Slider ---
        val curAccent = Theme.getAccentColor(context)
        val blurHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        blurHeaderRow.addView(TextView(context).apply {
            text = "💧 " + context.getString(R.string.theme_background_blur)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val blurValueText = TextView(context).apply {
            text = "${DataStore.wallpaperBlurRadius} dp"
            textSize = 12f
            setTextColor(curAccent)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        blurHeaderRow.addView(blurValueText)
        glassWallpaperLayout.addView(blurHeaderRow)

        val blurSeekBar = SeekBar(context).apply {
            max = 60
            progress = DataStore.wallpaperBlurRadius.coerceIn(0, 60)
            thumbTintList = ColorStateList.valueOf(curAccent)
            progressTintList = ColorStateList.valueOf(curAccent)
            setPadding(dp2px(4), dp2px(4), dp2px(4), dp2px(8))
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
        glassWallpaperLayout.addView(blurSeekBar)

        // --- Card Glass Opacity Slider ---
        val opacityHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp2px(2)
            }
        }
        opacityHeaderRow.addView(TextView(context).apply {
            text = "🪟 " + context.getString(R.string.theme_card_opacity)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val opacityValueText = TextView(context).apply {
            text = "${DataStore.cardGlassOpacity}%"
            textSize = 12f
            setTextColor(curAccent)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        opacityHeaderRow.addView(opacityValueText)
        glassWallpaperLayout.addView(opacityHeaderRow)

        val opacitySeekBar = SeekBar(context).apply {
            max = 100
            progress = DataStore.cardGlassOpacity.coerceIn(25, 100)
            thumbTintList = ColorStateList.valueOf(curAccent)
            progressTintList = ColorStateList.valueOf(curAccent)
            setPadding(dp2px(4), dp2px(4), dp2px(4), dp2px(8))
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
        glassWallpaperLayout.addView(opacitySeekBar)

        // Divider
        glassWallpaperLayout.addView(createDivider().apply {
            (layoutParams as? LinearLayout.LayoutParams)?.let {
                it.topMargin = dp2px(6)
                it.bottomMargin = dp2px(8)
            }
        })

        // Wallpaper Toggle Switch Row
        val wpSwitchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        wpSwitchRow.addView(TextView(context).apply {
            text = context.getString(R.string.enable_wallpaper_theme)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })

        val toggleSwitch = SwitchCompat(context).apply {
            isChecked = DataStore.wallpaperThemeEnabled
        }
        wpSwitchRow.addView(toggleSwitch)
        glassWallpaperLayout.addView(wpSwitchRow)

        // Wallpaper Horizontal Carousel
        val wallpaperScrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, dp2px(6), 0, 0)
        }
        val wallpaperContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        wallpaperScrollView.addView(wallpaperContainer)
        glassWallpaperLayout.addView(wallpaperScrollView)

        fun populateWallpapers() {
            wallpaperContainer.removeAllViews()

            // + Upload Wallpaper Card
            val uploadCard = MaterialCardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp2px(75), dp2px(105)).apply {
                    marginEnd = dp2px(8)
                }
                radius = dp2px(12).toFloat()
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
                        layoutParams = LinearLayout.LayoutParams(dp2px(26), dp2px(26))
                        setImageResource(R.drawable.ic_image_photo)
                        imageTintList = ColorStateList.valueOf(curAccent)
                    })
                    addView(TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = dp2px(4)
                        }
                        text = context.getString(R.string.upload_wallpaper)
                        textSize = 11f
                        setTextColor(curAccent)
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
                    layoutParams = LinearLayout.LayoutParams(dp2px(75), dp2px(105)).apply {
                        marginEnd = dp2px(8)
                    }
                    radius = dp2px(12).toFloat()
                    cardElevation = 0f
                    maxCardElevation = 0f
                    if (isSelected) {
                        strokeWidth = dp2px(2)
                        strokeColor = curAccent
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
                    val thumb = WallpaperManager.getThumbnail(context, item, dp2px(75), dp2px(105))
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
                        dp2px(30),
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
                        setPadding(dp2px(2), 0, dp2px(2), dp2px(2))
                    }
                    text = item.name
                    textSize = 9f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    isSingleLine = true
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                frame.addView(titleView)

                if (isSelected) {
                    val badge = ImageView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(dp2px(18), dp2px(18), Gravity.TOP or Gravity.START).apply {
                            setMargins(dp2px(3), dp2px(3), 0, 0)
                        }
                        setImageResource(R.drawable.ic_action_done)
                        imageTintList = ColorStateList.valueOf(Color.WHITE)
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(curAccent)
                        }
                        setPadding(dp2px(2))
                    }
                    frame.addView(badge)
                }

                if (!item.isPreset) {
                    val deleteBtn = ImageView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(dp2px(18), dp2px(18), Gravity.TOP or Gravity.END).apply {
                            setMargins(0, dp2px(3), dp2px(3), 0)
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

        glassWallpaperCard.addView(glassWallpaperLayout)
        container.addView(glassWallpaperCard)

        val scrollView = NestedScrollView(context).apply {
            setPadding(dp2px(14), dp2px(10), dp2px(14), dp2px(10))
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
