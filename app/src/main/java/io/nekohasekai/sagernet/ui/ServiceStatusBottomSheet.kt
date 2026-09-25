package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.ItemDashboardLatencyServiceBinding
import io.nekohasekai.sagernet.databinding.LayoutServiceStatusSheetBinding

class ServiceStatusBottomSheet(
    private val latencyMap: Map<String, Int>,
    private val onTargetSelected: (ServiceTarget) -> Unit,
    private val onTestAllRequested: () -> Unit,
) : BottomSheetDialogFragment() {

    private var _binding: LayoutServiceStatusSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutServiceStatusSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSheetTestAll.setOnClickListener {
            onTestAllRequested()
            dismiss()
        }

        binding.sheetRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.sheetRecycler.adapter = SheetServiceAdapter()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class SheetServiceAdapter : RecyclerView.Adapter<SheetServiceAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val itemBinding = ItemDashboardLatencyServiceBinding.inflate(inflater, parent, false)
            return ViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(DashboardFragment.SERVICE_TARGETS[position])
        }

        override fun getItemCount(): Int = DashboardFragment.SERVICE_TARGETS.size

        inner class ViewHolder(val itemBinding: ItemDashboardLatencyServiceBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(target: ServiceTarget) {
                itemBinding.serviceIcon.setImageResource(target.iconResId)
                itemBinding.serviceName.setText(target.nameResId)

                val latency = latencyMap[target.id] ?: -1
                val context = itemView.context

                when {
                    latency == -2 -> {
                        itemBinding.serviceLatency.text = context.getString(R.string.connecting)
                        itemBinding.serviceLatency.setTextColor(
                            ContextCompat.getColor(context, R.color.material_grey_500)
                        )
                    }
                    latency > 0 -> {
                        itemBinding.serviceLatency.text = context.getString(R.string.available, latency)
                        itemBinding.serviceLatency.setTextColor(
                            ContextCompat.getColor(context, R.color.cupertino_green)
                        )
                    }
                    else -> {
                        itemBinding.serviceLatency.text = context.getString(R.string.unavailable)
                        itemBinding.serviceLatency.setTextColor(
                            ContextCompat.getColor(context, R.color.material_red_500)
                        )
                    }
                }

                itemBinding.root.setOnClickListener {
                    onTargetSelected(target)
                    dismiss()
                }
            }
        }
    }
}