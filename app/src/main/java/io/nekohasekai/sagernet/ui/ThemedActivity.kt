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

package io.nekohasekai.sagernet.ui

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.applyGlassBlur
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getBooleanProperty
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.utils.WallpaperManager

abstract class ThemedActivity : AppCompatActivity {
    constructor() : super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    enum class Type {
        Default,
        Dialog
    }

    open val type = Type.Default

    var themeResId = 0
    var uiMode = 0

    private var backdropContainer: FrameLayout? = null
    private var backdropImage: ImageView? = null
    private var backdropGlow: View? = null
    private var lastBackdropKey: String? = null

    private val setNavigationBarColor = DataStore.experimentalFlagsProperties.getBooleanProperty("setNavigationBarColor")

    override fun onCreate(savedInstanceState: Bundle?) {
        when (type) {
            Type.Default -> {
                Theme.apply(this)
            }
            Type.Dialog -> {
                Theme.applyDialog(this)
            }
        }
        Theme.applyNightTheme()

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // https://stackoverflow.com/questions/79319740/edge-to-edge-doesnt-work-when-activity-recreated-or-appcompatdelegate-setdefaul
            // BAKLAVA and later VANILLA_ICE_CREAM have fixed this
            // set this before super.onCreate(savedInstanceState)
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }

        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = 60 }
        }
        if (Theme.usingNightMode() && DataStore.amoledPureBlack && !Theme.isWallpaperTheme()) {
            window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        } else if (Theme.isCupertino()) {
            window.setBackgroundDrawableResource(R.color.cupertino_window_bg)
        } else {
            window.setBackgroundDrawable(ColorDrawable(Theme.getClassicListBackgroundColor()))
        }
        uiMode = resources.configuration.uiMode

        onBackPressedCallback?.let {
            onBackPressedDispatcher.addCallback(this, it)
        }

        val applyFragmentGlassBlur = { f: androidx.fragment.app.Fragment ->
            if (f is DialogFragment) {
                try {
                    val isBottom = f is com.google.android.material.bottomsheet.BottomSheetDialogFragment
                    f.dialog?.applyGlassBlur(isBottomSheet = isBottom)
                } catch (_: Exception) {}
            }
        }

        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentStarted(fm: FragmentManager, f: androidx.fragment.app.Fragment) {
                applyFragmentGlassBlur(f)
            }
            override fun onFragmentResumed(fm: FragmentManager, f: androidx.fragment.app.Fragment) {
                applyFragmentGlassBlur(f)
            }
        }, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val insetController = WindowCompat.getInsetsController(window, window.decorView)
            if (setNavigationBarColor) {
                insetController.isAppearanceLightNavigationBars =
                    if (DataStore.appTheme == Theme.BLACK) !Theme.usingNightMode() else false
                @Suppress("DEPRECATION")
                window.navigationBarColor = getColorAttr(androidx.appcompat.R.attr.colorPrimaryDark)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = true
                }
            } else {
                insetController.isAppearanceLightNavigationBars = !Theme.usingNightMode()
            }
            insetController.isAppearanceLightStatusBars =
                if (DataStore.appTheme == Theme.BLACK || DataStore.appTheme >= 100) !Theme.usingNightMode() else false
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            findViewById<AppBarLayout>(R.id.appbar)?.apply {
                updatePadding(
                    top = bars.top,
                    left = bars.left,
                    right = bars.right,
                )
            }
            insets
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        updateSubpageBackdrop()
    }

    override fun onResume() {
        super.onResume()
        updateSubpageBackdrop()
    }

    private fun updateSubpageBackdrop() {
        if (type != Type.Default || this is MainActivity) return
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return

        val key = listOf(
            Theme.usingNightMode(),
            DataStore.amoledPureBlack,
            Theme.isWallpaperTheme(),
            Theme.isCupertino(),
            DataStore.currentWallpaperPath,
            DataStore.wallpaperBlurRadius,
            Theme.getAccentColor(this),
            themeResId,
        ).joinToString("|")
        if (backdropContainer != null && lastBackdropKey == key) return
        lastBackdropKey = key

        if (backdropContainer == null) {
            val container = FrameLayout(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            val bgImage = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val glowSize = dp2px(400)
            val glow = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(glowSize, glowSize, Gravity.CENTER)
                setBackgroundResource(R.drawable.bg_ambient_glow)
                alpha = 0.1f
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            container.addView(bgImage)
            container.addView(glow)
            content.addView(container, 0)
            backdropContainer = container
            backdropImage = bgImage
            backdropGlow = glow
        }

        val container = backdropContainer ?: return
        val bgImage = backdropImage ?: return
        WallpaperManager.applyWindowBackdrop(
            container = container,
            bgImage = bgImage,
            ambientGlow = backdropGlow,
            showMeshWhenDefault = Theme.isCupertino(),
        )
    }

    override fun setTheme(resId: Int) {
        super.setTheme(resId)

        themeResId = resId
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (newConfig.uiMode != uiMode) {
            uiMode = newConfig.uiMode

            if (DataStore.appTheme == Theme.BLACK || DataStore.appTheme >= 100) {
                Theme.apply(this)
            }

            ActivityCompat.recreate(this)
        }
    }

    fun snackbar(@StringRes resId: Int): Snackbar = snackbar("").setText(resId)
    fun snackbar(text: CharSequence): Snackbar = snackbarInternal(text).apply {
        view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).apply {
            maxLines = 10
        }
    }

    internal open fun snackbarInternal(text: CharSequence): Snackbar = throw NotImplementedError()

    open val onBackPressedCallback: OnBackPressedCallback? get() = null

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        return try {
            super.dispatchTouchEvent(ev)
        } catch (e: IllegalStateException) {
            if (e.message?.contains("already recycled") == true) {
                true
            } else {
                throw e
            }
        } catch (e: IllegalArgumentException) {
            true
        }
    }

}