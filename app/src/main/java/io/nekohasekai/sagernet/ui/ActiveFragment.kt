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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.AppStats
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutTrafficItemBinding
import io.nekohasekai.sagernet.databinding.LayoutTrafficListBinding
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.FormatFileSizeCompat
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.utils.Theme

class ActiveFragment : Fragment(R.layout.layout_traffic_list) {

    private var _binding: LayoutTrafficListBinding? = null
    val binding get() = _binding!!
    lateinit var adapter: ActiveAdapter
    private val lifecycleOwnerOrNull: () -> LifecycleOwner? = {
        if (_binding != null && isAdded) viewLifecycleOwner else null
    }
    private val statsListener: (List<AppStats>) -> Unit = { emitStats(it) }
    private val statsUpdater = AppStatsListUpdater(
        lifecycleOwnerProvider = lifecycleOwnerOrNull,
        currentDataProvider = { if (::adapter.isInitialized) adapter.data else emptyList() },
        applyResult = { sortedList, diffResult ->
            val b = _binding ?: return@AppStatsListUpdater
            if (!isAdded) return@AppStatsListUpdater
            if (sortedList.isEmpty()) {
                showEmptyHolder(b)
                adapter.setData(emptyList(), null)
            } else {
                b.holder.isVisible = false
                b.trafficList.isVisible = true
                adapter.setData(sortedList, diffResult)
            }
        }
    )

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
        _binding = LayoutTrafficListBinding.bind(view)
        if (!Theme.isCupertino()) {
            view.setBackgroundColor(Theme.getClassicListBackgroundColor())
            binding.trafficList.setBackgroundColor(Theme.getClassicListBackgroundColor())
        }
        adapter = ActiveAdapter()
        binding.trafficList.layoutManager = FixedLinearLayoutManager(binding.trafficList)
        (binding.trafficList.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        binding.trafficList.applyGlassBlur()
        binding.trafficList.adapter = adapter
        (parentFragment as? TrafficFragment)?.listeners?.add(statsListener)
        emitStats(emptyList())
    }

    private fun showEmptyHolder(b: LayoutTrafficListBinding) {
        b.holder.isVisible = true
        b.trafficList.isVisible = false

        val service = (activity as? MainActivity)?.connection?.service
        b.holder.text = when {
            !SagerNet.started || DataStore.serviceMode != Key.MODE_VPN -> getString(R.string.traffic_holder)
            service?.trafficStatsEnabled != true -> getString(R.string.app_statistics_disabled)
            else -> getString(R.string.no_statistics)
        }
    }

    fun emitStats(statsList: List<AppStats>) {
        runOnMainDispatcher {
            val b = _binding ?: return@runOnMainDispatcher
            if (!isAdded) return@runOnMainDispatcher
            if (statsList.isEmpty()) {
                statsUpdater.cancel()
                showEmptyHolder(b)
                if (adapter.data.isNotEmpty()) {
                    adapter.setData(emptyList(), null)
                }
                return@runOnMainDispatcher
            }
            val snapshot = statsList.toList()
            statsUpdater.submit(
                computeList = {
                    val now = System.currentTimeMillis() / 1000
                    snapshot.filter { it.deactivateAt == 0 || now - it.deactivateAt < 5 }
                        .map { it.copy() }
                        .sortedWith { a, b ->
                            val dataA = a.uplink + a.downlink
                            val dataB = b.uplink + b.downlink
                            if (dataA != dataB) {
                                dataB.compareTo(dataA)
                            } else {
                                val connA = a.tcpConnections + a.udpConnections
                                val connB = b.tcpConnections + b.udpConnections
                                if (connA != connB) {
                                    connB.compareTo(connA)
                                } else {
                                    b.uid.compareTo(a.uid)
                                }
                            }
                        }
                },
                diffCallbackFactory = { oldList, list ->
                    object : DiffUtil.Callback() {
                        override fun getOldListSize(): Int = oldList.size
                        override fun getNewListSize(): Int = list.size
                        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                            return oldList[oldItemPosition].uid == list[newItemPosition].uid
                        }
                        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                            val o = oldList[oldItemPosition]
                            val n = list[newItemPosition]
                            return o.tcpConnections == n.tcpConnections &&
                                    o.udpConnections == n.udpConnections &&
                                    o.uplink == n.uplink &&
                                    o.downlink == n.downlink &&
                                    o.uplinkTotal == n.uplinkTotal &&
                                    o.downlinkTotal == n.downlinkTotal
                        }
                        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any {
                            return "payload_traffic_update"
                        }
                    }
                }
            )
        }
    }

    inner class ActiveAdapter : RecyclerView.Adapter<ActiveViewHolder>() {

        init {
            setHasStableIds(true)
        }

        var data: List<AppStats> = emptyList()
            private set

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
                LayoutTrafficItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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

        override fun getItemCount(): Int = data.size
    }

    inner class ActiveViewHolder(val binding: LayoutTrafficItemBinding) : RecyclerView.ViewHolder(
        binding.root
    ) {

        fun updateTrafficStats(stats: AppStats) {
            val ctx = itemView.context
            binding.tcpConnections.text = ctx.resources.getQuantityString(R.plurals.tcp_connections, stats.tcpConnections, stats.tcpConnections)
            binding.udpConnections.text = ctx.resources.getQuantityString(R.plurals.udp_connections, stats.udpConnections, stats.udpConnections)
            binding.trafficUplink.text = ctx.getString(
                R.string.traffic_uplink,
                FormatFileSizeCompat.formatFileSize(ctx, stats.uplinkTotal, DataStore.useIECUnit),
                FormatFileSizeCompat.formatFileSize(ctx, stats.uplink, DataStore.useIECUnit)
            )
            binding.trafficDownlink.text = ctx.getString(
                R.string.traffic_downlink,
                FormatFileSizeCompat.formatFileSize(ctx, stats.downlinkTotal, DataStore.useIECUnit),
                FormatFileSizeCompat.formatFileSize(ctx, stats.downlink, DataStore.useIECUnit)
            )
        }

        fun bind(stats: AppStats) {
            val packageName = PackageCache.resolvePackageName(stats.uid)

            binding.menu.setOnClickListener {
                val trafficParent = parentFragment as? TrafficFragment ?: return@setOnClickListener
                val popup = PopupMenu(it.context, it)
                popup.menuInflater.inflate(R.menu.traffic_item_menu, popup.menu)
                popup.setOnMenuItemClickListener(trafficParent.ItemMenuListener(stats, packageName))
                popup.show()
            }

            binding.label.text = packageName?.let {
                PackageCache.loadLabel(it)
            } ?: "UID ${stats.uid}"
            binding.desc.text = packageName?.let {
                "$packageName (${stats.uid})"
            } ?: "UID ${stats.uid} (${stats.uid})"

            updateTrafficStats(stats)

            binding.icon.bindAppIcon(
                lifecycleOwnerProvider = lifecycleOwnerOrNull,
                uid = stats.uid,
                packageName = packageName,
                fallbackRes = android.R.drawable.sym_def_app_icon,
            )
        }

    }

    override fun onDestroyView() {
        (parentFragment as? TrafficFragment)?.listeners?.remove(statsListener)
        statsUpdater.cancel()
        _binding?.trafficList?.adapter = null
        super.onDestroyView()
        _binding = null
    }

}
