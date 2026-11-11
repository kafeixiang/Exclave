package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.AppStats
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.databinding.ItemSpeedStatRowBinding
import io.nekohasekai.sagernet.databinding.LayoutSpeedStatsSheetBinding
import io.nekohasekai.sagernet.ktx.applyGlassBlur
import io.nekohasekai.sagernet.ktx.applyItemEntryAnimation
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.utils.FormatFileSizeCompat
import io.nekohasekai.sagernet.utils.PackageCache

class SpeedStatsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: LayoutSpeedStatsSheetBinding? = null
    private val binding get() = _binding!!
    private val lifecycleOwnerOrNull: () -> LifecycleOwner? = {
        if (_binding != null && isAdded) viewLifecycleOwner else null
    }
    private val adapter = SpeedStatsAdapter(lifecycleOwnerOrNull)
    private var rawStatsList: List<AppStats> = emptyList()
    private val statsUpdater = AppStatsListUpdater(
        lifecycleOwnerProvider = lifecycleOwnerOrNull,
        currentDataProvider = { adapter.data },
        applyResult = { sortedList, diffResult ->
            val b = _binding ?: return@AppStatsListUpdater
            val isEmpty = sortedList.isEmpty()
            if (b.emptyText.isVisible != isEmpty) {
                b.emptyText.isVisible = isEmpty
            }
            if (b.sheetRecycler.isVisible == isEmpty) {
                b.sheetRecycler.isVisible = !isEmpty
            }
            adapter.updateData(sortedList, diffResult)
        }
    )

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
        (binding.sheetRecycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        binding.sheetRecycler.adapter = adapter

        processAndDisplayStats(rawStatsList)
    }

    override fun onStart() {
        super.onStart()
        (activity as? MainActivity)?.updateTrafficTimeout()
    }

    override fun onStop() {
        super.onStop()
        (activity as? MainActivity)?.updateTrafficTimeout()
    }

    fun updateStats(statsList: List<AppStats>) {
        runOnMainDispatcher {
            rawStatsList = statsList
            if (_binding != null && isAdded) {
                processAndDisplayStats(statsList)
            }
        }
    }

    private fun processAndDisplayStats(statsList: List<AppStats>) {
        val snapshot = statsList.toList()
        statsUpdater.submit(
            computeList = {
                val map = snapshot.associate { it.uid to it.copy() }.toMutableMap()

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

                map.values.sortedByDescending { it.uplinkTotal + it.downlinkTotal }
            },
            diffCallbackFactory = { oldList, newList -> SpeedStatsDiffCallback(oldList, newList) }
        )
    }

    private class SpeedStatsDiffCallback(
        private val oldList: List<AppStats>,
        private val newList: List<AppStats>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].uid == newList[newItemPosition].uid
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val old = oldList[oldItemPosition]
            val new = newList[newItemPosition]
            return old.tcpConnectionsTotal == new.tcpConnectionsTotal &&
                    old.udpConnectionsTotal == new.udpConnectionsTotal &&
                    old.uplinkTotal == new.uplinkTotal &&
                    old.downlinkTotal == new.downlinkTotal
        }

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any {
            return PAYLOAD_STATS_CHANGE
        }
    }

    private class SpeedStatsAdapter(
        private val lifecycleOwnerProvider: () -> LifecycleOwner?
    ) : RecyclerView.Adapter<SpeedStatsAdapter.ViewHolder>() {

        private val animatedUids = hashSetOf<Int>()
        var data: List<AppStats> = emptyList()
            private set

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long {
            return data.getOrNull(position)?.uid?.toLong() ?: RecyclerView.NO_ID
        }

        fun updateData(newList: List<AppStats>, diffResult: DiffUtil.DiffResult?) {
            data = newList
            if (diffResult != null) {
                diffResult.dispatchUpdatesTo(this)
            } else {
                notifyDataSetChanged()
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ItemSpeedStatRowBinding.inflate(inflater, parent, false)
            return ViewHolder(binding, lifecycleOwnerProvider)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.isNotEmpty()) {
                holder.updateStatsOnly(data[position])
            } else {
                super.onBindViewHolder(holder, position, payloads)
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val stats = data[position]
            if (animatedUids.add(stats.uid)) {
                holder.itemView.applyItemEntryAnimation(position)
            }
            holder.bind(stats)
        }

        override fun getItemCount(): Int = data.size

        class ViewHolder(
            val binding: ItemSpeedStatRowBinding,
            private val lifecycleOwnerProvider: () -> LifecycleOwner?,
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(stats: AppStats) {
                val packageName = PackageCache.resolvePackageName(stats.uid)
                val label = if (packageName != null) PackageCache.loadLabel(packageName) else "UID ${stats.uid}"

                binding.appName.text = label
                binding.appIcon.bindAppIcon(
                    lifecycleOwnerProvider = lifecycleOwnerProvider,
                    uid = stats.uid,
                    packageName = packageName,
                    fallbackRes = R.drawable.ic_baseline_speed_24,
                    fallbackTintRes = R.color.material_grey_500,
                )

                updateStatsOnly(stats)
            }

            @SuppressLint("SetTextI18n")
            fun updateStatsOnly(stats: AppStats) {
                val context = itemView.context
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
        statsUpdater.cancel()
        _binding?.sheetRecycler?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val PAYLOAD_STATS_CHANGE = "payload_stats_change"
    }
}
