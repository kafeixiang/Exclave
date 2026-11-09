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

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.AppStats
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.databinding.LayoutTrafficItemBinding
import io.nekohasekai.sagernet.databinding.LayoutTrafficListBinding
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.FormatFileSizeCompat
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.utils.Theme

class StatsFragment : Fragment(R.layout.layout_traffic_list) {

    lateinit var binding: LayoutTrafficListBinding
    lateinit var adapter: ActiveAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.holder)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                bottom = bars.bottom + dp2px(112),
            )
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.traffic_list)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left + dp2px(4),
                right = bars.right + dp2px(4),
                bottom = bars.bottom + dp2px(112),
            )
            insets
        }
        binding = LayoutTrafficListBinding.bind(view)
        if (!Theme.isCupertino()) {
            view.setBackgroundColor(Theme.getClassicListBackgroundColor())
            binding.trafficList.setBackgroundColor(Theme.getClassicListBackgroundColor())
        }
        adapter = ActiveAdapter()
        binding.trafficList.layoutManager = FixedLinearLayoutManager(binding.trafficList)
        (binding.trafficList.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        binding.trafficList.applyGlassBlur()
        binding.trafficList.adapter = adapter

        (parentFragment as TrafficFragment).listeners.add(::emitStats)

        runOnDefaultDispatcher {
            emitStats(emptyList())
        }
    }

    fun emitStats(statsList: List<AppStats>) {
        val oldList = if (::adapter.isInitialized && adapter.isDataInitialized) adapter.data else emptyList()
        runOnDefaultDispatcher {
            val data = statsList.associate { it.uid to it.copy() }.toMutableMap()
            for (stats in SagerDatabase.statsDao.all()) {
                if (data.containsKey(stats.uid)) {
                    data[stats.uid]!! += stats
                } else {
                    data[stats.uid] = stats.toStats()
                }
            }
            for (stats in data.values) {
                stats.tcpConnectionsTotal += stats.tcpConnections
                stats.udpConnectionsTotal += stats.udpConnections
                stats.uplinkTotal += stats.uplink
                stats.downlinkTotal += stats.downlink
            }
            if (data.isEmpty()) {
                runOnMainDispatcher {
                    if (!isAdded) return@runOnMainDispatcher
                    binding.holder.isVisible = true
                    binding.trafficList.isVisible = false

                    if (!SagerNet.started || DataStore.serviceMode != Key.MODE_VPN) {
                        binding.holder.text = getString(R.string.traffic_holder)
                    } else if ((activity as MainActivity).connection.service?.trafficStatsEnabled != true) {
                        binding.holder.text = getString(R.string.app_statistics_disabled)
                    } else {
                        binding.holder.text = getString(R.string.no_statistics)
                    }
                    adapter.setData(emptyList(), null)
                }
            } else {
                val sortedList = data.values.sortedWith { a, b ->
                    val dataA = a.uplinkTotal + a.downlinkTotal
                    val dataB = b.uplinkTotal + b.downlinkTotal
                    if (dataA != dataB) {
                        dataB.compareTo(dataA)
                    } else {
                        val connA = a.tcpConnectionsTotal + a.udpConnectionsTotal
                        val connB = b.tcpConnectionsTotal + b.udpConnectionsTotal
                        if (connA != connB) {
                            connB.compareTo(connA)
                        } else {
                            b.uid.compareTo(a.uid)
                        }
                    }
                }

                val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                    override fun getOldListSize(): Int = oldList.size
                    override fun getNewListSize(): Int = sortedList.size
                    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                        return oldList[oldItemPosition].uid == sortedList[newItemPosition].uid
                    }
                    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                        val o = oldList[oldItemPosition]
                        val n = sortedList[newItemPosition]
                        return o.tcpConnectionsTotal == n.tcpConnectionsTotal &&
                                o.udpConnectionsTotal == n.udpConnectionsTotal &&
                                o.uplinkTotal == n.uplinkTotal &&
                                o.downlinkTotal == n.downlinkTotal &&
                                o.uplink == n.uplink &&
                                o.downlink == n.downlink
                    }
                    override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any {
                        return "payload_stats_update"
                    }
                })

                runOnMainDispatcher {
                    if (!isAdded) return@runOnMainDispatcher
                    binding.holder.isVisible = false
                    binding.trafficList.isVisible = true
                    adapter.setData(sortedList, diffResult)
                }
            }
        }
    }

    inner class ActiveAdapter : RecyclerView.Adapter<ActiveViewHolder>() {

        init {
            setHasStableIds(true)
        }

        var data: List<AppStats> = emptyList()
            private set
        val isDataInitialized get() = data.isNotEmpty()

        fun setData(newList: List<AppStats>, diffResult: DiffUtil.DiffResult?) {
            data = newList
            if (diffResult != null) {
                diffResult.dispatchUpdatesTo(this)
            } else {
                notifyDataSetChanged()
            }
        }

        override fun getItemId(position: Int): Long {
            return data.getOrNull(position)?.uid?.toLong() ?: RecyclerView.NO_ID
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActiveViewHolder {
            return ActiveViewHolder(
                LayoutTrafficItemBinding.inflate(layoutInflater, parent, false)
            )
        }

        override fun onBindViewHolder(holder: ActiveViewHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.isNotEmpty()) {
                holder.updateTrafficStats(data[position])
            } else {
                super.onBindViewHolder(holder, position, payloads)
            }
        }

        override fun onBindViewHolder(holder: ActiveViewHolder, position: Int) {
            holder.bind(data[position])
        }

        override fun getItemCount(): Int {
            return data.size
        }
    }

    inner class ActiveViewHolder(val binding: LayoutTrafficItemBinding) : RecyclerView.ViewHolder(
        binding.root
    ) {

        fun updateTrafficStats(stats: AppStats) {
            binding.tcpConnections.text = resources.getQuantityString(R.plurals.tcp_connections, stats.tcpConnectionsTotal, stats.tcpConnectionsTotal)
            binding.udpConnections.text = resources.getQuantityString(R.plurals.udp_connections, stats.udpConnectionsTotal, stats.udpConnectionsTotal)
            binding.trafficUplink.text = getString(
                R.string.traffic_uplink_total,
                FormatFileSizeCompat.formatFileSize(requireContext(), stats.uplinkTotal, DataStore.useIECUnit)
            )
            binding.trafficDownlink.text = getString(
                R.string.traffic_downlink_total,
                FormatFileSizeCompat.formatFileSize(requireContext(), stats.downlinkTotal, DataStore.useIECUnit)
            )
        }

        fun bind(stats: AppStats) {
            PackageCache.awaitLoadSync()

            val packageName = when (stats.uid) {
                1000 -> "android"
                else -> PackageCache.uidMap[stats.uid]?.iterator()?.next()
            }

            binding.menu.setOnClickListener {
                val popup = PopupMenu(requireContext(), it)
                popup.menuInflater.inflate(R.menu.traffic_item_menu, popup.menu)
                popup.setOnMenuItemClickListener(
                    (requireParentFragment() as TrafficFragment).ItemMenuListener(stats, packageName)
                )
                popup.show()
            }

            binding.label.text = packageName?.let {
                PackageCache.loadLabel(it)
            } ?: "UID ${stats.uid}"
            binding.desc.text = packageName?.let {
                "$packageName (${stats.uid})"
            } ?: "UID ${stats.uid} (${stats.uid})"

            updateTrafficStats(stats)

            val info = packageName?.let {
                PackageCache.installedApps[packageName]
            } ?: PackageCache.installedApps["android"]
            if (info != null) runOnDefaultDispatcher {
                try {
                    val icon = info.loadIcon(app.packageManager)
                    onMainDispatcher {
                        binding.icon.setImageDrawable(icon)
                    }
                } catch (_: Exception) {
                }
            }
        }

    }

}
