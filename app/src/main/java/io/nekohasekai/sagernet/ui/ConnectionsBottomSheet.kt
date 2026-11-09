package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
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
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.utils.FormatFileSizeCompat
import io.nekohasekai.sagernet.utils.PackageCache

class ConnectionsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: LayoutConnectionsSheetBinding? = null
    private val binding get() = _binding!!
    private val adapter = ConnectionsAdapter()
    private var rawStatsList: List<AppStats> = emptyList()

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
        (activity as? MainActivity)?.connection?.trafficTimeout = 1000
    }

    override fun onStop() {
        super.onStop()
        (activity as? MainActivity)?.connection?.trafficTimeout = 0
    }

    fun updateStats(statsList: List<AppStats>) {
        rawStatsList = statsList
        if (_binding != null) {
            processAndDisplayStats(statsList)
        }
    }

    private fun processAndDisplayStats(statsList: List<AppStats>) {
        val oldList = adapter.data
        runOnDefaultDispatcher {
            val copiedList = statsList.map { it.copy() }
            val sortedList = copiedList.sortedWith(
                compareByDescending<AppStats> { it.tcpConnections + it.udpConnections }
                    .thenByDescending { it.uplink + it.downlink }
                    .thenBy { it.uid }
            )
            val diffResult = DiffUtil.calculateDiff(ConnectionsDiffCallback(oldList, sortedList))

            runOnMainDispatcher {
                if (_binding == null) return@runOnMainDispatcher
                val isEmpty = sortedList.isEmpty()
                if (binding.emptyText.isVisible != isEmpty) {
                    binding.emptyText.isVisible = isEmpty
                }
                if (binding.sheetRecycler.isVisible == isEmpty) {
                    binding.sheetRecycler.isVisible = !isEmpty
                }
                adapter.updateData(sortedList, diffResult)
            }
        }
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

    private class ConnectionsAdapter : RecyclerView.Adapter<ConnectionsAdapter.ViewHolder>() {

        private val animatedUids = hashSetOf<Int>()
        var data: List<AppStats> = emptyList()
            private set

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long {
            return data.getOrNull(position)?.uid?.toLong() ?: RecyclerView.NO_ID
        }

        fun updateData(newList: List<AppStats>, diffResult: DiffUtil.DiffResult) {
            data = newList
            diffResult.dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ItemConnectionRowBinding.inflate(inflater, parent, false)
            return ViewHolder(binding)
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

        class ViewHolder(val binding: ItemConnectionRowBinding) :
            RecyclerView.ViewHolder(binding.root) {

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
                    binding.appIcon.setImageResource(R.drawable.ic_baseline_compare_arrows_24)
                    binding.appIcon.imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.material_grey_500)
                    )
                }

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
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val PAYLOAD_CONNECTIONS_CHANGE = "payload_connections_change"
    }
}
