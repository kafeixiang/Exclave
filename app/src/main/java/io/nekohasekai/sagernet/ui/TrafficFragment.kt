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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.AppStats
import io.nekohasekai.sagernet.databinding.LayoutTrafficBinding
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.utils.Theme
import java.util.concurrent.CopyOnWriteArrayList

class TrafficFragment : ToolbarFragment(R.layout.layout_traffic),
    Toolbar.OnMenuItemClickListener {

    private var _binding: LayoutTrafficBinding? = null
    val binding get() = _binding!!
    lateinit var adapter: TrafficAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setTitle(R.string.menu_traffic)
        toolbar.inflateMenu(R.menu.traffic_menu)
        toolbar.setOnMenuItemClickListener(this)
        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.traffic_tab)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
            )
            insets
        }

        _binding = LayoutTrafficBinding.bind(view)
        if (!Theme.isCupertino()) {
            binding.trafficPager.setBackgroundColor(Theme.getClassicListBackgroundColor())
        }
        adapter = TrafficAdapter()
        binding.trafficPager.adapter = adapter

        TabLayoutMediator(binding.trafficTab, binding.trafficPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.traffic_active)
                else -> getString(R.string.traffic_stats)
            }
            tab.view.setOnLongClickListener { // clear toast
                true
            }
        }.attach()

        (activity as? MainActivity)?.connection?.trafficTimeout = 1500

        (activity as? MainActivity)?.onBackPressedCallback?.isEnabled = true
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_clear_traffic_statistics -> {
                (activity as? MainActivity)?.connection?.service?.resetTrafficStats()
                emitStats(emptyList())
            }
        }
        return true
    }

    inner class TrafficAdapter : FragmentStateAdapter(this) {

        override fun getItemCount(): Int {
            return 2
        }

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> ActiveFragment()
                else -> StatsFragment()
            }
        }

    }

    val listeners = CopyOnWriteArrayList<(List<AppStats>) -> Unit>()

    fun emitStats(statsList: List<AppStats>) {
        runOnMainDispatcher {
            if (!isAdded) return@runOnMainDispatcher
            for (listener in listeners) listener(statsList)
        }
    }

    override fun onStart() {
        super.onStart()
        (activity as? MainActivity)?.updateTrafficTimeout()
    }

    override fun onStop() {
        super.onStop()
        (activity as? MainActivity)?.updateTrafficTimeout()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        (activity as? MainActivity)?.updateTrafficTimeout()
    }

    val createRule = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            (requireActivity() as MainActivity).ruleCreated()
        }
    }

    inner class ItemMenuListener(val stats: AppStats, val packageName: String?) : PopupMenu.OnMenuItemClickListener {
        override fun onMenuItemClick(item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.copy_label -> {
                    packageName?.let {
                        PackageCache.awaitLoadSync()
                        val success = SagerNet.trySetPrimaryClip(PackageCache.loadLabel(it))
                        snackbar(if (success) R.string.copy_success else R.string.copy_failed).show()
                    }
                }
                R.id.copy_package_name -> {
                    packageName?.let {
                        val success = SagerNet.trySetPrimaryClip(it)
                        snackbar(if (success) R.string.copy_success else R.string.copy_failed).show()
                    }
                }
                R.id.create_rule -> {
                    packageName?.let {
                        createRule.launch(Intent(
                            requireContext(), RouteSettingsActivity::class.java
                        ).apply {
                            putExtra(RouteSettingsActivity.EXTRA_PACKAGE_NAME, it)
                        })
                    }
                }
            }
            return true
        }

    }

}