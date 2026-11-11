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
import io.nekohasekai.sagernet.databinding.ItemConnectionRowBinding
import io.nekohasekai.sagernet.databinding.LayoutConnectionsSheetBinding
import io.nekohasekai.sagernet.ktx.applyGlassBlur
import io.nekohasekai.sagernet.ktx.applyItemEntryAnimation
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.utils.FormatFileSizeCompat
import io.nekohasekai.sagernet.utils.PackageCache

class ConnectionsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: LayoutConnectionsSheetBinding? = null
    private val binding get() = _binding!!
    private val lifecycleOwnerOrNull: () -> LifecycleOwner? = {
        if (_binding != null && isAdded) viewLifecycleOwner else null
    }
    private val adapter = ConnectionsAdapter(lifecycleOwnerOrNull)
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
        _binding = LayoutConnectionsSheetBinding.inflate(inflater, container, false)
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
                val merged = LinkedHashMap<Int, AppStats>(snapshot.size)
                for (entry in snapshot) {
                    val existing = merged[entry.uid]
                    if (existing != null) {
                        existing.tcpConnections += entry.tcpConnections
                        existing.udpConnections += entry.udpConnections
                        existing.uplink += entry.uplink
                        existing.downlink += entry.downlink
                    } else {
                        merged[entry.uid] = entry.copy()
                    }
                }
                merged.values.sortedWith(
                    compareByDescending<AppStats> { it.tcpConnections + it.udpConnections }
                        .thenByDescending { it.uplink + it.downlink }
                        .thenBy { it.uid }
                )
            },
            diffCallbackFactory = { oldList, newList -> ConnectionsDiffCallback(oldList, newList) }
        )
    }

    private class ConnectionsDiffCallback(
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
            return old.tcpConnections == new.tcpConnections &&
                    old.udpConnections == new.udpConnections &&
                    old.uplink == new.uplink &&
                    old.downlink == new.downlink
        }

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any {
            return PAYLOAD_CONNECTIONS_CHANGE
        }
    }

    private class ConnectionsAdapter(
        private val lifecycleOwnerProvider: () -> LifecycleOwner?
    ) : RecyclerView.Adapter<ConnectionsAdapter.ViewHolder>() {

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
            val binding = ItemConnectionRowBinding.inflate(inflater, parent, false)
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
            val binding: ItemConnectionRowBinding,
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
                    fallbackRes = R.drawable.ic_baseline_compare_arrows_24,
                    fallbackTintRes = R.color.material_grey_500,
                )

                updateStatsOnly(stats)
            }

            @SuppressLint("SetTextI18n")
            fun updateStatsOnly(stats: AppStats) {
                val context = itemView.context
                val tcp = stats.tcpConnections
                val udp = stats.udpConnections
                binding.connectionDetails.text = "TCP: $tcp · UDP: $udp"

                val up = FormatFileSizeCompat.formatFileSize(context, stats.uplink, DataStore.useIECUnit)
                val down = FormatFileSizeCompat.formatFileSize(context, stats.downlink, DataStore.useIECUnit)
                binding.uploadSpeed.text = "⬆ $up"
                binding.downloadSpeed.text = "⬇ $down"
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
        private const val PAYLOAD_CONNECTIONS_CHANGE = "payload_connections_change"
    }
}
