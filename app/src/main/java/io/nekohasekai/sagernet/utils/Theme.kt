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

package io.nekohasekai.sagernet.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.ColorUtils
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.getColorAttr

object Theme {

    const val RED = 1
    const val PINK = 2
    const val PURPLE = 3
    const val DEEP_PURPLE = 4
    const val INDIGO = 5
    const val BLUE = 6
    const val LIGHT_BLUE = 7
    const val CYAN = 8
    const val TEAL = 9
    const val GREEN = 10
    const val LIGHT_GREEN = 11
    const val LIME = 12
    const val YELLOW = 13
    const val AMBER = 14
    const val ORANGE = 15
    const val DEEP_ORANGE = 16

    const val BROWN = 17
    const val GREY = 18
    const val BLUE_GREY = 19
    const val BLACK = 20

    const val VERDANT_MINT = 22

    const val DYNAMIC = 21

    const val CUPERTINO_PINK = 101
    const val CUPERTINO_BLUE = 102
    const val CUPERTINO_ORANGE = 103
    const val CUPERTINO_GREEN = 104
    const val CUPERTINO_PURPLE = 105
    const val CUPERTINO_AMBER = 106
    const val CUPERTINO_MINT = 107
    const val CUPERTINO_CORAL = 108
    const val CUPERTINO_ROSE_GOLD = 109
    const val CUPERTINO_PISTACHIO = 110
    const val CUPERTINO_SUNFLOWER = 111
    const val CUPERTINO_VIOLET = 112
    const val CUPERTINO_WHITE = 113
    const val CUPERTINO_SKY = 114
    const val CUPERTINO_MORANDI = 115
    const val CUPERTINO_WINE = 116
    const val CUPERTINO_SAGE = 117
    const val CUPERTINO_WHEAT = 118
    const val CUPERTINO_ICE = 119
    const val CUPERTINO_EMERALD = 120
    const val CUPERTINO_CHAMPAGNE = 121
    const val CUPERTINO_SILVER = 122
    const val CUPERTINO_UNICORN = 123

    private fun defaultTheme() = CUPERTINO_PINK

    fun apply(context: Context) {
        context.setTheme(getTheme())
    }

    fun applyDialog(context: Context) {
        context.setTheme(getDialogTheme())
    }

    fun getTheme(): Int {
        if (DataStore.customColorEnabled && DataStore.customColor != 0) {
            return getTheme(findClosestTheme(DataStore.customColor))
        }
        return getTheme(DataStore.appTheme)
    }

    fun getDialogTheme(): Int {
        if (DataStore.customColorEnabled && DataStore.customColor != 0) {
            return getDialogTheme(findClosestTheme(DataStore.customColor))
        }
        return getDialogTheme(DataStore.appTheme)
    }

    fun getTheme(theme: Int): Int {
        return when (theme) {
            RED -> R.style.Theme_SagerNet_Red
            PINK -> R.style.Theme_SagerNet
            PURPLE -> R.style.Theme_SagerNet_Purple
            DEEP_PURPLE -> R.style.Theme_SagerNet_DeepPurple
            INDIGO -> R.style.Theme_SagerNet_Indigo
            BLUE -> R.style.Theme_SagerNet_Blue
            LIGHT_BLUE -> R.style.Theme_SagerNet_LightBlue
            CYAN -> R.style.Theme_SagerNet_Cyan
            TEAL -> R.style.Theme_SagerNet_Teal
            GREEN -> R.style.Theme_SagerNet_Green
            LIGHT_GREEN -> R.style.Theme_SagerNet_LightGreen
            LIME -> R.style.Theme_SagerNet_Lime
            YELLOW -> R.style.Theme_SagerNet_Yellow
            AMBER -> R.style.Theme_SagerNet_Amber
            ORANGE -> R.style.Theme_SagerNet_Orange
            DEEP_ORANGE -> R.style.Theme_SagerNet_DeepOrange
            BROWN -> R.style.Theme_SagerNet_Brown
            GREY -> R.style.Theme_SagerNet_Grey
            BLUE_GREY -> R.style.Theme_SagerNet_BlueGrey
            BLACK -> if (usingNightMode()) R.style.Theme_SagerNet_Black else R.style.Theme_SagerNet_LightBlack
            VERDANT_MINT -> R.style.Theme_SagerNet_VerdantMint
            DYNAMIC -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) R.style.Theme_SagerNet_Dynamic else getTheme(defaultTheme())
            CUPERTINO_PINK -> R.style.Theme_SagerNet_Cupertino_Pink
            CUPERTINO_BLUE -> R.style.Theme_SagerNet_Cupertino_Blue
            CUPERTINO_ORANGE -> R.style.Theme_SagerNet_Cupertino_Orange
            CUPERTINO_GREEN -> R.style.Theme_SagerNet_Cupertino_Green
            CUPERTINO_PURPLE -> R.style.Theme_SagerNet_Cupertino_Purple
            CUPERTINO_AMBER -> R.style.Theme_SagerNet_Cupertino_Amber
            CUPERTINO_MINT -> R.style.Theme_SagerNet_Cupertino_Mint
            CUPERTINO_CORAL -> R.style.Theme_SagerNet_Cupertino_Coral
            CUPERTINO_ROSE_GOLD -> R.style.Theme_SagerNet_Cupertino_RoseGold
            CUPERTINO_PISTACHIO -> R.style.Theme_SagerNet_Cupertino_Pistachio
            CUPERTINO_SUNFLOWER -> R.style.Theme_SagerNet_Cupertino_Sunflower
            CUPERTINO_VIOLET -> R.style.Theme_SagerNet_Cupertino_Violet
            CUPERTINO_WHITE -> R.style.Theme_SagerNet_Cupertino_White
            CUPERTINO_SKY -> R.style.Theme_SagerNet_Cupertino_Sky
            CUPERTINO_MORANDI -> R.style.Theme_SagerNet_Cupertino_Morandi
            CUPERTINO_WINE -> R.style.Theme_SagerNet_Cupertino_Wine
            CUPERTINO_SAGE -> R.style.Theme_SagerNet_Cupertino_Sage
            CUPERTINO_WHEAT -> R.style.Theme_SagerNet_Cupertino_Wheat
            CUPERTINO_ICE -> R.style.Theme_SagerNet_Cupertino_Ice
            CUPERTINO_EMERALD -> R.style.Theme_SagerNet_Cupertino_Emerald
            CUPERTINO_CHAMPAGNE -> R.style.Theme_SagerNet_Cupertino_Champagne
            CUPERTINO_SILVER -> R.style.Theme_SagerNet_Cupertino_Silver
            CUPERTINO_UNICORN -> R.style.Theme_SagerNet_Cupertino_Unicorn
            else -> getTheme(defaultTheme())
        }
    }

    fun getDialogTheme(theme: Int): Int {
        return when (theme) {
            RED -> R.style.Theme_SagerNet_Dialog_Red
            PINK -> R.style.Theme_SagerNet_Dialog
            PURPLE -> R.style.Theme_SagerNet_Dialog_Purple
            DEEP_PURPLE -> R.style.Theme_SagerNet_Dialog_DeepPurple
            INDIGO -> R.style.Theme_SagerNet_Dialog_Indigo
            BLUE -> R.style.Theme_SagerNet_Dialog_Blue
            LIGHT_BLUE -> R.style.Theme_SagerNet_Dialog_LightBlue
            CYAN -> R.style.Theme_SagerNet_Dialog_Cyan
            TEAL -> R.style.Theme_SagerNet_Dialog_Teal
            GREEN -> R.style.Theme_SagerNet_Dialog_Green
            LIGHT_GREEN -> R.style.Theme_SagerNet_Dialog_LightGreen
            LIME -> R.style.Theme_SagerNet_Dialog_Lime
            YELLOW -> R.style.Theme_SagerNet_Dialog_Yellow
            AMBER -> R.style.Theme_SagerNet_Dialog_Amber
            ORANGE -> R.style.Theme_SagerNet_Dialog_Orange
            DEEP_ORANGE -> R.style.Theme_SagerNet_Dialog_DeepOrange
            BROWN -> R.style.Theme_SagerNet_Dialog_Brown
            GREY -> R.style.Theme_SagerNet_Dialog_Grey
            BLUE_GREY -> R.style.Theme_SagerNet_Dialog_BlueGrey
            BLACK -> if (usingNightMode()) R.style.Theme_SagerNet_Dialog_Black else R.style.Theme_SagerNet_Dialog_LightBlack
            VERDANT_MINT -> R.style.Theme_SagerNet_Dialog_VerdantMint
            DYNAMIC -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) R.style.Theme_SagerNet_Dialog_Dynamic else getDialogTheme(defaultTheme())
            CUPERTINO_PINK -> R.style.Theme_SagerNet_Dialog_Cupertino_Pink
            CUPERTINO_BLUE -> R.style.Theme_SagerNet_Dialog_Cupertino_Blue
            CUPERTINO_ORANGE -> R.style.Theme_SagerNet_Dialog_Cupertino_Orange
            CUPERTINO_GREEN -> R.style.Theme_SagerNet_Dialog_Cupertino_Green
            CUPERTINO_PURPLE -> R.style.Theme_SagerNet_Dialog_Cupertino_Purple
            CUPERTINO_AMBER -> R.style.Theme_SagerNet_Dialog_Cupertino_Amber
            CUPERTINO_MINT -> R.style.Theme_SagerNet_Dialog_Cupertino_Mint
            CUPERTINO_CORAL -> R.style.Theme_SagerNet_Dialog_Cupertino_Coral
            CUPERTINO_ROSE_GOLD -> R.style.Theme_SagerNet_Dialog_Cupertino_RoseGold
            CUPERTINO_PISTACHIO -> R.style.Theme_SagerNet_Dialog_Cupertino_Pistachio
            CUPERTINO_SUNFLOWER -> R.style.Theme_SagerNet_Dialog_Cupertino_Sunflower
            CUPERTINO_VIOLET -> R.style.Theme_SagerNet_Dialog_Cupertino_Violet
            CUPERTINO_WHITE -> R.style.Theme_SagerNet_Dialog_Cupertino_White
            CUPERTINO_SKY -> R.style.Theme_SagerNet_Dialog_Cupertino_Sky
            CUPERTINO_MORANDI -> R.style.Theme_SagerNet_Dialog_Cupertino_Morandi
            CUPERTINO_WINE -> R.style.Theme_SagerNet_Dialog_Cupertino_Wine
            CUPERTINO_SAGE -> R.style.Theme_SagerNet_Dialog_Cupertino_Sage
            CUPERTINO_WHEAT -> R.style.Theme_SagerNet_Dialog_Cupertino_Wheat
            CUPERTINO_ICE -> R.style.Theme_SagerNet_Dialog_Cupertino_Ice
            CUPERTINO_EMERALD -> R.style.Theme_SagerNet_Dialog_Cupertino_Emerald
            CUPERTINO_CHAMPAGNE -> R.style.Theme_SagerNet_Dialog_Cupertino_Champagne
            CUPERTINO_SILVER -> R.style.Theme_SagerNet_Dialog_Cupertino_Silver
            CUPERTINO_UNICORN -> R.style.Theme_SagerNet_Dialog_Cupertino_Unicorn
            else -> getDialogTheme(defaultTheme())
        }
    }

    var currentNightMode = -1
    fun getNightMode(): Int {
        if (currentNightMode == -1) {
            currentNightMode = DataStore.nightTheme
        }
        return getNightMode(currentNightMode)
    }

    fun getNightMode(mode: Int): Int {
        return when (mode) {
            0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            1 -> AppCompatDelegate.MODE_NIGHT_YES
            2 -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
        }
    }

    fun usingNightMode(): Boolean {
        return when (DataStore.nightTheme) {
            1 -> true
            2 -> false
            else -> (app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }

    fun applyNightTheme() {
        AppCompatDelegate.setDefaultNightMode(getNightMode())
    }

    fun isCupertino(theme: Int = DataStore.appTheme): Boolean {
        return theme in CUPERTINO_PINK..CUPERTINO_UNICORN
    }

    fun isWallpaperTheme(): Boolean {
        return DataStore.wallpaperThemeEnabled && DataStore.currentWallpaperPath.isNotEmpty()
    }

    fun getAccentColor(context: Context): Int {
        if (DataStore.customColorEnabled && DataStore.customColor != 0) {
            return DataStore.customColor
        }
        return context.getColorAttr(androidx.appcompat.R.attr.colorAccent)
    }

    fun findClosestTheme(targetColor: Int): Int {
        val cupertinoColors = app.resources.getIntArray(R.array.cupertino_colors)
        var closestTheme = CUPERTINO_PINK
        var minDistance = Double.MAX_VALUE

        for ((i, color) in cupertinoColors.withIndex()) {
            val dist = colorDistance(targetColor, color)
            if (dist < minDistance) {
                minDistance = dist
                closestTheme = i + 101
            }
        }
        return closestTheme
    }

    private fun colorDistance(c1: Int, c2: Int): Double {
        val rDiff = Color.red(c1) - Color.red(c2)
        val gDiff = Color.green(c1) - Color.green(c2)
        val bDiff = Color.blue(c1) - Color.blue(c2)
        return (rDiff * rDiff + gDiff * gDiff + bDiff * bDiff).toDouble()
    }

    fun getCustomColors(): MutableList<Int> {
        val raw = DataStore.customColorsList
        if (raw.isBlank()) return mutableListOf()
        return raw.split(",")
            .mapNotNull {
                it.trim().toLongOrNull(16)?.toInt()
            }
            .toMutableList()
    }

    fun saveCustomColors(colors: List<Int>) {
        DataStore.customColorsList = colors.joinToString(",") { Integer.toHexString(it) }
    }

    fun addCustomColor(color: Int) {
        val list = getCustomColors()
        if (!list.contains(color)) {
            list.add(0, color)
            saveCustomColors(list)
        }
    }

    fun deleteCustomColor(color: Int) {
        val list = getCustomColors()
        list.remove(color)
        saveCustomColors(list)
        if (DataStore.customColor == color) {
            if (list.isNotEmpty()) {
                DataStore.customColor = list.first()
            } else {
                DataStore.customColorEnabled = false
            }
        }
    }

    fun getCardBackgroundColor(context: Context, isSelected: Boolean = false): Int {
        val isNight = usingNightMode()
        val isWallpaper = isWallpaperTheme()
        val isPureBlack = isNight && DataStore.amoledPureBlack
        val accent = getAccentColor(context)

        if (isPureBlack && !isWallpaper) {
            val baseBlack = Color.rgb(8, 8, 8)
            return if (isSelected) {
                ColorUtils.compositeColors(
                    ColorUtils.setAlphaComponent(accent, 55),
                    baseBlack
                )
            } else {
                baseBlack
            }
        }

        // FlClash dynamic card glass opacity
        val opacityPercent = (DataStore.cardGlassOpacity.coerceIn(20, 100)) / 100f
        val baseAlpha = kotlin.math.round((255 * opacityPercent)).toInt()

        val baseGlass = if (isNight) {
            Color.argb(baseAlpha, 22, 22, 26)
        } else {
            Color.argb(baseAlpha, 255, 255, 255)
        }

        return if (isSelected) {
            val selectAlpha = if (isNight) 55 else 48
            ColorUtils.compositeColors(
                ColorUtils.setAlphaComponent(accent, selectAlpha),
                baseGlass
            )
        } else {
            baseGlass
        }
    }

    fun getCardStrokeColor(context: Context, isSelected: Boolean = false): Int {
        val isNight = usingNightMode()
        val accent = getAccentColor(context)

        if (isSelected) {
            return ColorUtils.setAlphaComponent(accent, 190)
        }

        // Pure physical specular highlight rim, zero theme color contamination
        return if (isNight) {
            Color.argb(45, 255, 255, 255)
        } else {
            Color.argb(25, 0, 0, 0)
        }
    }

    fun getClassicListBackgroundColor(): Int {
        val isNight = usingNightMode()
        return if (isNight) {
            if (DataStore.amoledPureBlack) Color.BLACK else Color.rgb(18, 18, 20)
        } else {
            Color.rgb(0xF2, 0xF2, 0xF7)
        }
    }

}