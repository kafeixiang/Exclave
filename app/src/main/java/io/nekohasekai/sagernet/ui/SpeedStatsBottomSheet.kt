package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.AppStats
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.databinding.ItemSpeedStatRowBinding
import io.nekohasekai.sagernet.databinding.LayoutSpeedStatsSheetBinding
import io.nekohasekai.sagernet.ktx.applyGlassBlur
import io.nekohasekai.sagernet.ktx.applyItemEntryAnimation
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.utils.FormatFileSizeCompat
import io.nekohasekai.sagernet.utils.PackageCache

class SpeedStatsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: LayoutSpeedStatsSheetBinding? = null
    private val binding get() = _binding!!
    private val adapter = SpeedStatsAdapter()
    private var rawStatsList: List<AppStats> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutSpeedStatsSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.applyGlassBlur()

        binding.sheetRecycler.layoutManager = LinearLayoutManager(context)
        binding.sheetRecycler.adapter = adapter

        processAndDisplayStats(rawStatsList)
    }

    override fun onStart() {
        super.onStart()
        (activity as? MainActivity)?.connection?.trafficTimeout = 1000
    }

    fun updateStats(statsList: List<AppStats>) {
        rawStatsList = statsList
        if (_binding != null) {
            processAndDisplayStats(statsList)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun processAndDisplayStats(statsList: List<AppStats>) {
        runOnDefaultDispatcher {
            val map = statsList.associate { it.uid to it.copy() }.toMutableMap()

            val dbStatsList = try {
                SagerDatabase.statsDao.all()
            } catch (_: Exception) {
                emptyList()
            }

            for (s in dbStatsList) {
                if (map.containsKey(s.uid)) {
                    map[s.uid]!! += s
                } else {
                    map[s.uid] = s.toStats()
                }
            }

            for (s in map.values) {
                s.tcpConnectionsTotal += s.tcpConnections
                s.udpConnectionsTotal += s.udpConnections
                s.uplinkTotal += s.uplink
                s.downlinkTotal += s.downlink
            }

            val sortedList = map.values.sortedByDescending { it.uplinkTotal + it.downlinkTotal }

            runOnMainDispatcher {
                if (_binding == null) return@runOnMainDispatcher
                if (sortedList.isEmpty()) {
                    binding.emptyText.isVisible = true
                    binding.sheetRecycler.isVisible = false
                    adapter.data = emptyList()
                } else {
                    binding.emptyText.isVisible = false
                    binding.sheetRecycler.isVisible = true
                    adapter.data = sortedList
                }
                adapter.notifyDataSetChanged()
            }
        }
    }

    private class SpeedStatsAdapter : RecyclerView.Adapter<SpeedStatsAdapter.ViewHolder>() {

        var data: List<AppStats> = emptyList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ItemSpeedStatRowBinding.inflate(inflater, parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.itemView.applyItemEntryAnimation(position)
            holder.bind(data[position])
        }

        override fun getItemCount(): Int = data.size

        class ViewHolder(val binding: ItemSpeedStatRowBinding) :
            RecyclerView.ViewHolder(binding.root) {

            @SuppressLint("SetTextI18n")
            fun bind(stats: AppStats) {
                val context = itemView.context
                val packageName = PackageCache.uidMap[stats.uid]?.firstOrNull()
                val label = if (packageName != null) PackageCache.loadLabel(packageName) else "UID ${stats.uid}"

                val icon = try {
                    if (packageName != null) context.packageManager.getApplicationIcon(packageName) else null
                } catch (_: Exception) {
                    null
                }

                binding.appName.text = label
                if (icon != null) {
                    binding.appIcon.imageTintList = null
                    binding.appIcon.setImageDrawable(icon)
                } else {
                    binding.appIcon.setImageResource(R.drawable.ic_baseline_speed_24)
                    binding.appIcon.imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.material_grey_500)
                    )
                }

                val tcpTotal = stats.tcpConnectionsTotal
                val udpTotal = stats.udpConnectionsTotal
                binding.connectionCount.text = "TCP: $tcpTotal · UDP: $udpTotal"

                val upStr = FormatFileSizeCompat.formatFileSize(context, stats.uplinkTotal, DataStore.useIECUnit)
                val downStr = FormatFileSizeCompat.formatFileSize(context, stats.downlinkTotal, DataStore.useIECUnit)
                binding.uploadTotal.text = "⬆ $upStr"
                binding.downloadTotal.text = "⬇ $downStr"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
