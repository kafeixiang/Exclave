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

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.util.Linkify
import android.view.View
import android.widget.ArrayAdapter
import android.widget.HorizontalScrollView
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutAboutBinding
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.Theme
import libexclavecore.Libexclavecore

class AboutFragment : ToolbarFragment(R.layout.layout_about) {

    private var _binding: LayoutAboutBinding? = null
    private val binding get() = _binding!!

    private val requestIgnoreBatteryOptimizations = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateBatteryOptimizationState()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = LayoutAboutBinding.bind(view)

        if (!Theme.isCupertino()) {
            binding.layoutAbout.setBackgroundColor(Theme.getClassicListBackgroundColor())
        }

        toolbar.setTitle(R.string.menu_about)

        binding.cardVersion.applyGlassBlur()
        binding.cardLicense.applyGlassBlur()

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
            )
            binding.aboutScrollView.updatePadding(
                bottom = bars.bottom + dp2px(112),
            )
            insets
        }

        binding.aboutScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            val dy = scrollY - oldScrollY
            val activity = activity as? MainActivity ?: return@OnScrollChangeListener
            if (!activity.isFinishing && !activity.isDestroyed) {
                if (dy > 6) {
                    activity.binding.cupertinoDock.hidePanel()
                } else if (dy < -6) {
                    activity.binding.cupertinoDock.showPanel()
                }
            }
        })

        // Card 1: Version Details
        binding.itemAppTitle.setOnLongClickListener {
            showLibrariesDialog()
            true
        }

        binding.textAppVersion.text = BuildConfig.VERSION_NAME
        binding.itemAppVersion.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_VIEW,
                "https://github.com/ExclaveNetwork/Exclave/releases".toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
        binding.itemAppVersion.setOnLongClickListener {
            DataStore.enableDebug = !DataStore.enableDebug
            snackbar(if (DataStore.enableDebug) "developer mode enabled" else "developer mode disabled").show()
            true
        }

        binding.titleCoreVersion.text = getString(R.string.version_x, "exclave-core")
        binding.textCoreVersion.text = Libexclavecore.getV2RayVersion()
        binding.itemCoreVersion.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_VIEW,
                "https://github.com/ExclaveNetwork/exclave-core".toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        binding.titleGoVersion.text = getString(R.string.version_x, "Go")
        binding.textGoVersion.text = Libexclavecore.getGoVersion()
        binding.itemGoVersion.setOnLongClickListener {
            showGoDepInfoDialog()
            true
        }

        updateBatteryOptimizationState()
        binding.itemIgnoreBattery.setOnClickListener {
            val ctx = requireContext()
            requestIgnoreBatteryOptimizations.launch(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    "package:${ctx.packageName}".toUri()
                )
            )
        }

        // Card 2: License Details
        binding.itemLicense.setOnClickListener {
            showGplLicenseDialog()
        }

        binding.itemThirdParty.setOnClickListener {
            showThirdPartyNoticesDialog()
        }

        (requireActivity() as? MainActivity)?.onBackPressedCallback?.isEnabled = true
    }

    private fun updateBatteryOptimizationState() {
        val b = _binding ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val ctx = context ?: return
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val needed = pm != null && !pm.isIgnoringBatteryOptimizations(ctx.packageName)
            b.dividerBattery.isVisible = needed
            b.itemIgnoreBattery.isVisible = needed
        } else {
            b.dividerBattery.isVisible = false
            b.itemIgnoreBattery.isVisible = false
        }
    }

    private fun showLibrariesDialog() {
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx).apply {
            setView(NestedScrollView(ctx).apply {
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                setPadding(dp2px(16), dp2px(16), dp2px(16), 0)
                addView(HorizontalScrollView(ctx).apply {
                    addView(TextView(ctx).apply {
                        text = resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
                        textSize = dp2pxf(4)
                        typeface = Typeface.MONOSPACE
                        isSingleLine = false
                        setTextIsSelectable(true)
                        setHorizontallyScrolling(false)
                    })
                })
            })
            setPositiveButton(android.R.string.ok, null)
        }.create().apply { applyGlassBlur() }.show()
    }

    private fun showGoDepInfoDialog() {
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx).apply {
            setView(NestedScrollView(ctx).apply {
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                setPadding(dp2px(16), dp2px(16), dp2px(16), 0)
                addView(HorizontalScrollView(ctx).apply {
                    addView(TextView(ctx).apply {
                        text = Libexclavecore.getDepInfo()
                        textSize = dp2pxf(4)
                        typeface = Typeface.MONOSPACE
                        isSingleLine = false
                        setTextIsSelectable(true)
                        setHorizontallyScrolling(false)
                    })
                })
            })
            setPositiveButton(android.R.string.ok, null)
        }.create().apply { applyGlassBlur() }.show()
    }

    private fun showGplLicenseDialog() {
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx).apply {
            setView(
                TextView(ctx).apply {
                    setPadding(dp2px(16), dp2px(16), dp2px(16), dp2px(16))
                    text = getString(
                        if (Libexclavecore.buildWithClash()) {
                            R.string.license_gpl_v3_only
                        } else {
                            R.string.license_gpl_v3_or_later
                        }
                    )
                    setTextIsSelectable(true)
                    Linkify.addLinks(this, Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS)
                }
            )
            setOnCancelListener { _ ->
                showLicenseAlertDialogFromAssets(ctx, "license/GPL-3.0.txt")
            }
            setPositiveButton(android.R.string.ok) { _, _ ->
                showLicenseAlertDialogFromAssets(ctx, "license/GPL-3.0.txt")
            }
        }.create().apply { applyGlassBlur() }.show()
    }

    private fun showThirdPartyNoticesDialog() {
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx).apply {
            setView(ListView(ctx).apply {
                adapter = ArrayAdapter(
                    ctx, android.R.layout.simple_list_item_1,
                    arrayOf(
                        getString(R.string.third_party_notices),
                        "Apache License Version 2.0",
                        "MIT License",
                        "BSD 3-Clause License",
                        "BSD 2-Clause License",
                        "GNU GENERAL PUBLIC LICENSE Version 3",
                        "Mozilla Public License Version 2.0",
                        "Creative Commons Attribution 4.0 International Public License",
                        "Community Data License Agreement - Permissive - Version 2.0"
                    )
                )
                setOnItemClickListener { _, _, position, _ ->
                    showLicenseAlertDialogFromAssets(ctx, when (position) {
                        0 -> "license/notices.txt"
                        1 -> "license/Apache-2.0.txt"
                        2 -> "license/MIT.txt"
                        3 -> "license/BSD-3-Clause.txt"
                        4 -> "license/BSD-2-Clause.txt"
                        5 -> "license/GPL-3.0.txt"
                        6 -> "license/MPL-2.0.txt"
                        7 -> "license/CC-BY-4.0.txt"
                        8 -> "license/CDLA-Permissive-2.0.txt"
                        else -> error("index out of bound")
                    })
                }
            })
            setPositiveButton(android.R.string.ok, null)
        }.create().apply { applyGlassBlur() }.show()
    }

    private fun showLicenseAlertDialogFromAssets(context: Context, asset: String) {
        MaterialAlertDialogBuilder(context).apply {
            setView(NestedScrollView(context).apply {
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                setPadding(dp2px(16), dp2px(16), dp2px(16), 0)
                addView(HorizontalScrollView(context).apply {
                    addView(TextView(context).apply {
                        text = context.assets.open(asset).use { it.reader().readText() }
                        textSize = dp2pxf(4)
                        typeface = Typeface.MONOSPACE
                        isSingleLine = false
                        setTextIsSelectable(true)
                        setHorizontallyScrolling(false)
                    })
                })
            })
            setPositiveButton(android.R.string.ok, null)
        }.create().apply { applyGlassBlur() }.show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
