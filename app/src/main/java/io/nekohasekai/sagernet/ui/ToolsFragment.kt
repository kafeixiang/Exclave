/******************************************************************************
 * Copyright (C) 2021 by nekohasekai <contact-git@sekai.icu>                  *
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

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutToolsBinding

class ToolsFragment : ToolbarFragment(R.layout.layout_tools) {

    private var _binding: LayoutToolsBinding? = null
    private var tabLayoutMediator: TabLayoutMediator? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setTitle(R.string.menu_tools)
        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.tools_tab)) { v, insets ->
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

        val binding = LayoutToolsBinding.bind(view)
        _binding = binding
        binding.toolsPager.adapter = ToolsAdapter()

        tabLayoutMediator = TabLayoutMediator(binding.toolsTab, binding.toolsPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tools_network)
                else -> getString(R.string.backup)
            }
            tab.view.setOnLongClickListener { // clear toast
                true
            }
        }.also { it.attach() }

        (requireActivity() as? MainActivity)?.onBackPressedCallback?.isEnabled = true
    }

    override fun onDestroyView() {
        tabLayoutMediator?.detach()
        tabLayoutMediator = null
        _binding?.toolsPager?.adapter = null
        _binding = null
        super.onDestroyView()
    }

    inner class ToolsAdapter : FragmentStateAdapter(this) {

        override fun getItemCount() = 2

        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> NetworkFragment()
            else -> BackupFragment()
        }
    }

}