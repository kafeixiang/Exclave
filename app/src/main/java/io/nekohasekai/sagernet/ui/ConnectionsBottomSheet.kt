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
    private val adapter = ConnectionsAdapter()

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
        binding.sheetRecycler.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        (activity as? MainActivity)?.connection?.trafficTimeout = 1000
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateStats(statsList: List<AppStats>) {
        runOnMainDispatcher {
            if (_binding == null) return@runOnMainDispatcher
            if (statsList.isEmpty()) {
                binding.emptyText.isVisible = true
                binding.sheetRecycler.isVisible = false
                adapter.data = emptyList()
            } else {
                binding.emptyText.isVisible = false
                binding.sheetRecycler.isVisible = true
                adapter.data = statsList
            }
            adapter.notifyDataSetChanged()
        }
    }

    private class ConnectionsAdapter : RecyclerView.Adapter<ConnectionsAdapter.ViewHolder>() {

        var data: List<AppStats> = emptyList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ItemConnectionRowBinding.inflate(inflater, parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.itemView.applyItemEntryAnimation(position)
            holder.bind(data[position])
        }

        override fun getItemCount(): Int = data.size

        class ViewHolder(val binding: ItemConnectionRowBinding) :
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
                    binding.appIcon.setImageResource(R.drawable.ic_baseline_compare_arrows_24)
                    binding.appIcon.imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.material_grey_500)
                    )
                }

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
}
